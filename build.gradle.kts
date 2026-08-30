import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

group = "net.shopplugin"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:9.1.0")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    relocate("org.sqlite", "net.shopplugin.libs.sqlite")
    relocate("com.zaxxer.hikari", "net.shopplugin.libs.hikari")
    relocate("com.mysql", "net.shopplugin.libs.mysql")
    minimize {
        // These three all rely on reflection and/or java.util.ServiceLoader
        // (JDBC driver registration, Hikari's internal driver detection) to
        // find classes that are never referenced directly in our bytecode.
        // Shadow's minimize() only sees direct bytecode references, so
        // without these excludes it can strip classes these libraries load
        // reflectively at runtime, producing a jar that compiles and builds
        // fine but throws ClassNotFoundException/SQLException the moment
        // the database layer is actually used.
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        exclude(dependency("com.zaxxer:HikariCP:.*"))
        exclude(dependency("com.mysql:mysql-connector-j:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
