plugins {
    java
    application
    id("me.champeau.jmh") version "0.7.2"
}

group = "com.lab"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    named("jmh") {
        java.srcDir("src/main/java")
        java.include("org/lab/lsh/bench/**")
    }
}

application {
    mainClass.set("com.lab.lsh.Main")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf(
        "-Xms2g",
        "-Xmx2g",
        "-XX:+UseG1GC"
    )
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    timeUnit.set("ns")
    benchmarkMode.set(listOf("avgt"))
    profilers.set(listOf("gc"))
    jvmArgs.set(listOf(
        "-Xms2g",
        "-Xmx2g",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DebugNonSafepoints"
    ))
}