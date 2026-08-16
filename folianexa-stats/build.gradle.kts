// FoliaNexaStats: reports per-player kills/deaths/blocks-mined/playtime to
// folia-nexa-mgmt's public player hub (PLAN.md §7A in the FoliaNexa repo),
// softdepending on AuraSkills (power level) and Vault (economy balance,
// reported only when AxAuctions is present — AxAuctions itself is a
// closed-source premium plugin with no public API to compile against; it
// transacts through whichever Vault-registered economy plugin is present,
// same as the rest of the Bukkit ecosystem, so that's what this actually
// reads).
//
// Scaffolded by the folia-plugin-scaffold skill (FoliaNexa repo).
// Targets Java 21 / paper-api 1.21.4-R0.1-SNAPSHOT, matching this
// cluster's default engine version — see docs/plugin-dev/01-environment-setup.md
// in the FoliaNexa repo for why these exact coordinates.

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "io.github.kenvandine.folianexastats"
// The release workflow (.github/workflows/release.yml) passes
// -PreleaseVersion=<tag's version> so a release's jar/plugin.yml
// version always matches the release tag it's published under. Plain
// local `./gradlew build` (no property set) falls back to this default.
version = (findProperty("releaseVersion") as String?) ?: "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") // hosts io.papermc.paper:paper-api
    maven("https://jitpack.io") // hosts com.github.MilkBowl:VaultAPI
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Soft integrations — present at compile time so we can call their real
    // APIs directly, but never bundled/shaded: at runtime we check whether
    // each plugin is actually loaded (Bukkit.getPluginManager()) before
    // touching its API, and both are declared `softdepend` in plugin.yml so
    // Paper loads us after them when present but doesn't require them.
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.3.12")
    // VaultAPI's own POM pulls in an old org.bukkit:bukkit that conflicts
    // with paper-api's own (newer) bukkit capability — exclude it, since
    // paper-api already provides everything Vault's API surface needs
    // (just org.bukkit.OfflinePlayer).
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("") // the fat jar IS the plugin jar
}

tasks.processResources {
    // Stamps plugin.yml's version: '${version}' placeholder with this
    // build's actual project.version, so it's never hand-edited out of
    // sync with the jar's own version (see the `version = ...` comment
    // above).
    filesMatching("plugin.yml") {
        expand("version" to project.version.toString())
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
