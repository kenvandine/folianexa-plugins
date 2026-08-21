// Solstice: a from-scratch, Folia-native seasons, calendar, and
// temperature plugin. See README.md and docs/FEATURES.md for the full
// feature list and design decisions.
//
// Converted from Maven (pom.xml) to Gradle so this plugin builds and
// releases through the same .github/workflows/release.yml as every other
// plugin in this monorepo — see README.md's "Adding a new plugin".
// Targets Java 21, built against dev.folia:folia-api (not paper-api,
// since Solstice depends on Folia-specific scheduling APIs).

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "io.github.kenvandine.solstice"
// The release workflow (.github/workflows/release.yml) passes
// -PreleaseVersion=<tag's version> so a release's jar/plugin.yml
// version always matches the release tag it's published under. Plain
// local `./gradlew build` (no property set) falls back to this default.
version = (findProperty("releaseVersion") as String?) ?: "0.1.0"

// No Java toolchain pin here (unlike the other plugins in this repo):
// this exact folia-api snapshot (26.2.build.5-beta) ships class files
// compiled for JDK 25 even though its API surface targets release 21, so
// the compiler *running* this build must itself be JDK 25+ to read it
// off the classpath — pinning a JDK 21 toolchain makes Gradle pick a
// JDK 21 javac that can't parse those class files at all. `options.release`
// below still constrains Solstice's own emitted bytecode/API to 21,
// matching the old pom.xml's <release>21</release>. See .java-version
// (read by release.yml) for why CI runs this build under JDK 25.
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
        // this. Falling back to POM-based resolution sidesteps it.
        metadataSources {
            mavenPom()
            artifact()
            ignoreGradleMetadataRedirection() // the POM redirects to a .module file; skip it (see comment above)
        }
    }
    maven("https://repo.codemc.io/repository/maven-releases/") // hosts com.github.retrooper:packetevents-spigot
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") // hosts me.clip:placeholderapi
}

dependencies {
    compileOnly("dev.folia:folia-api:26.2.build.5-beta")

    // Soft integrations — present at compile time so we can call their real
    // APIs directly, but never bundled/shaded: PacketEvents and
    // PlaceholderAPI must be installed as their own plugins, and both are
    // declared `softdepend` in plugin.yml so Folia loads us after them when
    // present but doesn't require them.
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("me.clip:placeholderapi:2.12.3")

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
