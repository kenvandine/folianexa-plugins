// Phqen1xWorldEditCraft: natural-language structure generation for Folia.
// An operator describes a building, a Lemonade Server on the LAN turns it
// into a compact JSON "build script", the plugin rasterizes that
// deterministically into a voxel grid and writes a real Sponge v3 .schem
// file, then pastes it with its own Folia-safe region-scheduled engine —
// no runtime dependency on WorldEdit or FAWE (neither officially supports
// Folia). Full design: docs/phqen1x-rpg-suite/01-worldeditcraft-design.md
// in the FoliaNexa repo.
//
// Targets Java 21 / paper-api 1.21.4-R0.1-SNAPSHOT, matching this
// cluster's default engine version — see docs/plugin-dev/01-environment-setup.md
// in the FoliaNexa repo for why these exact coordinates. Namespace is
// io.github.phqen1x.* rather than io.github.kenvandine.* — see
// docs/phqen1x-rpg-suite/00-project-plan.md#repository-and-namespace.

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "io.github.phqen1x.worldeditcraft"
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
