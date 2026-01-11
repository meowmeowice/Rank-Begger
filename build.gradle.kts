import dev.architectury.pack200.java.Pack200Adapter
import net.fabricmc.loom.task.RemapJarTask
import org.apache.commons.lang3.SystemUtils

plugins {
    kotlin("jvm") version "2.0.0"
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "best.spaghetcodes"
version = "1.0.0"

loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) {
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(Pack200Adapter())
        mixinConfig("mixins.catdueller.json")
    }
    @Suppress("UnstableApiUsage")
    mixin {
        defaultRefmapName.set("mixins.catdueller.refmap.json")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

repositories {
    maven("https://repo.essential.gg/repository/maven-public")
    maven("https://repo.spongepowered.org/repository/maven-public")
    maven("https://maven.afterlike.org/releases")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

val shade: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

val modShade: Configuration by configurations.creating {
    configurations.modImplementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    shade(kotlin("stdlib-jdk8"))
    shade("gg.essential:vigilance:312")
    shade("gg.essential:universalcraft-1.8.9-forge:446")

    shade("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")

    shade("org.java-websocket:Java-WebSocket:1.6.0")

    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")
}

sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
    java.srcDir(layout.projectDirectory.dir("src/main/kotlin"))
    kotlin.destinationDirectory.set(java.destinationDirectory)
}

tasks {
    withType(JavaCompile::class) {
        dependsOn(processResources)
        options.encoding = "UTF-8"
    }

    withType(Jar::class) {
        archiveBaseName.set("CatDueller")
        manifest.attributes.run {
            this["FMLCorePluginContainsFMLMod"] = "true"
            this["ForceLoadAsMod"] = "true"
            this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
            this["MixinConfigs"] = "mixins.catdueller.json"
        }
    }

    val remapJar = named<RemapJarTask>("remapJar") {
        archiveClassifier.set("")
        from(shadowJar)
        input.set(shadowJar.get().archiveFile)
    }

    shadowJar {
        destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
        archiveClassifier.set("non-obfuscated-with-deps")
        configurations = listOf(shade)
        exclude("fabric.mod.json")

        doLast {
            configurations.forEach {
                println("Copying dependencies into mod: ${it.files}")
            }
        }

        fun relocate(name: String) = relocate(name, "org.afterlike.catdueller.lib.$name")
        relocate("gg.essential.vigilance")
        relocate("gg.essential.elementa")
        relocate("gg.essential.universalcraft")
        relocate("org.java_websocket")
    }

    jar {
        archiveClassifier.set("without-deps")
        destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    }

    assemble.get().dependsOn(remapJar)
}