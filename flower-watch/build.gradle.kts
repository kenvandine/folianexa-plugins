// FlowerWatch: temporary, observation-only diagnostic plugin. Logs the
// *cause* (BlockGrowEvent, BlockFertilizeEvent, a player, etc.) behind
// every flower-material block change, since CoreProtect alone only
// records that a block changed, not why — see README.md.
//
// Scaffolded by the folia-plugin-scaffold skill (FoliaNexa repo).
// Targets Java 21 / paper-api 1.21.4-R0.1-SNAPSHOT, matching this
// cluster's default engine version — see docs/plugin-dev/01-environment-setup.md
// in the FoliaNexa repo for why these exact coordinates.

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "io.github.kenvandine.flowerwatch"
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
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // CoreProtect deliberately has NO dependency entry here — see
    // CoreProtectBridge's class doc for why (no working JitPack build,
    // not on Maven Central/PaperMC's repo). The integration is reached
    // entirely through reflection at runtime instead.

    // FlowerMaterialsTest exercises real org.bukkit.Material constants
    // directly (the whole point of that class), so unlike a pure-domain
    // test it needs paper-api on the test classpath too, not just compile.
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
