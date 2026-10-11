plugins {
    id("net.fabricmc.fabric-loom") version "1.15.5"
}

val modId = project.property("mod_id").toString()
val modName = project.property("mod_name").toString()
val modAuthor = project.property("mod_author").toString()
val javaVersion = project.property("java_version").toString()
val minecraftVersion = project.property("minecraft_version").toString()
val modMenuLocalJar = file(providers.gradleProperty("modmenu_jar")
        .orElse("libs/modmenu-18.0.0-alpha.8.jar")
        .get())
base {
    archivesName.set("${modId}-${minecraftVersion}")
}

version = project.property("version").toString()
group = project.property("group").toString()

repositories {
    mavenCentral()
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

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")
    implementation("net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    compileOnly("net.caffeinemc:sodium-fabric:0.8.12+mc26.1.2")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    implementation("com.google.zxing:core:3.5.1")
    include("com.google.zxing:core:3.5.1")
    implementation("org.luaj:luaj-jse:3.0.1")
    include("org.luaj:luaj-jse:3.0.1")
    implementation("com.github.FPSMasterTeam:Cadence:v0.1.1") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    include("com.github.FPSMasterTeam:Cadence:v0.1.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    include("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")

    compileOnly("io.github.llamalad7:mixinextras-common:0.5.3")
    compileOnly("org.ow2.asm:asm:9.8")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.checkerframework:checker-qual:3.12.0")
    if (modMenuLocalJar.isFile) {
        compileOnly(files(modMenuLocalJar))
    } else {
        compileOnly("com.terraformersmc:modmenu:18.0.0-alpha.8")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.ow2.asm:asm:9.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")

    val skijaVersion = "0.143.17"
    implementation("io.github.humbleui:skija-windows-x64:$skijaVersion")
    include("io.github.humbleui:skija-windows-x64:$skijaVersion")
    include("io.github.humbleui:skija-shared:$skijaVersion")
    include("io.github.humbleui:types:0.2.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

loom {
    val aw = file("src/main/resources/${modId}.accesswidener")
    if (aw.exists()) {
        accessWidenerPath.set(aw)
    }
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
    filesMatching(listOf("fabric.mod.json")) {
        expand(props)
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Implementation-Title" to project.name,
                "Implementation-Version" to archiveVersion,
                "Implementation-Vendor" to modAuthor,
                "Built-On-Minecraft" to minecraftVersion
            )
        )
    }
}
