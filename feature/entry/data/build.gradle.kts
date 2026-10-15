import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    id("hermes-quality-gate")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":feature:entry:domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    testImplementation(project(":feature:entry:application"))
    testImplementation(project(":fixtures:hermes:runner"))
}

tasks.test {
    useJUnit()
}

tasks.register<Test>("liveAcceptanceTest") {
    group = "verification"
    description =
        "Runs the live session-matrix and run-matrix acceptance lanes against the deterministic journey Gateway."
    dependsOn("testClasses")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnit()
    filter {
        includeTestsMatching("org.hermesnative.client.feature.entry.data.LiveGatewaySessionAcceptanceTest")
        includeTestsMatching("org.hermesnative.client.feature.entry.data.LiveGatewayRunAcceptanceTest")
    }
    systemProperty("fixture.repositoryRoot", rootProject.projectDir.absolutePath)
    providers.gradleProperty("live.session.matrix.evidenceDir").orNull?.let { evidenceDir ->
        systemProperty("live.session.matrix.evidenceDir", evidenceDir)
    }
    inputs.file(rootProject.file("fixtures/hermes/pinned-fixture.properties"))
        .withPropertyName("pinnedDescriptor")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("fixtures/hermes/journey-tls"))
        .withPropertyName("journeyTls")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
