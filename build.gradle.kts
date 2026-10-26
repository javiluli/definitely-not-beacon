import java.util.zip.ZipFile

plugins {
// Uncomment if you are using IntelliJ.
//  idea
    java
    id("com.azuredoom.hytale-tools") version "1.0.51"
}


tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        memberLevel = org.gradle.external.javadoc.JavadocMemberLevel.PACKAGE
        addStringOption("Xdoclint:-missing", "-quiet")
    }
}

group = project.property("group").toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("java_version").toString().toInt()))
}

hytaleTools {
    javaVersion = property("java_version").toString().toInt()
    hytaleVersion = property("hytale_version").toString()
    manifestServerVersion = property("manifestServerVersion").toString()
    manifestGroup = property("manifest_group").toString()
    modId = property("mod_name").toString()
    modDescription = property("mod_description").toString()
    modUrl = property("mod_url").toString()
    mainClass = property("main_class").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
    curseforgeId = property("curseforgeID").toString()
    disabledByDefault = property("disabled_by_default").toString().toBoolean()
    includesPack = property("includes_pack").toString().toBoolean()
    patchline = property("patchline").toString()
    injectServerJavadocsIntoSources = property("injectServerJavadocsIntoSources").toString().toBoolean()
    generateAssetsBinary = property("generateAssetsBinary").toString().toBoolean()
    // El Beacon no usa el puente del editor; hytale-tools lo incrusta por defecto.
    bundleAssetEditorRuntime = false
    hytaleHomeOverride = providers.gradleProperty("hytaleHomeOverride").orElse("")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

// La fixture y las mediciones acceden a helpers del Beacon sin ampliar su API pública.
val integrationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}

val profiling by sourceSets.creating {
    compileClasspath += integrationTest.output + integrationTest.compileClasspath
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("${project.property("mod_id")}-ht${project.property("hytale_version")}")
    archiveVersion.set(project.property("version").toString())
}

val verifyProductionJar by tasks.registering {
    group = "verification"
    description = "Rejects files from development tools or fixtures in the public plugin JAR."
    val productionJar = tasks.named<Jar>("jar")
    dependsOn(productionJar)
    doLast {
        val allowed = mutableSetOf("META-INF/MANIFEST.MF")
        sourceSets.main.get().output.asFileTree.visit {
            if (!isDirectory) allowed.add(relativePath.pathString)
        }
        ZipFile(productionJar.get().archiveFile.get().asFile).use { archive ->
            val unexpected = archive.entries().asSequence()
                .filter { !it.isDirectory && it.name !in allowed }.map { it.name }.toList()
            check(unexpected.isEmpty()) { "Development files in public JAR: $unexpected" }
        }
    }
}

tasks.check {
    dependsOn(verifyProductionJar)
}

// Uncomment if you are using IntelliJ.
// idea {
//     module {
//         isDownloadSources = true
//         isDownloadJavadoc = true
//     }
// }
