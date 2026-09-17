import java.security.MessageDigest
plugins { id("fabric-loom") version "1.15.5" }
version = property("mod_version") as String
group = property("maven_group") as String
base { archivesName.set(property("archives_base_name") as String) }
repositories { mavenCentral(); maven("https://api.modrinth.com/maven") }
val launcherHome = providers.environmentVariable("TROPIMON_HOME").orNull?.let(::file)
    ?: providers.environmentVariable("APPDATA").orNull?.let { file(it).resolve(".tropimon") }
    ?: file(System.getProperty("user.home")).resolve(".tropimon")
val overrideJar = providers.gradleProperty("cobblemonJar").orNull?.let(::file)
val installed = launcherHome.resolve("mods").listFiles()?.filter {
    it.isFile && it.name.matches(Regex("Cobblemon-fabric-.+\\.jar", RegexOption.IGNORE_CASE))
}.orEmpty()
val officialOnly = providers.gradleProperty("officialDependenciesOnly").isPresent
val cbJar = if (officialOnly) null else overrideJar ?: run {
    check(installed.size == 1) { "Exactly one active Cobblemon JAR is required, or specify -PcobblemonJar / -PofficialDependenciesOnly." }
    installed.single()
}
check(cbJar == null || cbJar.isFile) { "Invalid Cobblemon JAR override." }
dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings("net.fabricmc:yarn:1.21.1+build.3:v2")
    modImplementation("net.fabricmc:fabric-loader:0.17.2")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.116.6+1.21.1")
    if (cbJar != null) modImplementation(files(cbJar))
    else modImplementation("maven.modrinth:MdwFAVRL:YgmyyFcs")
    modRuntimeOnly("net.fabricmc:fabric-language-kotlin:1.13.7+kotlin.2.2.21")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
}
java { withSourcesJar() }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(21) }
tasks.test { useJUnitPlatform() }
tasks.processResources {
    inputs.property("version", project.version)
    doFirst {
        check(file("src/main/resources/fabric.mod.json").readText().contains("\"cobblemon\": \">=1.8.0\"")) {
            "Cobblemon minimum must remain >=1.8.0 with no artificial minor upper bound."
        }
    }
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
tasks.withType<Jar>().configureEach {
    exclude("**/.git/**", "**/.env*", "**/*.log", "**/config/**", "**/saves/**", "**/backups/**")
    from("LICENSE")
    from("THIRD_PARTY.md")
}
val privacyCheck by tasks.registering(Exec::class) {
    dependsOn(tasks.remapJar, tasks.remapSourcesJar)
    commandLine(file(System.getProperty("java.home")).resolve("bin/java").absolutePath,
        "tools/PrivacyCheck.java", projectDir.absolutePath,
        tasks.remapJar.get().archiveFile.get().asFile.absolutePath,
        tasks.remapSourcesJar.get().archiveFile.get().asFile.absolutePath)
}
val testLocalDelivery by tasks.registering(Exec::class) {
    dependsOn(tasks.remapJar)
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        file("tools/TestDeployment.ps1").absolutePath, "-Jar", tasks.remapJar.get().archiveFile.get().asFile.absolutePath)
}
tasks.check { dependsOn(privacyCheck, testLocalDelivery) }
tasks.register("prepareReleaseDelivery") {
    dependsOn(tasks.build)
    doLast {
        val source = tasks.remapJar.get().archiveFile.get().asFile
        for (kind in listOf("local", "shareable")) {
            val dir = layout.buildDirectory.dir("release/${project.version}/$kind").get().asFile.apply { mkdirs() }
            val suffix = if (kind == "local") "-LOCAL" else ""
            val dest = dir.resolve("TropimonFarm-${project.version}+1.21.1$suffix.jar")
            source.copyTo(dest, true)
            val hash = MessageDigest.getInstance("SHA-256").digest(dest.readBytes()).joinToString("") { "%02x".format(it) }
            dest.resolveSibling(dest.name + ".sha256").writeText(hash + "\n")
            if (kind == "local") file("tools/install-local-deferred.ps1").copyTo(dir.resolve("install-local-deferred.ps1"), true)
        }
    }
}
val smoke by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
val smokeJar by tasks.registering(Jar::class) {
    from(smoke.output)
    archiveClassifier.set("smoke-dev")
    destinationDirectory.set(layout.buildDirectory.dir("smoke-helper"))
}
tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapSmokeJar") {
    inputFile.set(smokeJar.flatMap { it.archiveFile })
    archiveClassifier.set("smoke")
    destinationDirectory.set(layout.buildDirectory.dir("smoke-helper"))
    addNestedDependencies.set(false)
}
