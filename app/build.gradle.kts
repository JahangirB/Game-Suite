plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// CI stamps each build with its run number, so every published APK has a higher versionCode
// than the one before. Local builds are version 1 and never count as newer than a release.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// The id Android and Firebase know the app by. Set APP_ID to give your own build its own id
// (so it can sit next to someone else's Pip & Pop). It stays com.gamesuite.app so installed
// phones keep updating; the Kotlin code lives in the com.pippop package (the namespace below).
val appId = System.getenv("APP_ID")?.takeIf { it.isNotBlank() } ?: "com.gamesuite.app"

// Push invitations use Firebase, which is optional: google-services.json from your Firebase
// project goes into app/ (CI writes it from the GOOGLE_SERVICES_JSON secret; the file is never
// committed). It is read by hand (no Google Services plugin), so the app still builds without
// it and invitations then stay off.
val firebase: Map<String, String> = file("google-services.json").takeIf { it.exists() }?.let { f ->
    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper().parse(f) as Map<String, Any?>
    val project = json["project_info"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val client = (json["client"] as List<Map<String, Any?>>).first { c ->
        val info = c["client_info"] as Map<String, Any?>
        (info["android_client_info"] as Map<String, Any?>)["package_name"] == appId
    }
    @Suppress("UNCHECKED_CAST")
    mapOf(
        "PROJECT_ID" to project["project_id"].toString(),
        "SENDER_ID" to project["project_number"].toString(),
        "APP_ID" to (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"].toString(),
        "API_KEY" to (client["api_key"] as List<Map<String, Any?>>).first()["current_key"].toString(),
    )
} ?: emptyMap()

// The repository and the branch CI built from: where the updater, the skins and the sticker packs
// come from. Local builds have neither.
val repository = System.getenv("GITHUB_REPOSITORY").orEmpty()
val branch = System.getenv("GITHUB_REF_NAME").orEmpty()

android {
    namespace = "com.pippop"
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        for (key in listOf("PROJECT_ID", "SENDER_ID", "APP_ID", "API_KEY")) {
            buildConfigField("String", "FIREBASE_$key", "\"${firebase[key].orEmpty()}\"")
        }

        // The in-app updater checks the releases of the repository CI built from, so every copy
        // of this project updates from its own releases. Local builds don't update themselves.
        buildConfigField(
            "String", "UPDATE_BASE_URL",
            "\"${if (repository.isEmpty()) "" else "https://github.com/$repository/releases/latest/download"}\"",
        )
        // False in the real app; the preview build type below turns it on.
        buildConfigField("boolean", "PREVIEW", "false")
        // The name phones look for when playing nearby. The preview has its own, so the two never meet.
        buildConfigField("String", "NEARBY_SERVICE_ID", "\"com.arcade.sudoku.battle\"")
        // Ship Battle skins are files in the same repository's skins/ folder, read straight from
        // its default branch: publishing a new one needs no app update. Local builds have none.
        buildConfigField(
            "String", "SKINS_BASE_URL",
            "\"${if (repository.isEmpty()) "" else "https://raw.githubusercontent.com/$repository/HEAD/skins"}\"",
        )
    }

    signingConfigs {
        // Published builds are signed with your own private key, given through the SIGNING_*
        // environment variables (CI fills them from repository secrets), so every update
        // installs over the last one. Without them Android's default debug key is used: fine
        // for trying the app, but it differs per machine.
        getByName("debug") {
            System.getenv("SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // The preview app: the same code under its own id, name and icon colour, so it installs
        // next to the real Pip & Pop and keeps its own data. CI builds it from the work branch
        // and publishes it as the "preview" pre-release, which is also where it updates from.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "PREVIEW", "true")
            buildConfigField("String", "NEARBY_SERVICE_ID", "\"com.arcade.sudoku.battle.preview\"")
            buildConfigField(
                "String", "UPDATE_BASE_URL",
                "\"${if (repository.isEmpty()) "" else "https://github.com/$repository/releases/download/preview"}\"",
            )
            // Skins and sticker packs come from the branch being previewed, not the default branch.
            buildConfigField(
                "String", "SKINS_BASE_URL",
                "\"${if (repository.isEmpty() || branch.isEmpty()) "" else "https://raw.githubusercontent.com/$repository/$branch/skins"}\"",
            )
            // No Firebase: the preview's id is not in the Firebase project, so invitations stay off.
            for (key in listOf("PROJECT_ID", "SENDER_ID", "APP_ID", "API_KEY")) {
                buildConfigField("String", "FIREBASE_$key", "\"\"")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Screenshot tests need the app's resources (fonts, strings) on the JVM.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.play.services.nearby)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
