plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kover.gradle.plugin)
    implementation(libs.gradle.pitest.plugin)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.konsist)
}

providers.gradleProperty("architecture.testBuildDir").orNull?.let { testBuildDir ->
    layout.buildDirectory.set(file(testBuildDir))
}

providers.gradleProperty("fixtureDescriptor.testBuildDir").orNull?.let { testBuildDir ->
    layout.buildDirectory.set(file(testBuildDir))
}

tasks.test {
    useJUnit()
    maxParallelForks = 1
    systemProperty("architecture.projectDir", projectDir.parentFile.absolutePath)
}
