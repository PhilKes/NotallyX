import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.io.FileHandler

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
    id("com.ncorti.ktfmt.gradle") version "0.20.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"
    id("io.github.philkes.android-translations-converter") version "1.0.5"
}

android {
    namespace = "com.philkes.notallyx"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.philkes.notallyx"
        minSdk = 21
        targetSdk = 34
        versionCode = project.findProperty("app.versionCode").toString().toInt()
        versionName = project.findProperty("app.versionName").toString()
        resourceConfigurations += listOf(
            "en", "ca", "cs", "da", "de", "el", "es", "fr", "hu", "in", "it", "ja", "my", "nb", "nl", "nn", "pl", "pt-rBR", "pt-rPT", "ro", "ru", "sk", "sv", "tl", "tr", "uk", "vi", "zh-rCN", "zh-rTW"
        )
        vectorDrawables.generatedDensities?.clear()
    }

    ksp {
        arg("room.generateKotlin", "true")
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("RELEASE_STORE_FILE").get())
            storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "app_name", "NotallyX DEBUG")
        }
        release {
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        create("beta"){
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-BETA"
            resValue("string", "app_name", "NotallyX BETA")
        }
    }

    applicationVariants.all {
        this.outputs
            .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "NotallyX-$versionName.apk"
            }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes += listOf(
            "DebugProbesKt.bin",
            "META-INF/**.version",
            "kotlin/**.kotlin_builtins",
            "kotlin-tooling-metadata.json"
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ktfmt {
    kotlinLangStyle()
}

tasks.register<KtfmtFormatTask>("ktfmtPrecommit") {
    source = project.fileTree(rootDir)
    include("**/*.kt")
}

tasks.register<Copy>("installLocalGitHooks") {
    val scriptsDir = File(rootProject.rootDir, ".scripts/")
    val hooksDir = File(rootProject.rootDir, ".git/hooks")
    from(scriptsDir) {
        include("pre-commit", "pre-commit.bat")
    }
    into(hooksDir)
    inputs.files(file("${scriptsDir}/pre-commit"), file("${scriptsDir}/pre-commit.bat"))
    outputs.dir(hooksDir)
    fileMode = 509 // 0775 octal in decimal
    // If this throws permission denied:
    // chmod +rwx ./.git/hooks/pre-commit*
}

tasks.preBuild.dependsOn(tasks.named("installLocalGitHooks"), tasks.exportTranslationsToExcel)

tasks.register("generateChangelogs") {
    doLast {
        exec {
            commandLine("bash", rootProject.file("generate-changelogs.sh").absolutePath,
                "v${project.findProperty("app.lastVersionName").toString()}",
                rootProject.file("CHANGELOG.md").absolutePath)
            standardOutput = System.out
            errorOutput = System.err
            isIgnoreExitValue = true
        }
        val config = PropertiesConfiguration()
        val fileHandler = FileHandler(config).apply {
            file = rootProject.file("gradle.properties")
            load()
        }
        val currentVersionName = config.getProperty("app.versionName")
        config.setProperty("app.lastVersionName", currentVersionName)
        fileHandler.save()
        println("Updated app.lastVersionName to $currentVersionName")
    }
}

afterEvaluate {
    tasks.named("bundleRelease").configure {
        dependsOn(tasks.named("testReleaseUnitTest"))
    }
    tasks.named("assembleRelease").configure {
        dependsOn(tasks.named("testReleaseUnitTest"))
        finalizedBy(tasks.named("generateChangelogs"))
    }
}

dependencies {
    val navVersion = "2.3.5"
    val roomVersion = "2.6.1"

    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")
    implementation("androidx.preference:preference-ktx:1.2.1")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("androidx.work:work-runtime:2.9.1")

    implementation("cat.ereza:customactivityoncrash:2.4.0")
    implementation("com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("cn.Leaqi:SwipeDrawer:1.6")
    implementation("com.github.skydoves:colorpickerview:2.3.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("me.zhanghai.android.fastscroll:library:1.3.0")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("net.zetetic:android-database-sqlcipher:4.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.ocpsoft.prettytime:prettytime:4.0.6.Final")
    implementation("org.simpleframework:simple-xml:2.7.1") {
        exclude(group = "xpp3", module = "xpp3")
    }

    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.work:work-testing:2.9.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.json:json:20180813")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.mockito:mockito-core:5.13.0")
    testImplementation("org.robolectric:robolectric:4.13")
}