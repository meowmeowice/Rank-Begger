import dev.architectury.pack200.java.Pack200Adapter
import net.fabricmc.loom.task.RemapJarTask
import org.apache.commons.lang3.SystemUtils
import org.objectweb.asm.*
import java.util.Base64
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.JarEntry

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

// Helper function to copy DLL into the native-obfuscator output jar
fun copyDllIntoJar(jarFile: File, dllFile: File, outputDir: File) {
    val tempJar = File(jarFile.parentFile, "output-with-native.jar")
    JarFile(jarFile).use { jf ->
        JarOutputStream(tempJar.outputStream(), jf.manifest).use { jos ->
            jf.entries().asSequence().forEach { entry ->
                if (entry.name == "META-INF/MANIFEST.MF") return@forEach
                jos.putNextEntry(JarEntry(entry.name))
                jos.write(jf.getInputStream(entry).readBytes())
                jos.closeEntry()
            }
            jos.putNextEntry(JarEntry("native0/x64-windows.dll"))
            jos.write(dllFile.readBytes())
            jos.closeEntry()
        }
    }
    jarFile.delete()
    tempJar.renameTo(jarFile)
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

    // ==================== Build Pipeline ====================
    // shadowJar → yguardObfuscate → remapForNative(MCP→SRG) → nativeObfuscate → cmake → packageNative → stripKotlinMetadata → remapJar
    //
    // yGuard runs FIRST so all classes are renamed. Then remapForNative
    // converts MCP→SRG so native-obfuscator generates C++ with SRG names
    // directly. remapJar at the end converts MCP→SRG for .class files.

    // Step 1: yGuard rename obfuscation (runs on shadowJar output)
    val yguardObfuscate = register("yguardObfuscate") {
        dependsOn(shadowJar)
        val inputJar = shadowJar.get().archiveFile.get().asFile
        val outputJar = layout.buildDirectory.file("intermediates/CatDueller-yguard.jar").get().asFile
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
                    "inoutpair"("in" to inputJar.absolutePath, "out" to outputJar.absolutePath)
                    "externalclasses"("path" to configurations.runtimeClasspath.get().asPath)
                    "rename"("logfile" to "C:/tmp/catdueller-native/yguardlog.xml", "conservemanifest" to "true") {
                        "property"("name" to "naming-scheme", "value" to "small")
                        "keep"(
                            "runtimevisibleannotations" to "keep",
                            "runtimeinvisibleannotations" to "keep",
                            "runtimevisibleparameterannotations" to "keep",
                            "runtimeinvisibleparameterannotations" to "keep",
                            "runtimevisibletypeannotations" to "keep",
                            "runtimeinvisibletypeannotations" to "keep"
                        ) {
                            // Mod entry point — Forge loads by class name
                            "class"("name" to "org.afterlike.catdueller.CatDueller")
                            // Mixins — referenced by name in mixins.catdueller.json
                            "class"("classes" to "private", "methods" to "private", "fields" to "private") {
                                "patternset" { "include"("name" to "org.afterlike.catdueller.mixins.**") }
                            }
                            // Config — uses reflection (getDeclaredField) for serialization
                            "class"("classes" to "private", "methods" to "private", "fields" to "private") {
                                "patternset" {
                                    "include"("name" to "org.afterlike.catdueller.core.Config")
                                    "include"("name" to "org.afterlike.catdueller.core.Config\$*")
                                }
                            }
                            // MovementRecorder — serializes to/from JSON files
                            "class"("classes" to "private", "methods" to "private", "fields" to "private") {
                                "patternset" {
                                    "include"("name" to "org.afterlike.catdueller.bot.player.MovementRecorder")
                                    "include"("name" to "org.afterlike.catdueller.bot.player.MovementRecorder\$*")
                                }
                            }
                            // Third-party libs — must not be renamed
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

    // Step 2: Generate native-obfuscator whitelist from yGuard log.
    // The original class names (HWIDLock, BotBase, HWIDUtil) have been renamed
    // by yGuard, so we parse the log to find their new names.
    val generateNativeWhitelist = register("generateNativeWhitelist") {
        dependsOn(yguardObfuscate)
        val yguardLog = File("C:/tmp/catdueller-native/yguardlog.xml")
        val generatedWhitelist = layout.buildDirectory.file("native-obfuscator/whitelist.txt").get().asFile
        outputs.file(generatedWhitelist)
        doLast {
            // Original classes we want to native-obfuscate
            val originalClasses = listOf(
                "org.afterlike.catdueller.core.HWIDLock",
                "org.afterlike.catdueller.utils.system.HWIDUtil",
                "org.afterlike.catdueller.bot.BotBase"
            )

            // Parse yGuard log to build full rename mapping
            // Package renames: old dot-name -> new short name
            val packageRenameMap = mutableMapOf<String, String>()
            // Class renames: old dot-name -> new short name (just the class part)
            val classRenameMap = mutableMapOf<String, String>()

            if (yguardLog.exists()) {
                val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(yguardLog)

                val pkgNodes = doc.getElementsByTagName("package")
                for (i in 0 until pkgNodes.length) {
                    val el = pkgNodes.item(i) as org.w3c.dom.Element
                    val name = el.getAttribute("name")
                    val map = el.getAttribute("map")
                    if (map.isNotEmpty()) packageRenameMap[name] = map
                }

                val classNodes = doc.getElementsByTagName("class")
                for (i in 0 until classNodes.length) {
                    val el = classNodes.item(i) as org.w3c.dom.Element
                    val name = el.getAttribute("name")
                    val map = el.getAttribute("map")
                    if (map.isNotEmpty()) classRenameMap[name] = map
                }
            }

            // Resolve a fully-qualified class name through yGuard renames
            fun resolveClass(originalDotName: String): String {
                val lastDot = originalDotName.lastIndexOf('.')
                val pkg = if (lastDot >= 0) originalDotName.substring(0, lastDot) else ""
                val simpleName = if (lastDot >= 0) originalDotName.substring(lastDot + 1) else originalDotName

                // Resolve package: yGuard renames the LEAF segment of each package.
                // E.g., "org.afterlike.catdueller.core" map="A" means
                //   org.afterlike.catdueller.core → org.afterlike.catdueller.A
                // We must apply renames from outermost to innermost, building the
                // resolved path as we go.
                val pkgParts = if (pkg.isEmpty()) emptyList() else pkg.split('.')
                val resolvedParts = mutableListOf<String>()
                for (i in pkgParts.indices) {
                    val originalFullPkg = pkgParts.subList(0, i + 1).joinToString(".")
                    val mapped = packageRenameMap[originalFullPkg]
                    if (mapped != null) {
                        // yGuard renamed this package level — replace just the leaf
                        resolvedParts.add(mapped)
                    } else {
                        resolvedParts.add(pkgParts[i])
                    }
                }
                val resolvedPkg = resolvedParts.joinToString(".")

                // Resolve class name
                val resolvedClass = classRenameMap[originalDotName] ?: simpleName

                return if (resolvedPkg.isEmpty()) resolvedClass else "$resolvedPkg.$resolvedClass"
            }

            // Generate whitelist entries (internal name format with wildcard)
            generatedWhitelist.parentFile.mkdirs()
            val lines = mutableListOf<String>()
            for (orig in originalClasses) {
                val resolved = resolveClass(orig)
                val internal = resolved.replace('.', '/')
                // Use wildcard to catch inner classes too (e.g., HWIDLock$WhitelistData)
                lines.add("${internal}*")
                println("[whitelist] $orig -> ${internal}*")
            }
            generatedWhitelist.writeText(lines.joinToString("\n") + "\n")
            println("[whitelist] Generated ${lines.size} entries")
        }
    }

    // Step 2.5: Remap yGuard output from MCP→SRG BEFORE native-obfuscator.
    // This way the generated C++ already contains SRG names (func_xxx, field_xxx)
    // and we don't need any string patching in the DLL.
    val remapForNative = register<RemapJarTask>("remapForNative") {
        dependsOn(yguardObfuscate)
        val mpcJar = layout.buildDirectory.file("intermediates/CatDueller-yguard.jar")
        from(mpcJar)
        input.set(mpcJar)
        archiveClassifier.set("yguard-srg")
        destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
        archiveBaseName.set("CatDueller")
    }

    // Step 3: native-obfuscator transpiles whitelisted classes to C++
    val nativeObfuscate = register<Exec>("nativeObfuscate") {
        dependsOn(generateNativeWhitelist, remapForNative)
        val inputJar = remapForNative.get().archiveFile.get().asFile
        val outputDir = layout.buildDirectory.dir("native-obfuscator").get().asFile
        val whitelistFile = layout.buildDirectory.file("native-obfuscator/whitelist.txt").get().asFile
        val libsDir = File("C:/tmp/catdueller-native/libs")
        inputs.file(inputJar)
        inputs.file(whitelistFile)
        outputs.dir(outputDir)
        doFirst {
            // Clean output dir but preserve the generated whitelist
            val whitelistContent = if (whitelistFile.exists()) whitelistFile.readText() else ""
            outputDir.listFiles()?.filter { it.name != "whitelist.txt" }?.forEach { it.deleteRecursively() }
            // Restore whitelist if it was deleted
            if (whitelistContent.isNotEmpty() && !whitelistFile.exists()) {
                whitelistFile.parentFile.mkdirs()
                whitelistFile.writeText(whitelistContent)
            }
            libsDir.deleteRecursively()
            libsDir.mkdirs()
            configurations.runtimeClasspath.get().files.forEach { file ->
                file.copyTo(File(libsDir, file.name), overwrite = true)
            }
            println("[nativeObfuscate] Copied ${libsDir.listFiles()?.size ?: 0} dependency jars to ${libsDir.absolutePath}")
        }
        commandLine(
            "java", "-jar",
            rootProject.file("obfuscate/native-obfuscator.jar").absolutePath,
            "-w", whitelistFile.absolutePath,
            "-l", libsDir.absolutePath,
            "-p", "hotspot",
            inputJar.absolutePath,
            outputDir.absolutePath
        )
    }

    // Step 4: CMake configure + build
    val cmakeConfigure = register<Exec>("cmakeConfigure") {
        dependsOn(nativeObfuscate)
        val cppDir = layout.buildDirectory.dir("native-obfuscator/cpp").get().asFile
        val safeBuildDir = File("C:/tmp/catdueller-native/cpp")
        inputs.dir(cppDir)
        outputs.dir(safeBuildDir)
        doFirst {
            safeBuildDir.deleteRecursively()
            safeBuildDir.mkdirs()
            cppDir.walkTopDown().forEach { src ->
                val rel = src.relativeTo(cppDir)
                val dest = File(safeBuildDir, rel.path)
                if (src.isDirectory) dest.mkdirs() else src.copyTo(dest, overwrite = true)
            }
            File(safeBuildDir, "build").mkdirs()
        }
        workingDir(File(safeBuildDir, "build"))
        environment("JAVA_HOME", "C:\\Program Files\\Java\\jdk-1.8")
        commandLine(
            "cmake", "..",
            "-G", "MinGW Makefiles",
            "-DCMAKE_BUILD_TYPE=Release",
            "-DCMAKE_C_COMPILER=C:/w64devkit/bin/gcc.exe",
            "-DCMAKE_CXX_COMPILER=C:/w64devkit/bin/g++.exe"
        )
    }

    val cmakeBuild = register<Exec>("cmakeBuild") {
        dependsOn(cmakeConfigure)
        val safeBuildDir = File("C:/tmp/catdueller-native/cpp/build")
        workingDir(safeBuildDir)
        commandLine("cmake", "--build", ".", "--config", "Release", "--", "-j4")
    }

    // Step 5: Copy DLL into output jar
    val packageNative = register("packageNative") {
        dependsOn(cmakeBuild)
        val outputDir = layout.buildDirectory.dir("native-obfuscator").get().asFile
        doLast {
            val nativeJar = outputDir.listFiles()?.firstOrNull { it.extension == "jar" }
                ?: throw GradleException("Could not find output jar in ${outputDir.absolutePath}")
            val safeBuildDir = File("C:/tmp/catdueller-native/cpp/build")
            val dll = safeBuildDir.walkTopDown().firstOrNull { it.extension == "dll" }
            if (dll == null) {
                safeBuildDir.walkTopDown().filter { it.isFile }.forEach { println("  ${it.relativeTo(safeBuildDir)}") }
                throw GradleException("Could not find compiled DLL in ${safeBuildDir.absolutePath}")
            }
            println("[packageNative] Found DLL: ${dll.absolutePath}")
            println("[packageNative] Target JAR: ${nativeJar.absolutePath}")
            copyDllIntoJar(nativeJar, dll, outputDir)
            val standardName = File(outputDir, "output.jar")
            if (nativeJar.name != "output.jar") {
                nativeJar.renameTo(standardName)
            }
        }
    }

    // Step 6: Strip Kotlin metadata + encrypt strings
    val stripKotlinMetadata = register("stripKotlinMetadata") {
        dependsOn(packageNative)
        val inputJar = layout.buildDirectory.file("native-obfuscator/output.jar").get().asFile
        val outputJar = layout.buildDirectory.file("intermediates/CatDueller-stripped.jar").get().asFile
        inputs.file(inputJar)
        outputs.file(outputJar)
        doLast {
            val stripAnnotations = setOf(
                "Lkotlin/Metadata;",
                "Lkotlin/jvm/internal/SourceDebugExtension;",
                "Lkotlin/coroutines/jvm/internal/DebugMetadata;"
            )
            val removeClasses = setOf(
                "kotlin/Metadata.class", "kotlin/Metadata\$DefaultImpls.class",
                "kotlin/coroutines/jvm/internal/DebugMetadata.class",
                "kotlin/coroutines/jvm/internal/DebugMetadataKt.class",
                "kotlin/jvm/internal/SourceDebugExtension.class"
            )

            // Per-class key derivation: hash class name into 16-byte key
            fun deriveKey(className: String): ByteArray {
                val key = ByteArray(16)
                var h1 = -0x61C8864680B583EBL // 0x9E3779B97F4A7C15
                var h2 = 0x6C62272E07BB0142L
                for (c in className) {
                    h1 = h1 xor (c.toLong() * 0x100000001B3L)
                    h1 = (h1 shl 31) or (h1 ushr 33)
                    h2 = h2 xor (c.toLong() * 0x517CC1B727220A95L)
                    h2 = (h2 shl 27) or (h2 ushr 37)
                }
                for (i in 0..7) key[i] = (h1 ushr (i * 8)).toByte()
                for (i in 0..7) key[i + 8] = (h2 ushr (i * 8)).toByte()
                return key
            }

            // Encrypt string with per-class key, return as String (raw chars)
            fun encrypt(s: String, key: ByteArray): String {
                val raw = s.toByteArray(Charsets.UTF_8)
                val sb = StringBuilder(raw.size)
                for (i in raw.indices) {
                    val enc = (raw[i].toInt() xor key[i % key.size].toInt()) and 0xFF
                    sb.append(enc.toChar())
                }
                return sb.toString()
            }

            // After yGuard rename, our classes may be under different paths.
            // Instead of matching a prefix, we encrypt ALL classes except known third-party libs.
            val skipPrefixes = listOf(
                "kotlin/", "org/jetbrains/", "org/spongepowered/",
                "org/java_websocket/", "com/google/gson/",
                "net/minecraft/", "net/minecraftforge/",
                "by/radioegor146/", "native0/",
                "META-INF/"
            )
            val decryptorMethodName = "\u0001"
            var strippedCount = 0
            var encryptedStringCount = 0

            JarFile(inputJar).use { jf ->
                JarOutputStream(outputJar.outputStream(), jf.manifest).use { jos ->
                    jf.entries().asSequence().toList().forEach { entry ->
                        if (entry.name == "META-INF/MANIFEST.MF") return@forEach
                        if (entry.name in removeClasses) {
                            println("[strip] Removed: ${entry.name}")
                            return@forEach
                        }
                        val bytes = jf.getInputStream(entry).readBytes()
                        if (!entry.name.endsWith(".class")) {
                            jos.putNextEntry(JarEntry(entry.name))
                            jos.write(bytes)
                            jos.closeEntry()
                            return@forEach
                        }

                        val shouldProcess = !skipPrefixes.any { entry.name.startsWith(it) }
                        val reader = ClassReader(bytes)

                        // All our classes get string encryption + COMPUTE_FRAMES.
                        // Native methods have no bytecode so COMPUTE_FRAMES won't touch them.
                        val shouldEncryptStrings = shouldProcess

                        val writer = if (shouldEncryptStrings) {
                            object : ClassWriter(reader, COMPUTE_FRAMES) {
                                override fun getCommonSuperClass(type1: String, type2: String): String {
                                    return "java/lang/Object"
                                }
                            }
                        } else {
                            ClassWriter(0)
                        }

                        var metadataStripped = false
                        var className = ""
                        var needsDecryptor = false
                        var isInterface = false
                        var classKey = ByteArray(0)

                        val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {
                            override fun visit(version: Int, access: Int, name: String, sig: String?, superName: String?, interfaces: Array<out String>?) {
                                className = name
                                isInterface = (access and Opcodes.ACC_INTERFACE) != 0
                                classKey = deriveKey(name)
                                super.visit(version, access, name, sig, superName, interfaces)
                            }
                            override fun visitAnnotation(desc: String?, vis: Boolean): AnnotationVisitor? {
                                if (desc in stripAnnotations) { metadataStripped = true; return null }
                                return super.visitAnnotation(desc, vis)
                            }
                            override fun visitSource(source: String?, debug: String?) {}
                            override fun visitMethod(access: Int, name: String, desc: String, sig: String?, exceptions: Array<out String>?): MethodVisitor? {
                                val mv = super.visitMethod(access, name, desc, sig, exceptions)
                                if (!shouldEncryptStrings || isInterface) return mv
                                return object : MethodVisitor(Opcodes.ASM9, mv) {
                                    override fun visitLdcInsn(value: Any?) {
                                        if (value is String && value.length >= 3) {
                                            val encrypted = encrypt(value, classKey)
                                            mv?.visitLdcInsn(encrypted)
                                            mv?.visitMethodInsn(Opcodes.INVOKESTATIC, className, decryptorMethodName, "(Ljava/lang/String;)Ljava/lang/String;", false)
                                            needsDecryptor = true
                                            encryptedStringCount++
                                            return
                                        }
                                        super.visitLdcInsn(value)
                                    }
                                }
                            }
                        }
                        reader.accept(visitor, 0)

                        // Inject decryptor that derives key from own class name at runtime
                        if (needsDecryptor && !isInterface) {
                            val mv = writer.visitMethod(
                                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
                                decryptorMethodName, "(Ljava/lang/String;)Ljava/lang/String;", null, null
                            )
                            mv.visitCode()

                            // String cn = <MethodHandles.lookup().lookupClass().getName()>
                            // We use a trick: embed the class name derivation inline
                            // Actually simpler: use new Throwable().getStackTrace()[0].getClassName()
                            // But safest for perf: just call Class.forName with a constant that we also encrypt? No.
                            // Best approach: MethodHandles.lookup().lookupClass().getName()
                            // But that's Java 7+. We target Java 8, so it's fine.
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles\$Lookup;", false)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandles\$Lookup", "lookupClass", "()Ljava/lang/Class;", false)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false)
                            // Replace '.' with '/' to match internal name
                            mv.visitLdcInsn(".")
                            mv.visitLdcInsn("/")
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false)
                            mv.visitVarInsn(Opcodes.ASTORE, 1) // cn (internal class name)

                            // Derive 16-byte key from cn (same algorithm as build-time)
                            // long h1 = 0x9E3779B97F4A7C15L, h2 = 0x6C62272E07BB0142L
                            mv.visitLdcInsn(-0x61C8864680B583EBL) // 0x9E3779B97F4A7C15
                            mv.visitVarInsn(Opcodes.LSTORE, 2) // h1
                            mv.visitLdcInsn(0x6C62272E07BB0142L)
                            mv.visitVarInsn(Opcodes.LSTORE, 4) // h2

                            // for (int i = 0; i < cn.length(); i++) { ... }
                            mv.visitInsn(Opcodes.ICONST_0)
                            mv.visitVarInsn(Opcodes.ISTORE, 6) // i
                            val keyLoopStart = Label()
                            val keyLoopEnd = Label()
                            mv.visitLabel(keyLoopStart)
                            mv.visitVarInsn(Opcodes.ILOAD, 6)
                            mv.visitVarInsn(Opcodes.ALOAD, 1)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                            mv.visitJumpInsn(Opcodes.IF_ICMPGE, keyLoopEnd)

                            // int c = cn.charAt(i)
                            mv.visitVarInsn(Opcodes.ALOAD, 1)
                            mv.visitVarInsn(Opcodes.ILOAD, 6)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false)
                            mv.visitVarInsn(Opcodes.ISTORE, 7) // c

                            // h1 = h1 ^ ((long)c * 0x100000001B3L)
                            mv.visitVarInsn(Opcodes.LLOAD, 2)
                            mv.visitVarInsn(Opcodes.ILOAD, 7)
                            mv.visitInsn(Opcodes.I2L)
                            mv.visitLdcInsn(0x100000001B3L)
                            mv.visitInsn(Opcodes.LMUL)
                            mv.visitInsn(Opcodes.LXOR)
                            // h1 = (h1 << 31) | (h1 >>> 33)
                            mv.visitVarInsn(Opcodes.LSTORE, 8) // temp h1
                            mv.visitVarInsn(Opcodes.LLOAD, 8)
                            mv.visitIntInsn(Opcodes.BIPUSH, 31)
                            mv.visitInsn(Opcodes.LSHL)
                            mv.visitVarInsn(Opcodes.LLOAD, 8)
                            mv.visitIntInsn(Opcodes.BIPUSH, 33)
                            mv.visitInsn(Opcodes.LUSHR)
                            mv.visitInsn(Opcodes.LOR)
                            mv.visitVarInsn(Opcodes.LSTORE, 2) // h1

                            // h2 = h2 ^ ((long)c * 0x517CC1B727220A95L)
                            mv.visitVarInsn(Opcodes.LLOAD, 4)
                            mv.visitVarInsn(Opcodes.ILOAD, 7)
                            mv.visitInsn(Opcodes.I2L)
                            mv.visitLdcInsn(0x517CC1B727220A95L)
                            mv.visitInsn(Opcodes.LMUL)
                            mv.visitInsn(Opcodes.LXOR)
                            // h2 = (h2 << 27) | (h2 >>> 37)
                            mv.visitVarInsn(Opcodes.LSTORE, 8) // temp h2
                            mv.visitVarInsn(Opcodes.LLOAD, 8)
                            mv.visitIntInsn(Opcodes.BIPUSH, 27)
                            mv.visitInsn(Opcodes.LSHL)
                            mv.visitVarInsn(Opcodes.LLOAD, 8)
                            mv.visitIntInsn(Opcodes.BIPUSH, 37)
                            mv.visitInsn(Opcodes.LUSHR)
                            mv.visitInsn(Opcodes.LOR)
                            mv.visitVarInsn(Opcodes.LSTORE, 4) // h2

                            mv.visitIincInsn(6, 1)
                            mv.visitJumpInsn(Opcodes.GOTO, keyLoopStart)
                            mv.visitLabel(keyLoopEnd)

                            // Build byte[16] key from h1, h2
                            mv.visitIntInsn(Opcodes.BIPUSH, 16)
                            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
                            mv.visitVarInsn(Opcodes.ASTORE, 6) // key
                            for (i in 0..7) {
                                mv.visitVarInsn(Opcodes.ALOAD, 6)
                                mv.visitIntInsn(Opcodes.BIPUSH, i)
                                mv.visitVarInsn(Opcodes.LLOAD, 2)
                                if (i > 0) { mv.visitIntInsn(Opcodes.BIPUSH, i * 8); mv.visitInsn(Opcodes.LUSHR) }
                                mv.visitInsn(Opcodes.L2I)
                                mv.visitInsn(Opcodes.I2B)
                                mv.visitInsn(Opcodes.BASTORE)
                            }
                            for (i in 0..7) {
                                mv.visitVarInsn(Opcodes.ALOAD, 6)
                                mv.visitIntInsn(Opcodes.BIPUSH, i + 8)
                                mv.visitVarInsn(Opcodes.LLOAD, 4)
                                if (i > 0) { mv.visitIntInsn(Opcodes.BIPUSH, i * 8); mv.visitInsn(Opcodes.LUSHR) }
                                mv.visitInsn(Opcodes.L2I)
                                mv.visitInsn(Opcodes.I2B)
                                mv.visitInsn(Opcodes.BASTORE)
                            }

                            // Decrypt: input chars XOR key → byte[] → new String(bytes, "UTF-8")
                            mv.visitVarInsn(Opcodes.ALOAD, 0) // input string
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
                            mv.visitVarInsn(Opcodes.ASTORE, 7) // result bytes

                            mv.visitInsn(Opcodes.ICONST_0)
                            mv.visitVarInsn(Opcodes.ISTORE, 8) // i
                            val decLoopStart = Label()
                            val decLoopEnd = Label()
                            mv.visitLabel(decLoopStart)
                            mv.visitVarInsn(Opcodes.ILOAD, 8)
                            mv.visitVarInsn(Opcodes.ALOAD, 0)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                            mv.visitJumpInsn(Opcodes.IF_ICMPGE, decLoopEnd)

                            // result[i] = (byte)(input.charAt(i) ^ key[i % 16])
                            mv.visitVarInsn(Opcodes.ALOAD, 7)
                            mv.visitVarInsn(Opcodes.ILOAD, 8)
                            mv.visitVarInsn(Opcodes.ALOAD, 0)
                            mv.visitVarInsn(Opcodes.ILOAD, 8)
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false)
                            mv.visitVarInsn(Opcodes.ALOAD, 6)
                            mv.visitVarInsn(Opcodes.ILOAD, 8)
                            mv.visitIntInsn(Opcodes.BIPUSH, 16)
                            mv.visitInsn(Opcodes.IREM)
                            mv.visitInsn(Opcodes.BALOAD)
                            mv.visitInsn(Opcodes.IXOR)
                            mv.visitInsn(Opcodes.I2B)
                            mv.visitInsn(Opcodes.BASTORE)

                            mv.visitIincInsn(8, 1)
                            mv.visitJumpInsn(Opcodes.GOTO, decLoopStart)
                            mv.visitLabel(decLoopEnd)

                            // return new String(result, "UTF-8")
                            mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
                            mv.visitInsn(Opcodes.DUP)
                            mv.visitVarInsn(Opcodes.ALOAD, 7)
                            mv.visitLdcInsn("UTF-8")
                            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([BLjava/lang/String;)V", false)
                            mv.visitInsn(Opcodes.ARETURN)
                            mv.visitMaxs(6, 10)
                            mv.visitEnd()
                        }

                        jos.putNextEntry(JarEntry(entry.name))
                        jos.write(writer.toByteArray())
                        jos.closeEntry()
                        if (metadataStripped) strippedCount++
                    }
                }
            }
            println("[strip] Stripped metadata + SourceFile from $strippedCount classes")
            println("[strip] Encrypted $encryptedStringCount strings in our classes")
        }
    }

    // Since remapForNative already converted MCP→SRG before native-obfuscator,
    // the entire jar is already in SRG. We skip loom's remapJar and just copy
    // the stripped jar as the final output.
    val remapJar = named<RemapJarTask>("remapJar") {
        enabled = false
    }

    val finalJar = register<Copy>("finalJar") {
        dependsOn(stripKotlinMetadata)
        from(layout.buildDirectory.file("intermediates/CatDueller-stripped.jar"))
        into(layout.buildDirectory.dir("libs"))
        rename { "CatDueller-1.0.0.jar" }
    }

    shadowJar {
        destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
        archiveClassifier.set("non-obfuscated-with-deps")
        configurations = listOf(shade)
        exclude("fabric.mod.json")
        doLast {
            configurations.forEach { println("Copying dependencies into mod: ${it.files}") }
        }
        fun relocate(name: String) = relocate(name, "org.afterlike.catdueller.lib.$name")
        relocate("org.java_websocket")
    }

    jar {
        archiveClassifier.set("without-deps")
        destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    }

    assemble.get().dependsOn(finalJar)}
