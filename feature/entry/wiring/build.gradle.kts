plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

val sentryDsn: String = providers.gradleProperty("mercurio.sentryDsn").orNull.orEmpty()
require(sentryDsn.none { it == '"' || it == '\\' || it == '\n' || it == '\r' }) {
    "mercurio.sentryDsn must not contain quotes, backslashes, or line breaks."
}

android {
    namespace = "org.mercurio.client.feature.entry.wiring"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":feature:entry:application"))
    implementation(project(":feature:entry:data"))
    implementation(project(":feature:entry:domain"))
    implementation(project(":feature:entry:presentation"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sentry.android.core)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    systemProperty("fixture.repositoryRoot", rootProject.projectDir.absolutePath)
}
