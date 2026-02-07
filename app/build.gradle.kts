plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.parkpin"
    compileSdk = 34 // MODIFICA IMPORTANTE: Usiamo la 34 stabile, la 36 è troppo nuova/beta

    defaultConfig {
        applicationId = "com.example.parkpin"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8 // Java 8 è standard per Android
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // LIBRERIE STANDARD (Uso le versioni esplicite per evitare errori di linking)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // *** QUESTA È QUELLA CHE TI MANCAVA/DAVA ERRORE ***
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.activity:activity:1.8.0")

    // 1. ROOM DATABASE (Java)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")

    // 2. RETROFIT & GSON (Network)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 3. OSMDROID (Mappa) - Con esclusione del conflitto
    implementation("org.osmdroid:osmdroid-android:6.1.18") {
        exclude(group = "com.intellij", module = "annotations")
    }

    // 4. NAVIGAZIONE
    implementation("androidx.navigation:navigation-fragment:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // OSMBonusPack (Per tracciare percorsi e navigazione)
    implementation("com.github.MKergall:osmbonuspack:6.9.0")
}

// Blocco di sicurezza globale per il conflitto Jetbrains/IntelliJ
configurations.all {
    exclude(group = "com.intellij", module = "annotations")
}