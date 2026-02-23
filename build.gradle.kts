import dev.architectury.pack200.java.Pack200Adapter
import net.fabricmc.loom.task.RemapJarTask
import org.apache.commons.lang3.SystemUtils

plugins {
    kotlin("jvm") version "2.0.0"
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.afterlike"
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
    mavenCentral()
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

val yguard: Configuration by configurations.creating

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    shade(kotlin("stdlib-jdk8"))

    shade("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")

    shade("org.java-websocket:Java-WebSocket:1.6.0")

    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")

    yguard("com.yworks:yguard:4.1.1")
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

    val yguardObfuscate = register("yguardObfuscate") {
        dependsOn(shadowJar)

        val inputJar = shadowJar.get().archiveFile.get().asFile
        val outputJar = layout.buildDirectory.file("intermediates/CatDueller-obfuscated.jar").get().asFile

        inputs.file(inputJar)
        outputs.file(outputJar)

        doLast {
            ant.withGroovyBuilder {
                "taskdef"(
                    "name" to "yguard",
                    "classname" to "com.yworks.yguard.YGuardTask",
                    "classpath" to yguard.asPath
                )

                "yguard" {
                    "inoutpair"(
                        "in" to inputJar.absolutePath,
                        "out" to outputJar.absolutePath
                    )

                    "externalclasses"(
                        "path" to configurations.runtimeClasspath.get().asPath
                    )

                    "rename"(
                        "logfile" to layout.buildDirectory.file("yguard/yguardlog.xml").get().asFile.absolutePath,
                        "conservemanifest" to "true"
                    ) {
                        "property"("name" to "naming-scheme", "value" to "small")

                        "keep"(
                            "runtimevisibleannotations" to "keep",
                            "runtimeinvisibleannotations" to "keep",
                            "runtimevisibleparameterannotations" to "keep",
                            "runtimeinvisibleparameterannotations" to "keep",
                            "runtimevisibletypeannotations" to "keep",
                            "runtimeinvisibletypeannotations" to "keep"
                        ) {
                            // Mod 入口點 - 必須保留
                            "class"("name" to "org.afterlike.catdueller.CatDueller")

                            // Mixin 類 - 必須保留
                            "class"("classes" to "private", "methods" to "private", "fields" to "private") {
                                "patternset" {
                                    "include"("name" to "org.afterlike.catdueller.mixins.**")
                                }
                            }

                            // Config 類 - 完全不混淆（類名、方法名、字段名都保留）
                            "class"("classes" to "private", "methods" to "private", "fields" to "private") {
                                "patternset" {
                                    "include"("name" to "org.afterlike.catdueller.core.Config")
                                    "include"("name" to "org.afterlike.catdueller.core.Config${'$'}*")
                                }
                            }

                            // MovementRecorder 的數據類 - 完全不混淆（Gson 序列化需要）
                            "class"("classes" to "private", "methods" to "private", "fields" to "private") {
                                "patternset" {
                                    "include"("name" to "org.afterlike.catdueller.bot.player.MovementRecorder")
                                    "include"("name" to "org.afterlike.catdueller.bot.player.MovementRecorder${'$'}*")
                                }
                            }


                            // 外部庫 - 必須保留
                            "class"("classes" to "private", "methods" to "private", "fields" to "private") {
                                "patternset" {
                                    "include"("name" to "kotlin.**")
                                    "include"("name" to "org.jetbrains.**")
                                    "include"("name" to "org.spongepowered.**")
                                    "include"("name" to "org.java_websocket.**")
                                    "include"("name" to "com.google.gson.**")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val remapJar = named<RemapJarTask>("remapJar") {
        archiveClassifier.set("")
        dependsOn(yguardObfuscate)
        val obfuscatedJar = layout.buildDirectory.file("intermediates/CatDueller-obfuscated.jar")
        from(obfuscatedJar)
        input.set(obfuscatedJar)
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
        relocate("org.java_websocket")
    }

    jar {
        archiveClassifier.set("without-deps")
        destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    }

    assemble.get().dependsOn(remapJar)
}