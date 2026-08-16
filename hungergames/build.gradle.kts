// HungerGames: a configurable battle-royale minigame — tributes queue into
// an arena, fight down to one survivor once a border-shrinking match
// starts, with optional randomized "twists" (Quarter-Quell-style rule
// changes) each match. Follows the pattern from the open-source Hunger
// Games plugin at https://modrinth.com/plugin/hungergames but is an
// original, from-scratch implementation, not a port of it.
//
// Scaffolded by the folia-plugin-scaffold skill (folia-server repo).
// Targets Java 21 / paper-api 1.21.4-R0.1-SNAPSHOT, matching this
// cluster's default engine version — see docs/plugin-dev/01-environment-setup.md
// in the folia-server repo for why these exact coordinates.

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "io.github.kenvandine.hungergames"
version = "0.1.0"

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
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("") // the fat jar IS the plugin jar
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
