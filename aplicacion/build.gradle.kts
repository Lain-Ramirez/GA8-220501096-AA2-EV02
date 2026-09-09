// Modulo `aplicacion`: la aplicacion Android del modulo movil de Menu08.
//
// Por que minSdk = 24:
//   - El permiso de ubicacion se pide siempre en tiempo de ejecucion. Eso existe desde la
//     API 23, asi que con 24 como minimo no queda ninguna version de Android donde el permiso
//     se conceda al instalar: el codigo del permiso tiene un solo camino.
//   - LocationManager.getCurrentLocation() solo existe desde la API 30. De 24 a 29 hay que
//     leer el punto con requestLocationUpdates() y darse de baja tras la primera lectura, asi
//     que la captura del GPS conserva esa rama alternativa.
//
// compileSdk y targetSdk van en 35: se compila y se declara comportamiento contra la misma
// version, que es lo que Google exige para publicar y lo que evita sorpresas de borde a borde.

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.menu08.movil"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.menu08.movil"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Lo que trae la plantilla de actividad vacia con vistas.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Anadido: el hilo de fondo de la capa de red se escribe con corrutinas, y esta no viene
    // en la plantilla. El tema de la aplicacion hereda de Theme.Material3.DayNight.NoActionBar,
    // asi que el Material de Google de arriba tampoco se puede quitar: sin el no arranca.
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
