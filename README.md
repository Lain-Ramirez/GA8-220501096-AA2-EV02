# Menu08 · módulo móvil

Aplicación Android con la que quien atiende un **food truck** reporta desde el teléfono el punto
donde está parado. Una pantalla para entrar y otra con un botón: se pulsa, el GPS da la posición y
el servidor la guarda en la parada que esté vigente en ese momento.

Es el anexo móvil del proyecto formativo **Menu08**, y existe porque un food truck no tiene
dirección fija: para en puntos distintos según el día, y esos puntos hay que poder corregirlos desde
la calle, no desde un escritorio.

| | |
|---|---|
| **Servidor** | `https://adso.menu08.com` — el único entorno del proyecto |
| **Lenguaje** | Kotlin |
| **Compilación** | Gradle, con el envoltorio incluido (`./gradlew`) |
| **IDE** | Android Studio |
| **Android mínimo** | **7.0 (Nougat, API 24)**, el `minSdk` que declara [`aplicacion/build.gradle.kts`](aplicacion/build.gradle.kts) |
| **Android objetivo** | 15 (API 35) |

## Sin bibliotecas de terceros

**En el APK no entra ninguna biblioteca de terceros**: solo AndroidX, el Material de Google que
exige el tema, y las corrutinas de Kotlin. Las de prueba —JUnit 4, `androidx.test` y Espresso— se
declaran en el mismo catálogo pero con `testImplementation` y `androidTestImplementation`, así que
se quedan fuera de lo que se instala en el teléfono. Todo está en un solo sitio,
[`gradle/libs.versions.toml`](gradle/libs.versions.toml), donde se ve de un vistazo.

No hay cliente HTTP externo: las peticiones salen por `HttpsURLConnection` del propio SDK. Tampoco
hay servicios de Google Play: la ubicación se lee con `LocationManager`, que es parte de Android.
Eso mantiene el APK pequeño y evita que el módulo dependa de que el teléfono tenga los servicios de
Google instalados.

## Dónde está el resto del proyecto

Este repositorio es el submódulo `movil/` de **[Lain-Ramirez/Menu08](https://github.com/Lain-Ramirez/Menu08)**,
donde vive la plataforma completa: la carta pública, el módulo de CAJA y el tablero de producción.

Los **dos servicios que consume esta aplicación** se programan allí, no aquí:

| Servicio | Qué hace |
|---|---|
| `POST /movil/ingresar` | Abre la sesión y devuelve el token contra falsificación de peticiones |
| `POST /movil/ubicacion` | Registra el punto del truck |

Su contrato —cuerpos, códigos y todos los errores— está en
[`docs/api-movil.md`](https://github.com/Lain-Ramirez/Menu08/blob/production/docs/api-movil.md) de
aquel repositorio, y hay una colección de Postman ejecutable en
[`postman/`](https://github.com/Lain-Ramirez/Menu08/tree/production/postman).

Los dos hablan **solo por HTTPS**. El texto claro está prohibido de forma explícita en
[`aplicacion/src/main/res/xml/seguridad_red.xml`](aplicacion/src/main/res/xml/seguridad_red.xml), y
no por herencia del `targetSdk`: si algún día alguien lo bajara, la prohibición seguiría en pie.

---

## Cómo compilar

Hace falta un JDK 17 o posterior —el que exige AGP 9.4; aquí se compiló con el 21— y el SDK de
Android con la plataforma 35. Android Studio los trae; a mano, se instalan con
`sdkmanager "platforms;android-35" "build-tools;36.0.0" "platform-tools"`.

La plataforma es la 35 porque `compileSdk = 35`, pero las herramientas de compilación las elige AGP
y no el `compileSdk`: la 9.4.0 pide la **36.0.0**, y el proyecto no declara `buildToolsVersion` para
bajarla.

1. **Clonar el repositorio.** Si se clona el proyecto completo, este módulo viene como submódulo:

   ```bash
   git clone --recurse-submodules git@github.com:Lain-Ramirez/Menu08.git
   cd Menu08/movil
   ```

   Y si ya estaba clonado sin los submódulos, `git submodule update --init movil`.

2. **Decirle a Gradle dónde está el SDK.** Se crea `local.properties` en la raíz del repositorio,
   junto a `settings.gradle.kts`:

   ```properties
   sdk.dir=/ruta/a/tu/Android/Sdk
   ```

   No está en el repositorio a propósito: la ruta cambia en cada máquina. Android Studio lo escribe
   solo al abrir el proyecto.

3. **Compilar la variante de depuración**, que no necesita nada más:

   ```bash
   ./gradlew assembleDebug
   ```

   El APK queda en `aplicacion/build/outputs/apk/debug/aplicacion-debug.apk`.

4. **Pasar las pruebas** antes de dar nada por bueno:

   ```bash
   ./gradlew aplicacion:testDebugUnitTest
   ```

   Son pruebas de JVM: corren en segundos, sin dispositivo, sin emulador y sin red.

## Cómo instalar en un teléfono

5. **Instalar**, con la depuración por USB activada en el teléfono:

   ```bash
   adb install -r aplicacion/build/outputs/apk/debug/aplicacion-debug.apk
   ```

   El `-r` reinstala conservando los datos.

6. **Si responde `INSTALL_FAILED_UPDATE_INCOMPATIBLE`**, es que ya hay instalada una versión firmada
   con otra clave: el APK de entrega y el de depuración llevan firmas distintas. Hay que desinstalar
   antes, y eso borra los datos de la aplicación:

   ```bash
   adb uninstall com.menu08.movil
   adb install aplicacion/build/outputs/apk/debug/aplicacion-debug.apk
   ```

## Por qué el almacén de firma no está aquí

La variante de entrega se firma con un almacén propio, y **ni el almacén ni sus contraseñas se
versionan**. Los dos archivos no viven igual:

- El almacén (`.jks`) vive **fuera del árbol** del repositorio. `.gitignore` bloquea `*.jks` y
  `*.keystore` como red por si alguien lo copia aquí sin darse cuenta.
- `clave_firma.properties` **sí vive dentro del directorio**, junto a `settings.gradle.kts`, porque
  Gradle lo busca por ruta relativa. Lleva las claves en claro, así que `.gitignore` lo cubre y no
  entra jamás en un commit.

Con una clave en el repositorio, cualquiera que lo clonara podría firmar un APK que el teléfono
aceptaría como actualización de éste. Por eso quien clone el proyecto **puede compilar en depuración
sin nada más**, y `assembleRelease` se detiene diciendo qué archivo falta en lugar de producir un APK
sin firmar, que no se instala en ningún teléfono y se descubre media hora después.

Cómo se genera el almacén y qué claves lleva ese archivo: [`docs/firma-apk.md`](docs/firma-apk.md).

---

## Documentación

| Documento | Qué contiene |
|---|---|
| [`docs/manual-movil.md`](docs/manual-movil.md) | Manual de uso: las dos pantallas, qué hace el botón y qué hacer ante cada mensaje |
| [`docs/pruebas-movil-dispositivo.md`](docs/pruebas-movil-dispositivo.md) | Las pruebas en dispositivo, con su salida real y lo que no cubren |
| [`docs/firma-apk.md`](docs/firma-apk.md) | Cómo se genera el almacén y cómo se firma y verifica el APK de entrega |

## Estructura

Lo que sostiene cada parte. No están todos los archivos: es el mapa para orientarse.

```
aplicacion/src/main/java/com/menu08/movil/
  Aplicacion.kt              instala el almacén de cookies en memoria
  red/
    ClienteMenu08.kt         la única salida a la red: HttpsURLConnection sobre Dispatchers.IO
    Resultado.kt             los tres desenlaces de una llamada, sin depender de org.json
    SesionMovil.kt           la sesión: en memoria y solo en memoria
    LlamadaEnVuelo.kt        una petición que sobrevive al giro de pantalla
  pantallas/
    ActividadIngreso.kt      correo y contraseña
    ActividadUbicacion.kt    la parada vigente y el botón del GPS
    LlamadaIngreso.kt        el ingreso en curso, sobre LlamadaEnVuelo
    LlamadaUbicacion.kt      el reporte en curso, sobre LlamadaEnVuelo
    ValidacionIngreso.kt     lo que se corta antes de salir a la red
    EstadoUbicacion.kt       los cuatro estados del botón, que sobreviven al giro
    Parada.kt · DiaSemana.kt · HorarioParada.kt
  ubicacion/
    GestorUbicacion.kt       permiso, proveedor y lectura del punto
    SaneadorCoordenadas.kt   rango y formato de la coordenada
    AntiguedadPunto.kt       cuándo un punto de reserva ya no vale

aplicacion/src/main/AndroidManifest.xml       permisos, tema y política de red
aplicacion/src/main/res/xml/seguridad_red.xml prohibición explícita del texto claro

aplicacion/src/test/          pruebas de JVM: corren sin dispositivo
aplicacion/src/androidTest/   pruebas instrumentadas: necesitan teléfono
```

**Nada se guarda en el disco del teléfono salvo el correo del último ingreso**, para no teclearlo
cada vez. Ni la contraseña, ni el token, ni el rol: viven en memoria y se pierden al cerrar la
aplicación, que es exactamente lo que se quiere en un teléfono que puede pasar de mano en mano
durante un turno.

---

Proyecto formativo · Tecnólogo en Análisis y Desarrollo de Software (ADSO), SENA · Ficha 3235887
