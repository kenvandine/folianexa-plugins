// FlowerWatch: temporary, observation-only diagnostic plugin. Logs the
// *cause* (BlockGrowEvent, BlockFertilizeEvent, a player, etc.) behind
// every flower-material block change, since CoreProtect alone only
// records that a block changed, not why — see README.md.
//
// Scaffolded by the folia-plugin-scaffold skill (FoliaNexa repo).
// Targets Java 21, built against dev.folia:folia-api — this cluster's
// actual running engine is Folia 26.2 (mgmt's EngineVersion singleton,
// FoliaNexa's models.py), not the older paper-api 1.21.4 coordinate a
// few of this repo's other plugins (campus-lobby, folianexa-stats,
// hungergames) still target. Same reasoning as Solstice's own
// build.gradle.kts: depending on plain paper-api instead here would
// mean compiling against an engine version this cluster doesn't
// actually run. See .java-version (read by .github/workflows/
// release.yml) for why CI builds this under JDK 25 — folia-api's
// 26.2.build.5-beta class files require a JDK 25+ javac to read off the
// classpath even though this plugin's own emitted bytecode still
// targets release 21 (options.release below), the same class-file-
// version mismatch Solstice's build.gradle.kts documents in more
// detail.

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

// No Java toolchain pin here (matching Solstice, unlike this repo's
// paper-api-1.21.4-based plugins): folia-api's class files need the
// compiler *running* this build to itself be JDK 25+, so pinning a JDK
// 21 toolchain would make Gradle pick a JDK 21 javac that can't parse
// them off the classpath at all. options.release below still
// constrains FlowerWatch's own compiled output to release 21.
tasks.withType<JavaCompile> {
    options.release.set(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { // hosts dev.folia:folia-api
        // folia-api publishes Gradle module metadata (redirected to from
        // its POM) that declares a JVM-25 target-compatibility attribute.
        // Even without a toolchain pin, Gradle's own attribute matching
        // would reject it whenever the JVM running Gradle isn't 25+ (e.g.
        // local `./gradlew` runs under a plain JDK 21 JAVA_HOME). Maven
        // consumers, which ignore Gradle metadata entirely, never hit
        // this. Falling back to POM-based resolution sidesteps it — see
        // Solstice's identical repository block for the same fix.
        metadataSources {
            mavenPom()
            artifact()
            ignoreGradleMetadataRedirection()
        }
    }
}

dependencies {
    // Pinned to the same build Solstice already verified compiles and
    // runs against this cluster's actual engine (26.2.build.6-beta is
    // available too, as of when this was written — bump deliberately,
    // not blindly, matching how this repo pins CoreProtect/Geyser
    // versions elsewhere rather than tracking "latest").
    compileOnly("dev.folia:folia-api:26.2.build.5-beta")

    // CoreProtect deliberately has NO dependency entry here — see
    // CoreProtectBridge's class doc for why (no working JitPack build,
    // not on Maven Central/PaperMC's repo). The integration is reached
    // entirely through reflection at runtime instead.

    // FlowerMaterialsTest exercises real org.bukkit.Material constants
    // directly (the whole point of that class), so unlike a pure-domain
    // test it needs folia-api on the test classpath too, not just compile.
    testImplementation("dev.folia:folia-api:26.2.build.5-beta")

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
