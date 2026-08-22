package io.github.phqen1x.worldeditcraft.schem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between plain Java values ({@code String}/{@code Number}/
 * {@code Boolean}/{@code Map}/{@code List}, the same shape the DSL's
 * {@code block_entity} op and {@code MiniJson} use) and {@link NbtTag},
 * so a block entity's free-form payload can round-trip through a
 * {@code .schem}'s {@code BlockEntities} list without every op author
 * needing to think in NBT tag types directly.
 */
final class NbtValueCodec {

    private NbtValueCodec() {
    }

    static NbtTag toTag(Object value) {
        return switch (value) {
            case null -> new NbtTag.StringTag("");
            case NbtTag tag -> tag;
            case String s -> new NbtTag.StringTag(s);
            case Boolean b -> new NbtTag.ByteTag((byte) (b ? 1 : 0));
            case Integer i -> new NbtTag.IntTag(i);
            case Long l -> new NbtTag.LongTag(l);
            case Short s -> new NbtTag.ShortTag(s);
            case Byte b -> new NbtTag.ByteTag(b);
            case Float f -> new NbtTag.FloatTag(f);
            case Double d -> new NbtTag.DoubleTag(d);
            case Number n -> new NbtTag.DoubleTag(n.doubleValue());
            case Map<?, ?> map -> {
                Map<String, NbtTag> entries = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    entries.put(String.valueOf(entry.getKey()), toTag(entry.getValue()));
                }
                yield new NbtTag.CompoundTag(entries);
            }
            case List<?> list -> {
                List<NbtTag> elements = new ArrayList<>();
                for (Object item : list) {
                    elements.add(toTag(item));
                }
                byte elementType = elements.isEmpty() ? NbtTag.END : NbtTag.typeIdOf(elements.get(0));
                yield new NbtTag.ListTag(elementType, elements);
            }
            default -> throw new IllegalArgumentException("Unsupported block-entity value type: " + value.getClass());
        };
    }

    static Object fromTag(NbtTag tag) {
        return switch (tag) {
            case NbtTag.ByteTag t -> (int) t.value();
            case NbtTag.ShortTag t -> (int) t.value();
            case NbtTag.IntTag t -> t.value();
            case NbtTag.LongTag t -> t.value();
            case NbtTag.FloatTag t -> (double) t.value();
            case NbtTag.DoubleTag t -> t.value();
            case NbtTag.ByteArrayTag t -> t.value();
            case NbtTag.StringTag t -> t.value();
            case NbtTag.ListTag t -> {
                List<Object> elements = new ArrayList<>();
                for (NbtTag element : t.value()) {
                    elements.add(fromTag(element));
                }
                yield elements;
            }
            case NbtTag.CompoundTag t -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<String, NbtTag> entry : t.value().entrySet()) {
                    map.put(entry.getKey(), fromTag(entry.getValue()));
                }
                yield map;
            }
            case NbtTag.IntArrayTag t -> t.value();
            case NbtTag.LongArrayTag t -> t.value();
        };
    }
}
