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
// AGP 9.4.0 compila Kotlin sin declarar `org.jetbrains.kotlin.android`: el complemento viaja
// dentro del propio complemento de Android (kotlin-gradle-plugin 2.2.10), por eso no aparece
// en el catalogo de versiones. Declararlo a mano seria anadir una dependencia de compilacion
// que este proyecto no necesita.
//
// compileSdk y targetSdk van en 35: se compila y se declara comportamiento contra la misma
// version, que es lo que Google exige para publicar y lo que evita sorpresas de borde a borde.

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// -----------------------------------------------------------------------------------------
// Firma del APK de entrega.
//
// Las contrasenas viven en clave_firma.properties, junto a settings.gradle.kts y fuera del
// control de versiones; el almacen vive fuera del arbol del repositorio. Ninguno de los dos se
// puede reconstruir desde aqui, y esa es la idea: quien clone el proyecto compila en depuracion
// sin nada mas, pero solo puede firmar la entrega quien tenga el almacen.
//
// El archivo declara cuatro claves:
//
//     almacen=/ruta/absoluta/al/almacen_menu08.jks
//     clave_almacen=...
//     alias=menu08
//     clave_alias=...
// -----------------------------------------------------------------------------------------
val archivoFirma = rootProject.file("clave_firma.properties")
val hayFirma = archivoFirma.exists()

// Sin el archivo NO se deja compilar la variante de entrega. La alternativa seria producir un
// APK sin firmar, que no se instala en ningun telefono y se descubre media hora despues, con el
// archivo ya subido a algun sitio. Es mejor detenerse aqui y decir que falta.
//
// Se mira lo que se pidio por linea de ordenes en vez de lanzar desde la tarea: assembleRelease
// es una tarea de ciclo de vida y sus dependencias corren ANTES que su propio doFirst, asi que
// el aviso llegaria cuando el APK ya estuviera empaquetado.
val pideEntrega = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

if (pideEntrega && !hayFirma) {
    throw GradleException(
        "Falta ${archivoFirma.absolutePath}: sin el no se puede firmar el APK de entrega.\n" +
            "La depuracion sigue funcionando: ./gradlew aplicacion:assembleDebug\n" +
            "Como se genera el almacen y que claves lleva el archivo, en docs/firma-apk.md."
    )
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

    signingConfigs {
        create("entrega") {
            if (hayFirma) {
                val claves = Properties().apply {
                    archivoFirma.inputStream().use { load(it) }
                }

                storeFile = file(claves.getProperty("almacen"))
                storePassword = claves.getProperty("clave_almacen")
                keyAlias = claves.getProperty("alias")
                keyPassword = claves.getProperty("clave_alias")
            }
        }
    }

    buildTypes {
        // La entrega se firma con el almacen propio, no con el de depuracion que Android Studio
        // crea en cada maquina: ese lo comparten todos los proyectos y no permite actualizar la
        // aplicacion despues con otra firma.
        //
        // Sin minificar, a proposito. R8 renombraria las clases y las trazas de un fallo en el
        // telefono dejarian de decir donde ocurrio, que es justo lo que hace falta durante la
        // sustentacion. El APK son cinco megas: no hay nada que ahorrar aqui.
        release {
            signingConfig = signingConfigs.getByName("entrega")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // AGP 8 y posteriores traen buildConfig desactivado. Sin esto, BuildConfig.DEBUG no se
    // resuelve, y es la guarda que deja las trazas de la capa de red fuera del APK de entrega.
    buildFeatures {
        buildConfig = true
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

    // La bateria de pruebas de JVM del #14 no anade NI UNA linea aqui: corre entera sobre el
    // JUnit 4 que ya traia la plantilla. Lo que la hace posible no es una biblioteca, es que las
    // cuatro reglas que ejercita —rango y formato de la coordenada, traduccion del codigo de
    // respuesta, antiguedad del punto y numeracion del dia— viven en funciones puras, sin un
    // solo import de android.* ni de org.json.
    //
    // Y tampoco se declara:
    //
    //     testOptions { unitTests.isReturnDefaultValues = true }
    //
    // Esa es, en Kotlin DSL, la linea que cambiaria el `throw RuntimeException("Method ... not
    // mocked.")` del jar de pruebas por un null o un 0. Parece la solucion y es peor que el
    // problema: JSONObject seguiria sin analizar nada, solo que en vez de reventar devolveria
    // vacio, y el aserto pasaria a comprobar una respuesta inventada. Un fallo ruidoso se
    // convertiria en una prueba verde que no prueba nada.
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
