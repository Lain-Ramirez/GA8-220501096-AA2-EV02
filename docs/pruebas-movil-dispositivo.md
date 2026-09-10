# Pruebas del módulo móvil en dispositivo

| | |
|---|---|
| **Entorno** | `https://adso.menu08.com` |
| **APK** | `Menu08Movil-1.0.apk`, variante `release` firmada en el #11. Certificado SHA-256 `470de61ccd20535c9a052504c8165653c950476f455576134092c6563dca8b40` ([`firma-apk.md`](firma-apk.md)) |
| **Dispositivo** | **TECNO BG7 con Android 13**, el mismo del #11 |
| **Fecha** | Jueves 10 de septiembre de 2026 |
| **Ronda desde fuera del APK** | De 08:28 a 08:35 hora de Bogotá (`date: Thu, 10 Sep 2026 13:28:12 GMT` a `13:35:53 GMT`) |
| **Ronda en dispositivo** | 09:06, ejecutada por Lain Ramírez sobre el teléfono |
| **Comprobaciones** | Desde fuera: **14 peticiones con 26 aserciones de la colección y 2 contrastes con `curl`, 0 fallos**. En dispositivo: recorrido completo correcto |

Las dos rondas se reparten el trabajo por una razón que conviene decir de entrada: el teléfono no
puede provocar la mitad de los códigos del contrato, porque la aplicación los corta antes de salir a
la red. Esos se comprueban con la colección de Postman del #10 y con `curl`. Lo que solo existe en el
teléfono —el permiso, el GPS, la pérdida de red— se comprueba en el teléfono.

---

## Antes de empezar: el banco tiene 11 paradas, no 4

El criterio 5 da por hecho que el Truck de Pruebas tiene las cuatro paradas que siembra
`datos_pruebas.sql`. **En producción tiene once**: las cuatro sembradas y siete que dejaron pruebas
anteriores. Leído a las 08:28 y releído a las 08:35 y a las 09:13, sin cambios entre lecturas:

| id | Día | Parada | Estado | De dónde viene |
|---:|---|---|---|---|
| 2 | Miércoles | Parque de Pruebas · 11:00 a 15:00 | **activa** | `datos_pruebas.sql` |
| 3 | Viernes | Plaza de Pruebas · 12:00 a 20:00 | **activa** | `datos_pruebas.sql` |
| 4 | Sábado | Zona Rosa de Pruebas · 18:00 a 01:00 | **activa** | `datos_pruebas.sql` |
| 5 | Lunes | Parada desactivada · 09:00 a 13:00 | inactiva | `datos_pruebas.sql` |
| 6 | Domingo | Parada nocturna del domingo · 20:00 a 02:00 | inactiva | [`pruebas-agenda-paradas.md`](https://github.com/Lain-Ramirez/Menu08/blob/production/docs/pruebas-agenda-paradas.md) |
| 7 | Martes | `Parada'); DROP TABLE ubicaciones;--` | inactiva | La prueba de inyección de [`pruebas-agenda-paradas.md`](https://github.com/Lain-Ramirez/Menu08/blob/production/docs/pruebas-agenda-paradas.md): se guardó como texto |
| 18 | Martes | Punto reportado 2026-09-08 08:09 | inactiva | [`pruebas-movil-servicios.md`](https://github.com/Lain-Ramirez/Menu08/blob/production/docs/pruebas-movil-servicios.md) |
| 19 | Martes | Parque programado por el dueno | inactiva | ídem |
| 20 | Martes | Punto reportado 2026-09-08 08:12 | inactiva | ídem |
| 21 | Martes | Punto reportado 2026-09-08 08:28 | inactiva | ídem |
| 22 | Martes | Punto reportado 2026-09-08 10:34 | inactiva | **No aparece en ningún documento.** Sus coordenadas, `4.6767000, -74.0483000`, son las del cuerpo de la petición 6 de la colección: casi seguro una ejecución de Postman que no se anotó |

Se comprobó el cambio real en el listado en vez del número absoluto. Re-sembrar el banco para volver
a cuatro exigiría tocar la base de producción a mano y cambiaría los identificadores; se descartó.

## Dos paradas de prueba en la agenda real de Festín Rodante

Ocurrió **dos veces el mismo día**, y las dos por el mismo motivo: se entró con
`foodtruck@menu08.local`, que es la cuenta de demostración de **Festín Rodante**
(`food_truck_id = 1`), en lugar de `pruebas.foodtruck@menu08.local`.

| id | Creada | Coordenadas | Detectada | Desactivada |
|---:|---|---|---|---|
| 23 | Miércoles 9, 10:49 | `3.5201500, -76.2991300` (Valle del Cauca) | Jueves 08:30, leyendo la carta pública | Jueves 08:34 |
| 24 | Jueves 10, 09:06 | `4.6428732, -74.1266361` (Bogotá, occidente) | Jueves 09:13, releyendo la agenda | Jueves 09:15 |

Las dos veces el reporte cayó **fuera** de la franja de cualquier parada real, así que tomó la rama
de alta de `Ubicacion::asentarPunto()` y creó una fila con las dos horas iguales —la forma en que esa
función deja una parada vigente 24 horas—. **Ninguna coordenada real de Festín Rodante se sobrescribió**:
se comprobaron sus ocho paradas activas y todas conservan las suyas, en Bogotá.

Lo que sí ocurrió es que las dos filas quedaron **activas** y publicadas en la carta que abre un
cliente con el QR:

```
Ahora en Punto reportado 2026-09-09 10:49 · Registrado desde la aplicacion movil · hasta las 10:49
Ahora en Punto reportado 2026-09-10 09:06 · Registrado desde la aplicacion movil · hasta las 09:06
```

Es decir, la posición real del teléfono de quien probaba, en la carta pública de un negocio.

**Corrección.** Las dos se desactivaron desde `/panel/ubicaciones`, con el mismo formulario que usa
el dueño y comprobando antes que la fila objetivo era exactamente una y estaba activa, porque ese
botón alterna el estado:

```console
POST /panel/ubicaciones/estado id=23 -> 302
  fila 23 · Punto reportado 2026-09-09 10:49 · estado: inactiva
  mensaje del panel: exito · Parada desactivada.

POST /panel/ubicaciones/estado id=24 -> 302
  fila 24 · Punto reportado 2026-09-10 09:06 -> inactiva
  filas: 10 · activas: 8

GET /carta/festin-rodante (publica, sin sesion) -> 200
  vigente: (ninguna)
  ¿Punto reportado? no
```

No se borró nada: las dos quedan inactivas y el dueño puede reactivarlas.

> **El riesgo real es mayor que lo que ocurrió.** Un reporte con la cuenta de demostración **dentro**
> de la franja de una parada activa no crea nada: entra por la rama de actualización y **sobrescribe
> sus coordenadas sin dejar rastro**. Festín Rodante tiene ocho franjas así. Las dos veces se libró
> por la hora, no por el procedimiento.

---

## 1 · Ingreso

### Las tres cuentas del Truck de Pruebas

`POST /movil/ingresar` no filtra por rol: cualquiera de las tres entra, y es el reporte del punto el
que exige `food_truck`.

| Cuenta | Rol | Desde fuera del APK | En el dispositivo |
|---|---|---|---|
| `pruebas.foodtruck@menu08.local` | `food_truck` | **200** · petición 3 de la colección | ✔ Entra |
| `pruebas.cajero@menu08.local` | `cajero` | **200** · petición 7 de la colección | ✔ Entra |
| `pruebas.produccion@menu08.local` | `produccion` | — | ✔ Entra |

### Los tres motivos de rechazo

El servidor iguala a propósito los tres motivos. Distinguirlos delataría qué cuentas existen y cuáles
están activas (`MovilControlador::ingresar()`):

```php
if (!Usuario::claveCorrecta($usuario, $clave) || (int) $usuario['activo'] !== 1) {
```

| Motivo | En el dispositivo | Desde fuera del APK |
|---|---|---|
| Correo inexistente | ✔ `Correo o contraseña incorrectos.` | **401** con `curl` |
| Contraseña equivocada | ✔ `Correo o contraseña incorrectos.` | **401** con `curl` · **401** en la colección, por el navegador |
| Cuenta desactivada | **No reproducible** | **No reproducible** |

```console
POST /movil/ingresar (inexistente)      -> 401  application/json; charset=utf-8
    cuerpo: {"error":"credenciales_invalidas","mensaje":"Correo o contraseña incorrectos."}
POST /movil/ingresar (clave-equivocada) -> 401  application/json; charset=utf-8
    cuerpo: {"error":"credenciales_invalidas","mensaje":"Correo o contraseña incorrectos."}

¿los dos cuerpos son idénticos byte a byte? SI
date: Thu, 10 Sep 2026 13:32:24 GMT
```

**El texto de la pantalla no lo pone el servidor.** `ActividadIngreso.mensajeDe()` traduce el
**código** —`401 -> R.string.ingreso_error_credenciales`— y enseña su propio texto, que hoy coincide
letra por letra con el `mensaje` del servidor. Si el servidor cambiara su frase, el teléfono seguiría
diciendo la suya.

**La cuenta desactivada no se puede provocar**: las tres cuentas sembradas entran con `activo = 1`, y
desactivar una exigiría tocar la tabla `usuarios` en producción. Queda cubierta por el código de
arriba —la misma rama que los otros dos motivos— y recogida en
[lo que no cubren](#lo-que-estas-pruebas-no-cubren).

> **Un hueco de la colección.** El criterio pide repetir el 401 «desde la colección de Postman del
> #10», pero la colección **no tiene ninguna petición de ingreso fallido contra `/movil/ingresar`**:
> la única que espera un 401 es `3 - Autenticacion / POST /ingresar (clave equivocada)`, que es el
> ingreso **del navegador** y responde HTML. Se ejecutó igual, porque el desenlace es el mismo, y el
> del servicio móvil se contrastó con `curl`. Para cumplir el criterio al pie de la letra habría que
> añadir dos peticiones a la carpeta 8.

```console
$ newman run Menu08.postman_collection.json --folder "3 - Autenticacion"
↳ GET /ingresar                      200  134 ms  ✓ Responde 200  ✓ Se capturo el token
↳ POST /ingresar (correcto)          302   99 ms  ✓ Responde 302
↳ POST /ingresar (clave equivocada)  401   92 ms  ✓ Responde 401
↳ POST /ingresar (sin token)         403   33 ms  ✓ Responde 403
↳ GET /salir                         302   29 ms  ✓ Responde 302
requests 5 · failed 0 · assertions 6 · failed 0
```

## 2 · Rol sin permiso: el cajero

Desde fuera del APK, con las peticiones 7 y 8 de la colección:

```console
↳ 7 - POST /movil/ingresar (cajero) -> 200        200  100 ms  ✓ Responde 200  ✓ El rol es cajero
    {"usuario":{"id":6,"nombre":"Cajero de pruebas","correo":"pruebas.cajero@menu08.local",
     "rol":"cajero","food_truck_id":4},"token_csrf":"…"}
↳ 8 - POST /movil/ubicacion con rol cajero -> 403 403   29 ms  ✓ Responde 403  ✓ Responde JSON  ✓ Es rol_no_autorizado
    {"error":"rol_no_autorizado","mensaje":"El rol \"cajero\" no tiene acceso a este servicio."}
```

El 403 sale de `exigirRolApi('food_truck')`, **antes** de tocar la tabla: el reporte no se guarda.

**En el dispositivo** el recorrido es más largo de lo que parece, y conviene dejarlo escrito: **la
aplicación no conoce el rol**. `EXTRA_ROL` viaja en el `Intent` pero nadie lo lee, así que el cajero
pasa por todo —diálogo del permiso, comprobación del proveedor y hasta 20 s de captura— y solo
después llega el 403. La pantalla dice entonces *«Esta cuenta no administra la agenda de paradas, así
que no puede reportar el punto del truck.»* y **la sesión sigue abierta**, que es lo que dicta el
código: `ErrorHttp.exigeReingreso` es `false` para `403 rol_no_autorizado` —lo fija `ResultadoTest`
desde el #14—, así que ni `ClienteMenu08` cierra la sesión ni la pantalla vuelve al ingreso.

## 3 · Lo que el dispositivo no alcanza, comprobado desde fuera del APK

La **coordenada fuera de rango** no la puede provocar el teléfono: `SaneadorCoordenadas` la descarta
antes de salir a la red, y lo fija `SaneadorCoordenadasTest` desde el #14.

El **401 sin sesión** y el **403 sin `_token`** se comprobaron con la colección. Conviene precisar
que no son inalcanzables desde el APK, solo improbables: **la aplicación no comprueba la sesión antes
de enviar**. `alPulsarElBoton()` no mira `SesionMovil`, y `enviarUbicacion()` manda
`SesionMovil.token.orEmpty()`. Si el proceso muere en segundo plano —memoria, `am kill`—, Android
recrea la pantalla desde el `Bundle` sin token ni cookie, y la siguiente pulsación sale con `_token`
vacío. Es, de hecho, la forma de provocar el reingreso sin esperar dos horas (ver
[lo que no cubren](#lo-que-estas-pruebas-no-cubren)).

```console
$ newman run movil-sin-escritura.json        # la carpeta 8 de la colección, sin la petición 6
↳ 1 - GET /salir (llegar sin sesion)                302  230 ms  1/1
↳ 2 - POST /movil/ubicacion sin sesion -> 401       401   45 ms  3/3
    {"error":"no_autenticado","mensaje":"Debe iniciar sesion para consultar este servicio."}
↳ 3 - POST /movil/ingresar (food_truck) -> 200      200  100 ms  5/5
↳ 4 - POST /movil/ubicacion sin _token -> 403       403   30 ms  3/3
    {"error":"token_invalido","mensaje":"El token de seguridad expiro o no es valido. Recargue el tablero."}
↳ 5 - POST /movil/ubicacion coordenada invalida     422   29 ms  3/3
    {"error":"coordenadas_invalidas","mensaje":"La latitud debe estar entre -90 y 90."}
↳ 7 - POST /movil/ingresar (cajero) -> 200          200  100 ms  2/2
↳ 8 - POST /movil/ubicacion con rol cajero -> 403   403   29 ms  3/3

requests 7 · failed 0 · test-scripts 7 · failed 0 · assertions 20 · failed 0
```

**Por qué no se ejecutó «tal cual».** La petición 6 es el reporte correcto, y **escribe**. El jueves
no hay parada vigente en el Truck de Pruebas, así que habría creado una fila más en el banco, y el
criterio 5 mide justamente cuántas filas hay. Se ejecutó la carpeta sin ella.

## 4 · El botón, en el dispositivo

El recorrido completo se ejecutó sobre el teléfono y **funciona**. La prueba dejó rastro en el
servidor, que es la evidencia más dura que hay de que las cuatro piezas encajan:

```
id 24 | Jueves | Punto reportado 2026-09-10 09:06 | 09:06 a 09:06 | 4.6428732, -74.1266361 | activa
```

Esa fila la escribió el teléfono a las 09:06. Para que exista tuvieron que funcionar, en orden: el
**permiso** de ubicación concedido, el **GPS** entregando una fijación —las coordenadas son un punto
real de Bogotá, no las de ninguna prueba sembrada—, el **saneo y el formateo** a siete decimales que
exige el validador, la **sesión** con su token, la **red**, y la escritura en la tabla por la rama de
alta de `asentarPunto()`. La ficha se pintó con la parada devuelta.

Los dos desenlaces del botón, según el código y lo comprobado:

| Situación | Código | Mensaje | Efecto en la tabla |
|---|---|---|---|
| Sin parada vigente | **201** | *«No había ninguna parada vigente: se registró una parada nueva con este punto.»* | Fila nueva, vigente 24 h |
| Con parada vigente | **200** | *«Se corrigió el punto de la parada que ya estaba vigente. El nombre, la referencia y el horario los puso el dueño y no se tocan.»* | Solo cambian `latitud` y `longitud` |

**La ficha enseña el horario como la web desde el #21**: `11:00 a 15:00` y no `11:00:00 a 15:00:00`, y
una fila creada por el propio reporte sale como `HH:MM a HH:MM (cierra al día siguiente)`, porque sus
dos horas son iguales.

> **Lo que quedó sin ejercitar aquí.** El reporte se hizo sobre Festín Rodante, no sobre el Truck de
> Pruebas, así que **la rama de actualización (200) no se vio en el dispositivo**: el punto cayó fuera
> de toda franja y tomó la de alta. Comprobarla exige pulsar dentro de una franja con parada vigente
> —el miércoles de 11:00 a 15:00 para el Parque de Pruebas—. Desde fuera del APK sí está comprobada:
> es la petición 6 de la colección, y la sección 6 de
> [`pruebas-movil-servicios.md`](https://github.com/Lain-Ramirez/Menu08/blob/production/docs/pruebas-movil-servicios.md).

## 5 · Permiso, proveedor y red

Las situaciones se recorrieron en el dispositivo. Los textos son los de `strings.xml`; el
comportamiento, el de `ActividadUbicacion` del #8:

| Situación | Cómo se provoca | Qué hace la aplicación |
|---|---|---|
| **Permiso concedido** | Primera pulsación → «Mientras la app está en uso» | Captura, envía y pinta la ficha, con *«Punto enviado a las \<hora\>.»* La hora sale en el formato del reloj del teléfono, de 12 o 24 h |
| **Denegado una vez** | Primera pulsación → «No permitir» | *«Sin el permiso de ubicación no hay punto que reportar. Pulsa el botón para concederlo.»* |
| **Segunda pulsación** | Volver a pulsar | La explicación —*«La ubicación se usa para fijar el punto de la parada del food truck, y sólo cuando pulsas el botón.»*— vuelve a ponerse a la vista y el sistema pregunta otra vez. Las dos cosas ocurren a la vez: `explicarPermiso()` encola el desplazamiento y `requestPermissions()` se llama a continuación, así que el diálogo sale encima |
| **Denegado sin volver a preguntar** | «No permitir» por segunda vez | *«El permiso de ubicación quedó denegado para siempre, así que el sistema ya no lo va a preguntar: hay que concederlo a mano.»* y el botón *«Abrir los ajustes de la aplicación»* |
| **Proveedor apagado** | Permiso concedido, ubicación del teléfono apagada | *«La ubicación del teléfono está apagada y no hay de dónde leer el punto.»* y el botón *«Abrir los ajustes de ubicación»*. No captura ni envía —**siempre que no quede un punto pendiente** de un envío fallido: ése se reenvía antes de mirar el proveedor— |
| **Sin red** | Modo avión, ubicación encendida | *«No se pudo enviar el punto, pero quedó guardado: pulsa el botón para reintentar el envío sin volver a leer el GPS.»* Al quitar el modo avión, la siguiente pulsación reenvía el mismo punto sin volver a leer el GPS |

**Dos precisiones sobre el modo avión.** El GPS no necesita red, así que lo normal es que capture y
falle solo el envío; pero sin la asistencia de la red la primera fijación puede pasar de los 20 s de
espera, y entonces sale *«No se obtuvo un punto reciente. Vuelve a intentarlo.»* Y si se quiere forzar
el caso del envío apoyándose en el punto de reserva, el margen no son dos minutos desde el reporte:
la antigüedad se mide **al agotarse los 20 s de espera** y el límite son 2 min desde la fijación
anterior, así que hay que pulsar antes de 1 min 40 s.

## Comandos usados en el dispositivo

```bash
adb uninstall com.menu08.movil        # el release y el debug tienen firmas distintas
adb install Menu08Movil-1.0.apk

adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release

adb shell pm clear com.menu08.movil                    # reinicia el recorrido del permiso
adb shell cmd location set-location-enabled false      # y true para volver
adb shell cmd connectivity airplane-mode enable        # y disable
```

---

## Intentos fallidos

- **Dos paradas de prueba en la agenda real de Festín Rodante**, por entrar con la cuenta de
  demostración en lugar de la del Truck de Pruebas. Ocurrió el miércoles 9 y otra vez el jueves 10,
  y se corrigió las dos veces: está en [su sección](#dos-paradas-de-prueba-en-la-agenda-real-de-festín-rodante).
  Es el fallo más importante de esta sesión y el que conviene no repetir.
- **La colección no se pudo ejecutar «tal cual»**: su petición 6 habría creado una fila en el banco
  que el criterio 5 mide. Se ejecutó sin ella.
- **La colección no tiene el ingreso fallido del servicio móvil**: el 401 de `/movil/ingresar` se
  contrastó con `curl`, y el de la colección es el del navegador.

## Lo que estas pruebas no cubren

- **La rama de actualización (200) en el dispositivo.** El reporte cayó fuera de toda franja y creó
  fila. Verla exige pulsar dentro de una franja con parada vigente del Truck de Pruebas: el miércoles
  de 11:00 a 15:00 sobre el Parque de Pruebas. Está comprobada desde fuera del APK.
- **La parada desactivada del lunes.** Que una parada con `activa = 0` no se tome por vigente solo
  queda demostrado pulsando el botón un **lunes entre las 09:00 y las 13:00**, que es su franja.
  Pulsado a otra hora, la fila nueva aparecería igual y no probaría nada.
- **La cuenta desactivada.** Las tres cuentas del Truck de Pruebas entran con `activo = 1`, y el
  teléfono no tiene forma de desactivar una. La rama que la rechaza es la misma que la de los otros
  dos motivos y devuelve el mismo cuerpo.
- **Un solo dispositivo.** Todo corre sobre un TECNO BG7 con Android 13. Queda sin ejercitar la rama
  de captura de las API 24 a 29, que lee el punto con `requestLocationUpdates()` porque
  `getCurrentLocation()` no existe hasta la API 30, y el comportamiento del permiso en versiones
  anteriores a Android 11, donde el sistema no deja de preguntar tras dos rechazos.
- **El permiso solo aproximado.** Desde Android 12 se puede conceder la ubicación aproximada y no la
  precisa. La pantalla lo avisa (*«El permiso concedido es sólo de ubicación aproximada…»*), pero no
  está entre las cuatro situaciones del criterio.
- **El token vencido de verdad.** El token vive 120 minutos desde el ingreso y no se renueva con el
  uso. Pero la **sesión** también caduca a los 120 minutos sin actividad, y eso se comprueba antes:
  dejar el teléfono dos horas quieto da un **401 `no_autenticado`**, no el 403 `token_invalido`. Para
  ver el 403 habría que mantener viva la sesión con otro reporte dentro de esas dos horas, y ese
  reporte escribe en el banco. Lo que sí se puede ver sin esperar es la vuelta al ingreso con *«La
  sesión caducó. Vuelve a ingresar.»*: `adb shell am kill com.menu08.movil` con la pantalla en
  segundo plano, volver desde Recientes y pulsar el botón.
- **La jornada que cruza la medianoche**, que solo se puede probar un domingo a las 00:30 sobre la
  Zona Rosa de Pruebas.
- **El 500.** No se puede provocar sin romper algo de verdad en producción.

## Lo que estas pruebas dejaron en el banco

**Ni una escritura en `ubicaciones` del Truck de Pruebas.** Comprobado a las 08:28, a las 08:35 y a
las 09:13: once filas, tres activas, sin un solo cambio de coordenadas ni de estado. Todas las
peticiones que llegaron al reporte se rechazaron antes de tocar la tabla, y la única que escribe se
dejó fuera de la ejecución.

En **Festín Rodante**, dos filas creadas por error y **las dos desactivadas**:

| id | Punto | Coordenadas | Estado final |
|---:|---|---|---|
| 23 | Punto reportado 2026-09-09 10:49 | `3.5201500, -76.2991300` | inactiva |
| 24 | Punto reportado 2026-09-10 09:06 | `4.6428732, -74.1266361` | inactiva |

Sus ocho paradas activas conservan sus coordenadas originales de Bogotá, y la carta pública vuelve a
no anunciar ninguna parada de prueba.

En `usuarios`, **seis ingresos correctos** actualizaron `ultimo_ingreso` —y, por el `ON UPDATE`,
`actualizado_en`— de **tres** cuentas: la 5 (`pruebas.foodtruck`, cuatro veces), la 6
(`pruebas.cajero`) y la **2** (`foodtruck@menu08.local`, la de demostración de Festín Rodante, usada
para corregir las dos paradas). No se recifró ninguna contraseña: los hashes son bcrypt de coste 10 y
el servidor corre PHP 8.3, cuyo `PASSWORD_DEFAULT` es el mismo.

En la **bitácora** quedaron seis líneas `AVISO`: tres de ingreso fallido —dos `Ingreso movil fallido`
de los `curl` y un `Ingreso fallido` del navegador—, dos `Token CSRF invalido` —la petición 4 sobre
`POST /movil/ubicacion` y el `POST /ingresar` sin token— y una de `AccesoDenegado`, que escribe
`ManejadorErrores` al atrapar la excepción de ese mismo `POST /ingresar`.
