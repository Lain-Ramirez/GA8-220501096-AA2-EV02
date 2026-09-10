# Firma del APK de entrega

El archivo que se entrega es **`Menu08Movil-1.0.apk`**, compilado en la variante `release` y firmado
con un almacén de claves propio. Generado y verificado el **10 de septiembre de 2026**.

Un APK de depuración **no sirve como entrega**: Android Studio lo firma con el almacén de
depuración de la máquina, una clave genérica que comparten todos los proyectos de ese equipo y que
no permite publicar la aplicación ni actualizarla después con otra firma.

## Dónde está cada cosa

| Archivo | Dónde vive | Versionado |
|---|---|---|
| `almacen_menu08.jks` | **Fuera del repositorio**, en la carpeta personal de quien firma | Nunca |
| `clave_firma.properties` | Dentro del repositorio, junto a `settings.gradle.kts` | **Nunca**: lo cubre `.gitignore` |
| `Menu08Movil-1.0.apk` | `aplicacion/build/outputs/apk/release/` | No: la carpeta `build` está ignorada |

Los dos primeros no viven igual a propósito. El almacén queda fuera del árbol para que ni un
descuido lo acerque a un `git add`; el archivo de contraseñas tiene que estar dentro para que Gradle
lo encuentre por ruta relativa, así que su defensa es `.gitignore` y nada más.

**El almacén no se puede regenerar.** Si se pierde, no hay forma de firmar una actualización de esta
misma aplicación: Android rechaza instalar encima un APK con otra firma. Conviene una copia fuera de
la máquina de desarrollo.

## Cómo se generó

```bash
keytool -genkeypair \
  -keystore /home/<usuario>/almacen_menu08.jks \
  -alias menu08 \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=..., O=SENA ficha 3235887, L=Bogota, C=CO"
```

`keytool` viene con el JDK que trae Android Studio, en `<android-studio>/jbr/bin/`. Los 10 000 días
de validez son unos 27 años: es lo habitual para una clave de aplicación y deja margen de sobra para
la vida del proyecto.

Después, el archivo que lee Gradle, con **cuatro claves** y sin comillas:

```properties
almacen=/home/<usuario>/almacen_menu08.jks
clave_almacen=...
alias=menu08
clave_alias=...
```

> Las contraseñas no se escriben en este documento ni en ningún otro del repositorio.

## Cómo lo lee Gradle

`aplicacion/build.gradle.kts` arma el `signingConfig` leyendo ese archivo, y **se detiene si no
está**:

```
$ ./gradlew aplicacion:assembleRelease

* What went wrong:
Falta /.../movil/clave_firma.properties: sin el no se puede firmar el APK de entrega.
La depuracion sigue funcionando: ./gradlew aplicacion:assembleDebug
Como se genera el almacen y que claves lleva el archivo, en docs/firma-apk.md.
```

Comprobado que **no deja nada a medias**: la carpeta `release/` ni siquiera se crea. La alternativa
—producir un APK sin firmar— sería peor: no se instala en ningún teléfono y el problema se descubre
media hora después, con el archivo ya enviado a alguien.

Y comprobado también el otro camino: sin ese archivo, `./gradlew aplicacion:assembleDebug` sigue
compilando sin quejarse. Quien clone el repositorio puede trabajar en depuración desde el primer
minuto; solo puede firmar la entrega quien tenga el almacén.

## La firma, verificada

```console
$ apksigner verify --verbose --print-certs Menu08Movil-1.0.apk
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Number of signers: 1
Signer #1 certificate DN: CN=..., O=SENA ficha 3235887, L=Bogota, C=CO
Signer #1 certificate SHA-256 digest: 470de61ccd20535c9a052504c8165653c950476f455576134092c6563dca8b40
Signer #1 certificate SHA-1 digest:   31d0e0729c182f656d451b8e097d85f103db6a12
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 2048
```

Esa huella SHA-256 es la misma que reporta `keytool -list` sobre el almacén: `47:0D:E6:1C:CD:20:…`.
Es lo que identifica a esta aplicación para siempre.

**Que `v1` sea `false` no es un fallo.** El esquema v1 —la firma dentro del `META-INF` del ZIP— solo
hace falta para instalar en Android anterior a la API 24, y el módulo declara `minSdk = 24`. El
esquema v2 firma el archivo completo, detecta cualquier alteración posterior y es el que aplica
desde Nougat. Verificado también en el dispositivo: `apkSigningVersion=2`.

## Metadatos del archivo

```console
$ aapt2 dump badging Menu08Movil-1.0.apk
package: name='com.menu08.movil' versionCode='1' versionName='1.0'
minSdkVersion:'24'
targetSdkVersion:'35'
```

`versionCode` y `versionName` son los que declara el `defaultConfig` de
`aplicacion/build.gradle.kts`. **4,6 MB**, frente a los 5,9 MB de la variante de depuración.

## Instalación en un dispositivo

Probado sobre un **TECNO BG7 con Android 13**.

**Primero, el error que hay que conocer.** Instalar la entrega encima de la versión de depuración
falla, porque la firma es otra:

```console
$ adb install -r Menu08Movil-1.0.apk
adb: failed to install ...: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE:
Existing package com.menu08.movil signatures do not match newer version; ignoring!]
```

Hay que **desinstalar primero**, aceptando que se pierden los datos de la aplicación —en este caso
solo el `correo_recordado`—:

```console
$ adb uninstall com.menu08.movil
Success
$ adb install Menu08Movil-1.0.apk
Success
```

La firma instalada cambia de la de depuración a la propia, comprobado con
`adb shell dumpsys package com.menu08.movil`.

**Comprobación funcional sobre el APK firmado**, no sobre el de depuración:

| Paso | Resultado |
|---|---|
| Abre la aplicación | Pantalla de ingreso, campos vacíos: instalación limpia, sin correo recordado |
| Ingreso con `foodtruck@menu08.local` contra `https://adso.menu08.com` | «Punto del truck · Sesión abierta como Administrador del food truck» |

**En la variante de entrega no se escribe ninguna traza.** La única llamada a `Log` del módulo está
dentro de `if (BuildConfig.DEBUG)`, así que el registro del dispositivo queda mudo mientras corre el
APK firmado. Con el de depuración sí aparece `I/Menu08Red: POST /movil/ingresar -> 200`. Es el
comportamiento que fija el issue de endurecimiento.

## Por qué no se minifica

`isMinifyEnabled = false` en la variante de entrega, a propósito. R8 renombraría las clases y las
trazas de un fallo en el teléfono dejarían de decir dónde ocurrió, que es justo lo que hace falta
durante la sustentación. El APK son cuatro megas y medio: no hay nada que ahorrar aquí.

## Rehacer la entrega

```bash
export JAVA_HOME=<android-studio>/jbr
./gradlew aplicacion:assembleRelease
cp aplicacion/build/outputs/apk/release/aplicacion-release.apk \
   aplicacion/build/outputs/apk/release/Menu08Movil-1.0.apk
```

Si cambia el `versionName`, cambia también el nombre del archivo que se entrega.
