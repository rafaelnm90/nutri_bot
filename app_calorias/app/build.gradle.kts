plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// O número da versão vem do GitHub Actions (número da execução), para cada APK
// novo instalar por cima do anterior. Na máquina de quem compila, fica 1.
val numeroVersao = (findProperty("numeroVersao") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.rafaelnm90.calorias"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rafaelnm90.calorias"
        minSdk = 26
        targetSdk = 35
        versionCode = numeroVersao
        versionName = "1.$numeroVersao"
    }

    // Assinatura fixa: o Android só aceita atualizar o app se o APK novo vier
    // assinado com a mesma chave do instalado. Sem ela, cada versão pediria
    // desinstalar (e apagaria o diário). O repositório é público: a chave e a
    // senha ficam nos segredos do GitHub (ASSINATURA_JKS e ASSINATURA_SENHA), e o
    // workflow APK recria o assinatura.jks antes de montar.
    signingConfigs {
        create("fixa") {
            storeFile = file("assinatura.jks")
            storePassword = System.getenv("ASSINATURA_SENHA")
            keyAlias = "calorias"
            keyPassword = System.getenv("ASSINATURA_SENHA")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixa")
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Lembretes de água, comida, check-in e relatório, mesmo com o app fechado.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Leitor de código de barras do Google Play Services: abre a câmera numa tela
    // pronta e devolve o número, sem o app pedir permissão de câmera.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
    // O org.json do Android não funciona em teste local; esta é a mesma biblioteca.
    testImplementation("org.json:json:20240303")
}
