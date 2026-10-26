import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    id("mercurio-quality-gate")
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
    testImplementation(project(":fixtures:mercurio:runner"))
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
        includeTestsMatching("org.mercurio.client.feature.entry.data.LiveGatewaySessionAcceptanceTest")
        includeTestsMatching("org.mercurio.client.feature.entry.data.LiveGatewayRunAcceptanceTest")
    }
    systemProperty("fixture.repositoryRoot", rootProject.projectDir.absolutePath)
    providers.gradleProperty("live.session.matrix.evidenceDir").orNull?.let { evidenceDir ->
        systemProperty("live.session.matrix.evidenceDir", evidenceDir)
    }
    providers.gradleProperty("live.fixture.parity.artifactDir").orNull?.let { artifactDir ->
        systemProperty("live.fixture.parity.artifactDir", artifactDir)
    }
    providers.gradleProperty("live.gateway.endpoint").orNull?.let { endpoint ->
        systemProperty("live.gateway.endpoint", endpoint)
    }
    providers.gradleProperty("live.gateway.bearerToken").orNull?.let { bearerToken ->
        systemProperty("live.gateway.bearerToken", bearerToken)
    }
    providers.gradleProperty("live.fixture.fileDriftIssues").orNull?.let { fileDriftIssues ->
        systemProperty("live.fixture.fileDriftIssues", fileDriftIssues)
    }
    inputs.file(rootProject.file("fixtures/mercurio/pinned-fixture.properties"))
        .withPropertyName("pinnedDescriptor")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("fixtures/mercurio/journey-tls"))
        .withPropertyName("journeyTls")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
