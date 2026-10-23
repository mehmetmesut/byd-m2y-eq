import java.util.Properties

plugins {
    id("com.android.application")
}

// M2Y release key lives outside the repo; local.properties (gitignored) points to its properties file
val signingProps = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { signingProps.load(it) }
signingProps.getProperty("m2y.signingProps")?.let { path -> File(path).reader().use { signingProps.load(it) } }

android {
    namespace = "com.m2y.eq"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.m2y.eq"
        minSdk = 28 // DynamicsProcessing
        // BYD head unit is Android 10; targeting it avoids later foreground-service type rules
        targetSdk = 29
        versionCode = 125076802
        versionName = "1.25.07.68.2"
    }

    signingConfigs {
        if (signingProps.getProperty("m2y.storeFile") != null) create("m2y") {
            storeFile = file(signingProps.getProperty("m2y.storeFile"))
            storePassword = signingProps.getProperty("m2y.storePassword")
            keyAlias = signingProps.getProperty("m2y.keyAlias")
            keyPassword = signingProps.getProperty("m2y.keyPassword") ?: signingProps.getProperty("m2y.storePassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("m2y")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    lint {
        // targetSdk 29 is intentional (single device, Android 10)
        disable += "ExpiredTargetSdkVersion"
        abortOnError = false
    }

    androidComponents.onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = "M2YEQ_${defaultConfig.versionName}-${variant.buildType}.apk"
            }
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
