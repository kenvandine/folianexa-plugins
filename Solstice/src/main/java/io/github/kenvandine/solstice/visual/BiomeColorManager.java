package io.github.kenvandine.solstice.visual;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketConfigReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketConfigSendEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientSelectKnownPacks;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.config.BiomesConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side-only seasonal biome recoloring over PacketEvents (PLAN.md §6). World data is never
 * touched: for every vanilla biome the server's registry sync advertises, we register one extra
 * "solstice:&lt;biome&gt;_&lt;season&gt;" entry per season (a clone of the vanilla NBT with only the
 * effects color fields overridden), then rewrite the biome palette cells of outgoing chunk packets
 * from the vanilla id to the season-matched custom id. Uninstalling the plugin means the client
 * simply stops receiving the extra registry entries and remapped chunks — nothing to revert.
 *
 * <p><b>Verification status:</b> this class compiles against the exact API shapes PLAN.md §6
 * verified statically (Folia scheduler layer, registry injection, chunk biome remap), but per that
 * plan's own "remaining risk" section it has not yet been exercised against a live client. The two
 * likeliest live-server bugs are exactly the ones flagged there: known-packs negotiation causing
 * partial (reference-only) registry entries, and any biome id renumbering by another plugin
 * breaking the vanilla-id → custom-id map built here. Only full-season colors are wired into the
 * remap; sub-season blending (PLAN.md §3.2) at the packet layer is a follow-up, not implemented
 * here, to avoid shipping a partially-verified 20-variant registry injection on top of an already
 * unverified one.
 */
public final class BiomeColorManager extends SimplePacketListenerAbstract implements Listener {

    private static final String BIOME_REGISTRY_KEY = "worldgen/biome";

    private record PlayerBiomeRegistry(List<String> orderedVanillaKeys, Map<String, Map<Season, Integer>> customIds) {
    }

    private final Solstice plugin;
    private final Map<UUID, PlayerBiomeRegistry> sessions = new ConcurrentHashMap<>();
    private boolean active;

    public BiomeColorManager(Solstice plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.config().main().biomeColorsEnabled()) {
            return;
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("packetevents")) {
            plugin.getLogger().warning("visuals.biome-colors.enabled is true but the PacketEvents plugin "
                    + "isn't installed/enabled — seasonal biome recoloring is disabled. See PLAN.md §6.");
            return;
        }
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        active = true;
    }

    public void stop() {
        if (active) {
            PacketEvents.getAPI().getEventManager().unregisterListener(this);
        }
        sessions.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPacketConfigReceive(PacketConfigReceiveEvent event) {
        if (event.getPacketType() != PacketType.Configuration.Client.SELECT_KNOWN_PACKS) {
            return;
        }
        // Tell the server the client has none of the advertised data packs cached, forcing a full
        // registry resync we can then intercept and augment (PLAN.md §6 "remaining risk").
        WrapperConfigClientSelectKnownPacks wrapper = new WrapperConfigClientSelectKnownPacks(event);
        wrapper.setKnownPacks(List.of());
    }

    @Override
    public void onPacketConfigSend(PacketConfigSendEvent event) {
        if (event.getPacketType() != PacketType.Configuration.Server.REGISTRY_DATA) {
            return;
        }
        WrapperConfigServerRegistryData wrapper = new WrapperConfigServerRegistryData(event);
        if (!BIOME_REGISTRY_KEY.equals(wrapper.getRegistryKey().getKey())) {
            return;
        }

        List<WrapperConfigServerRegistryData.RegistryElement> original = wrapper.getElements();
        List<WrapperConfigServerRegistryData.RegistryElement> combined = new ArrayList<>(original);
        List<String> orderedKeys = new ArrayList<>(original.size());
        Map<String, Map<Season, Integer>> customIds = new HashMap<>();

        BiomesConfig biomes = plugin.config().biomes();

        for (WrapperConfigServerRegistryData.RegistryElement element : original) {
            String key = element.getId().getKey();
            orderedKeys.add(key);
            NBT data = element.getData();
            if (!(data instanceof NBTCompound vanillaNbt)) {
                continue;
            }

            org.bukkit.block.Biome biome = org.bukkit.Registry.BIOME.get(
                    org.bukkit.NamespacedKey.minecraft(key));
            if (biome == null) {
                continue;
            }

            Map<Season, Integer> perSeason = new EnumMap<>(Season.class);
            for (Season season : Season.values()) {
                BiomesConfig.RgbColors colors = biomes.colorsFor(biome, season);
                NBTCompound seasonal = withOverriddenColors(vanillaNbt, colors);
                ResourceLocation seasonalKey = new ResourceLocation("solstice", key + "_" + season.name().toLowerCase());
                combined.add(new WrapperConfigServerRegistryData.RegistryElement(seasonalKey, seasonal));
                perSeason.put(season, combined.size() - 1);
            }
            customIds.put(key, perSeason);
        }

        wrapper.setElements(combined);
        sessions.put(event.getUser().getUUID(), new PlayerBiomeRegistry(orderedKeys, customIds));
    }

    private NBTCompound withOverriddenColors(NBTCompound vanillaNbt, BiomesConfig.RgbColors colors) {
        NBTCompound clone = vanillaNbt.copy();
        NBTCompound effects = clone.getCompoundTagOrNull("effects");
        if (effects == null) {
            effects = new NBTCompound();
            clone.setTag("effects", effects);
        }
        effects.setTag("fog_color", new NBTInt(colors.fog()));
        effects.setTag("water_color", new NBTInt(colors.water()));
        effects.setTag("water_fog_color", new NBTInt(colors.waterFog()));
        effects.setTag("sky_color", new NBTInt(colors.sky()));
        effects.setTag("foliage_color", new NBTInt(colors.foliage()));
        effects.setTag("grass_color", new NBTInt(colors.grass()));
        return clone;
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) {
            return;
        }
        User user = event.getUser();
        PlayerBiomeRegistry registry = sessions.get(user.getUUID());
        if (registry == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null || !plugin.playerDataStore().get(player.getUniqueId()).seasonColors()) {
            return;
        }
        if (plugin.config().main().skipBedrockClients() && isBedrockPlayer(player)) {
            return;
        }
        WorldSeasonState state = plugin.calendarEngine().stateOf(player.getWorld());
        if (state == null) {
            return;
        }

        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        for (BaseChunk section : column.getChunks()) {
            if (!(section instanceof Chunk_v1_18 modernSection)) {
                continue;
            }
            DataPalette biomePalette = modernSection.getBiomeData();
            if (biomePalette == null) {
                continue;
            }
            remapSection(biomePalette, registry, state.season());
        }
    }

    private void remapSection(DataPalette biomePalette, PlayerBiomeRegistry registry, Season season) {
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    int currentId = biomePalette.get(x, y, z);
                    if (currentId < 0 || currentId >= registry.orderedVanillaKeys().size()) {
                        continue;
                    }
                    String vanillaKey = registry.orderedVanillaKeys().get(currentId);
                    Map<Season, Integer> perSeason = registry.customIds().get(vanillaKey);
                    if (perSeason == null) {
                        continue;
                    }
                    Integer customId = perSeason.get(season);
                    if (customId != null) {
                        biomePalette.set(x, y, z, customId);
                    }
                }
            }
        }
    }

    private boolean isBedrockPlayer(Player player) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return (boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, player.getUniqueId());
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
