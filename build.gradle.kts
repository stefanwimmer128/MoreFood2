import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.matthewprenger.cursegradle.CurseProject
import net.minecraftforge.gradle.user.IReobfuscator
import net.minecraftforge.gradle.user.ReobfMappingType
import net.minecraftforge.gradle.user.ReobfTaskFactory
import net.minecraftforge.gradle.user.UserBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

object Config {
    val modid = "morefood2"
    val version = "1.0.0"
    val type = "release"
    
    val minecraft = "1.12.2"
    val forge = "14.23.5.2768"
    val mappings = "snapshot_20171003"
    
    val curse = "273296"
}


buildscript {
    repositories {
        jcenter()
        
        maven {
            setUrl("https://plugins.gradle.org/m2/")
        }
        
        maven {
            setUrl("http://files.minecraftforge.net/maven")
        }
    }
    
    dependencies {
        classpath("net.minecraftforge.gradle:ForgeGradle:2.3-SNAPSHOT")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.11"
    id("com.github.johnrengelman.shadow") version "4.0.1"
    id("com.matthewprenger.cursegradle") version "1.1.0"
    id("com.jfrog.bintray") version "1.8.4"
    id("maven-publish")
}

apply(plugin = "net.minecraftforge.gradle.forge")

group = "eu.stefanwimmer128.morefood2"
version = Config.version

base.archivesBaseName = "${Config.modid}_${Config.minecraft}"

repositories {
    jcenter()
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

val Project.minecraft
    get() =
        extensions.getByName<UserBaseExtension>("minecraft")
minecraft.apply {
    version = "${Config.minecraft}-${Config.forge}"
    mappings = Config.mappings
    
    runDir = "run"
    
    makeObfSourceJar = true
    
    replaceIn("src/api/kotlin/eu/stefanwimmer128/morefood2/api/MoreFood2API.kt")
    replace("#{VERSION}", Config.version)
}

val Project.reobf
    get() =
        extensions.getByName<NamedDomainObjectContainer<IReobfuscator>>("reobf")
reobf.apply {
    create("shadowJar").apply {
        mappingType = ReobfMappingType.SEARGE
    }
}

the<JavaPluginConvention>().apply {
    sourceCompatibility = JavaVersion.toVersion("1.8")
    targetCompatibility = JavaVersion.toVersion("1.8")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
    
    getByName<ProcessResources>("processResources") {
        inputs.property("version", project.version)
        inputs.property("mcversion", project.minecraft.version)
        
        from(sourceSets["main"].resources.srcDirs) {
            include("mcmod.info")
            
            expand(mapOf("version" to project.version, "mcversion" to project.minecraft.version))
        }
        
        from(sourceSets["main"].resources.srcDirs) {
            exclude("mcmod.info")
        }
    }
    
    getByName<Jar>("jar") {
        from(sourceSets["api"].output)
        classifier = "thin"
    }
    
    val shadowJar = getByName<ShadowJar>("shadowJar") {
        from(sourceSets["api"].output)
        classifier = ""
    }
    
    getByName<Jar>("sourceJar") {
        from(sourceSets["api"].allSource)
    }
    
    getByName("reobfShadowJar") {
        mustRunAfter(shadowJar)
    }
    
    create<ConfigureShadowRelocation>("relocateShadowJar") {
        target = shadowJar
        prefix = "shadow.morefood2"
        
        dependsOn(shadowJar)
    }
    
    create<Jar>("apiThinJar") {
        from(sourceSets["api"].output)
        
        classifier = "api-thin"
    }
    
    val apiJar = create<ShadowJar>("apiJar") {
        from(sourceSets["api"].output)
        
        classifier = "api"
    }
    
    create<ConfigureShadowRelocation>("relocateApiJar") {
        target = apiJar
        prefix = "shadow.morefood2"
        
        dependsOn(apiJar)
    }
    
    create<Jar>("deobfThinJar") {
        from(sourceSets["api"].output)
        from(sourceSets["main"].output)
        
        classifier = "deobf-thin"
    }
    
    val deobfJar = create<ShadowJar>("deobfJar") {
        from(sourceSets["api"].output)
        from(sourceSets["main"].output)
        
        classifier = "deobf"
    }
    
    create<ConfigureShadowRelocation>("relocateDeobfJar") {
        target = deobfJar
        prefix = "shadow.morefood2"
        
        dependsOn(deobfJar)
    }
}

artifacts {
    add("archives", tasks["apiJar"])
    add("archives", tasks["apiThinJar"])
    add("archives", tasks["deobfJar"])
    add("archives", tasks["deobfThinJar"])
}

curseforge {
    apiKey = System.getenv("CURSE_API_TOKEN") ?: ""
    
    project(closureOf<CurseProject> {
        id = Config.curse
        
        changelog = "Forge version: ${Config.minecraft}-${Config.forge}"
        
        releaseType = Config.type
        
        addGameVersion(Config.minecraft)
        
        mainArtifact(tasks["jar"])
        
        addArtifact(tasks["sourceJar"])
        addArtifact(tasks["deobfJar"])
        addArtifact(tasks["apiJar"])
    })
}

val PUBLICATIONS = mapOf(
    "jar" to null,
    "apiThinJar" to "api",
    "deobfThinJar" to "deobf"
)

bintray {
    user = System.getenv("BINTRAY_USER")
    key = System.getenv("BINTRAY_KEY")
    pkg.apply {
        repo = "maven"
        name = Config.modid
        
        version.apply {
            name = Config.version
            desc = "Minecraft Forge: ${Config.minecraft}-${Config.forge}"
            vcsTag = "v${Config.version}"
        }
    }
    withGroovyBuilder {
        "publications"(PUBLICATIONS.keys.toTypedArray())
    }
    override = true
    publish = true
}

publishing {
    publications {
        PUBLICATIONS.forEach { source, target ->
            create<MavenPublication>(source) {
                from(components["java"])
                
                artifactId = Config.modid + (target?.let { "-$it" } ?: "")
                version = Config.version
                
                artifacts.removeAll(artifacts)
                artifact(tasks[source]) {
                    classifier = null
                }
                artifact(tasks["sourceJar"])
                
                pom.withGroovyBuilder {
                    "setName"("MoreFood 2")
                    "setDescription"("Minecraft Forge: ${Config.minecraft}-${Config.forge}")
                    "setUrl"("https://minecraft.curseforge.com/projects/morefood2")
                    "licenses" {
                        "license" {
                            "setName"("ISC")
                            "setUrl"("https://raw.githubusercontent.com/stefanwimmer128/morefood2/master/LICENSE")
                            "setDistribution"("repo")
                        }
                    }
                    "developers" {
                        "developer" {
                            "setId"("stefanwimmer128")
                            "setName"("Stefan Wimmer")
                            "setEmail"("info@stefanwimmer128.eu")
                            "setUrl"("https://stefanwimmer128.eu")
                            "setRoles"(setOf("developer"))
                        }
                    }
                    "contributors" {
                        "contributor" {
                            "setName"("uriba")
                        }
                    }
                    "scm" {
                        "setConnection"("scm:git:https://github.com/stefanwimmer128/morefood2.git")
                        "setDeveloperConnection"("scm:git:git@github.com:stefanwimmer128/morefood2.git")
                        "setUrl"("https://github.com/stefanwimmer128/morefood2")
                    }
                    "issueManagement" {
                        "setSystem"("github")
                        "setUrl"("https://github.com/stefanwimmer128/morefood2/issues")
                    }
                }
            }
        }
    }
}
