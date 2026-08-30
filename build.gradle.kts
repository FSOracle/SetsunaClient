import java.net.URI

plugins {
    id("net.fabricmc.fabric-loom") version "1.15.5"
}

val modId = project.property("mod_id").toString()
val modName = project.property("mod_name").toString()
val modAuthor = project.property("mod_author").toString()
val javaVersion = project.property("java_version").toString()
val minecraftVersion = project.property("minecraft_version").toString()
val setsunaViaDir = layout.projectDirectory.dir("src/vendored/setsunavia")
val nestedLibraryModules = (1..67).map { "nested-%03d".format(it) }
val modMenuLocalJar = file(providers.gradleProperty("modmenu_jar")
        .orElse("D:/modmenu-18.0.0-alpha.8.jar")
        .get())
val supportedWebRtcPlatforms = setOf(
        "windows-x86_64", "windows-aarch64", "linux-x86_64", "linux-aarch64", "macos-aarch64"
)
val webRtcPlatform = providers.gradleProperty("webrtc_platform")
        .orElse("windows-x86_64")
        .get()
        .also { require(it in supportedWebRtcPlatforms) }

base {
    archivesName.set(modName)
}

version = project.property("version").toString()
group = project.property("group").toString()

repositories {
    flatDir { dirs("libs/nested") }
    mavenCentral()
    maven("https://repo.viaversion.com")
    maven("https://maven.lenni0451.net/everything")
    maven("https://maven.terraformersmc.com/releases")
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.oryxel1")
            includeGroup("com.github.FPSMasterTeam")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "Sponge"
                url = uri("https://repo.spongepowered.org/repository/maven-public")
            }
        }
        filter { includeGroupAndSubgroups("org.spongepowered") }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "CaffeineMC"
                url = uri("https://maven.caffeinemc.net/releases")
            }
        }
        filter { includeGroup("net.caffeinemc") }
    }
}

sourceSets.named("main") {
    val vendoredSetsunaViaDir = setsunaViaDir.asFile
    java.srcDirs(
            vendoredSetsunaViaDir.resolve("main/java"),
            vendoredSetsunaViaDir.resolve("api/java"),
            vendoredSetsunaViaDir.resolve("visuals/java")
    )
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")
    implementation("net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    compileOnly("net.caffeinemc:sodium-fabric:0.8.12+mc26.1.2")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    implementation("com.google.zxing:core:3.5.1")
    implementation("org.luaj:luaj-jse:3.0.1")
    implementation("com.github.FPSMasterTeam:Cadence:v0.1.1") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.3")
    compileOnly("org.ow2.asm:asm:9.8")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.checkerframework:checker-qual:3.12.0")
    if (modMenuLocalJar.isFile) compileOnly(files(modMenuLocalJar))
    else compileOnly("com.terraformersmc:modmenu:18.0.0-alpha.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.ow2.asm:asm:9.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")

    implementation("com.viaversion:viaversion-common:5.10.0")
    implementation("com.viaversion:viabackwards-common:5.10.0")
    implementation("com.viaversion:viaaprilfools-common:4.2.1")
    implementation("net.raphimc:ViaLegacy:3.0.16")
    implementation("com.seedfinding:mc_biome:1.171.1")
    implementation("com.seedfinding:mc_noise:1.171.1")
    implementation("com.seedfinding:mc_seed:1.171.2")
    implementation("com.seedfinding:mc_math:1.171.0")
    implementation("com.seedfinding:mc_core:1.210.0")
    implementation("net.raphimc:ViaBedrock:0.0.29-SNAPSHOT") {
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "at.yawk.lz4", module = "lz4-java")
        exclude(group = "io.netty")
    }
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("io.jsonwebtoken:jjwt-impl:0.13.0")
    implementation("io.jsonwebtoken:jjwt-gson:0.13.0") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("net.lenni0451:Reflect:1.6.3")
    implementation("net.lenni0451.commons:unchecked:1.9.2")
    implementation("de.florianreuth:classic4j:2.3.0")
    implementation("net.raphimc:MinecraftAuth:5.0.1") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("dev.kastle.netty:netty-transport-raknet:1.7.0") { exclude(group = "io.netty") }
    implementation("dev.kastle.netty:netty-transport-nethernet:1.7.0") { exclude(group = "io.netty") }
    implementation("dev.kastle.webrtc:webrtc-java:1.0.3:$webRtcPlatform")

    val skijaVersion = "0.143.17"
    implementation("io.github.humbleui:skija-windows-x64:$skijaVersion")
    nestedLibraryModules.forEach { include("setsuna.nested:$it:1.0.0") }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }


loom {
    val aw = file("src/main/resources/${modId}.accesswidener")
    if (aw.exists()) accessWidenerPath.set(aw)
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("runs/client")
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion.toInt())
}

tasks.named<ProcessResources>("processResources") {
    val vendoredSetsunaViaDir = setsunaViaDir.asFile
    from(vendoredSetsunaViaDir.resolve("main/resources")) {
        exclude("fabric.mod.json", "setsunavia.accesswidener")
    }
    from(vendoredSetsunaViaDir.resolve("api/resources")) { exclude("fabric.mod.json") }
    from(vendoredSetsunaViaDir.resolve("visuals/resources")) {
        exclude("fabric.mod.json", "setsunavia-visuals.accesswidener")
    }
    val props = mapOf(
            "version" to version,
            "mod_id" to modId,
            "mod_name" to modName,
            "mod_author" to modAuthor,
            "license" to project.property("license").toString(),
            "description" to (project.findProperty("description")?.toString() ?: ""),
            "minecraft_version" to minecraftVersion,
            "fabric_loader_version" to project.property("fabric_loader_version").toString(),
            "java_version" to javaVersion
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json")) { expand(props) }
}

tasks.named<Jar>("jar") {
    from(setsunaViaDir.file("LICENSE")) { rename("LICENSE", "LICENSE_SetsunaVia") }
    manifest {
        attributes(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Implementation-Title" to project.name,
                "Implementation-Version" to archiveVersion,
                "Implementation-Vendor" to modAuthor,
                "Built-On-Minecraft" to minecraftVersion
        )
    }
}

tasks.named<Jar>("sourcesJar") {
    from(setsunaViaDir.file("LICENSE")) { rename("LICENSE", "LICENSE_SetsunaVia") }
}
