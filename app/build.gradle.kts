import java.util.Properties

plugins {
    id("com.android.application")
}

// 収益化用の本番 ID は monetization.properties（gitignore 済み）または -P / ~/.gradle/gradle.properties から読む。
// 未設定なら Google 公式のテスト用 ID を使う（本番 ID はリポジトリに絶対にコミットしない）。
val monetizationProps = Properties().apply {
    val f = rootProject.file("monetization.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun monetizationValue(key: String, testDefault: String): String =
    (findProperty(key) as String?) ?: monetizationProps.getProperty(key) ?: testDefault

val admobAppId = monetizationValue("admobAppId", "ca-app-pub-3940256099942544~3347511713")
val admobBannerId = monetizationValue("admobBannerId", "ca-app-pub-3940256099942544/9214589741")

android {
    // アプリ内で言語を切り替えるので、App Bundle にしても言語ごとに分割しない
    bundle { language { enableSplit = false } }
    namespace = "jp.ayu.macro"
    compileSdk = 37

    defaultConfig {
        applicationId = "jp.ayu.macro"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.7"
    }

    buildFeatures {
        buildConfig = true
    }

    // standard: 配布する版。広告・課金 SDK なし、INTERNET 権限なし、全機能無料・無制限。
    // monetized: 収益化の下準備（既定は無効。テスト用 ID）。docs/MONETIZATION_ja.md 参照。
    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            isDefault = true
        }
        create("monetized") {
            dimension = "distribution"
            versionNameSuffix = "-monetized"
            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
            buildConfigField("boolean", "ADMOB_USES_TEST_IDS", (admobBannerId.startsWith("ca-app-pub-3940256099942544")).toString())
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    // monetized フレーバーだけに入る（standard の APK には含まれない）
    "monetizedImplementation"("com.google.android.gms:play-services-ads:25.5.0")
    "monetizedImplementation"("com.google.android.ump:user-messaging-platform:4.0.0")
    "monetizedImplementation"("com.android.billingclient:billing:9.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
