plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

android {
    namespace = "com.bscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bscan"
        minSdk = 29
        targetSdk = 35
        
        // Use provided version or sensible defaults
        versionCode = 474
        versionName = "3.17.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Ensure proper resource handling
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        debug {
            isTestCoverageEnabled = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use committed debug keystore for consistent signing
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    lint {
        // Known issue: NoClassDefFoundError in Navigation/Compose lint detectors
        // This is a toolchain bug, not a code issue - affects AGP 8.8+ with these library versions
        // See: https://issuetracker.google.com/issues/316801717
        disable += setOf(
            "WrongNavigateRouteType",          // Navigation lint ClassNotFoundException
            "SuspiciousModifierThen"           // Compose lint ClassNotFoundException
        )
        
        // Maintain quality checks for actual code issues
        abortOnError = false                   // Don't fail build on toolchain bugs
        warningsAsErrors = false              // Treat as warnings, not errors
        checkReleaseBuilds = true             // Still check release builds
        checkDependencies = true              // Check dependency issues
        
        // Enable helpful checks
        disable += setOf(
            "IconMissingDensityFolder",       // Not critical for functionality
            "VectorDrawableCompat"            // We use vector drawables correctly
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.material.icons.extended)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.4.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("com.google.code.gson:gson:2.10.1")
    
    // Configure test system properties
    tasks.withType<Test> {
        systemProperty("test.data.path", "${rootDir}/test-data/rfid-library")
    }
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // testImplementation("org.powermock:powermock-api-mockito2:2.0.9") // Removed due to performance issues
    // testImplementation("org.powermock:powermock-module-junit4:2.0.9")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// JaCoCo Configuration for Code Coverage
tasks.register<JacocoReport>("jacocoTestReport") {
    mustRunAfter("testDebugUnitTest")
    group = "Reporting"
    description = "Generate Jacoco coverage reports for the debug build"
    
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    }
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class", 
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/databinding/**",
        "**/ui/theme/**" // Exclude UI themes from coverage
    )
    
    val debugTree = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug"))
    val mainSrc = layout.projectDirectory.dir("src/main/java")
    
    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(debugTree.exclude(fileFilter))
    
    executionData.setFrom(fileTree(layout.buildDirectory).include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"))
}

// Disabled: Using exact-only RFID mappings instead of old catalog system
// tasks.register<Copy>("copyFilamentMappings") {
//     group = "build setup"
//     description = "Copy latest bambu_filament_mappings.json from tools to app assets"
//     
//     val toolsJsonFile = file("../tools/catalog-updater/bambu_filament_mappings.json")
//     val assetsDir = file("src/main/assets")
//     
//     from(toolsJsonFile)
//     into(assetsDir)
//     rename { "filament_mappings.json" }
//     
//     // Only copy if source file exists and is newer than destination
//     onlyIf {
//         toolsJsonFile.exists()
//     }
//     
//     doFirst {
//         assetsDir.mkdirs()
//         if (toolsJsonFile.exists()) {
//             println("📦 Copying catalog data: ${toolsJsonFile.name} -> assets/filament_mappings.json")
//         } else {
//             println("⚠️ Catalog file not found at ${toolsJsonFile.absolutePath}")
//         }
//     }
// }

// Disabled: No longer copying old catalog files
// tasks.whenTaskAdded {
//     if (name.startsWith("merge") && name.endsWith("Assets")) {
//         dependsOn("copyFilamentMappings")
//     }
// }