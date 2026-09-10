package com.menu08.movil.red

/**
 * Lo que devuelve cualquier llamada al servidor, ya clasificado.
 *
 * Los servicios de /movil/... responden JSON siempre, tambien al fallar, asi que un error del
 * servidor llega con su nombre (`error`) y su explicacion (`mensaje`) en vez de como un texto
 * suelto. Quien llama distingue los tres casos sin mirar codigos a mano.
 *
 * ESTE ARCHIVO NO IMPORTA NADA DE android.* NI DE org.json, y eso no es una casualidad de
 * estilo: es lo que permite probarlo. En una prueba local de JVM esas clases existen en el
 * camino de clases pero con el cuerpo vaciado —el android.jar del SDK las trae reducidas a
 * `throw new RuntimeException("Stub!")`, y el jar que AGP deriva para testDebugUnitTest las
 * sustituye por `throw new RuntimeException("Method ... not mocked.")`—, de modo que llamar a
 * cualquiera de ellas no falla por el aserto: revienta antes de llegar a el. Y org.json no es
 * una biblioteca que se pueda cambiar: viaja dentro del propio marco de Android.
 *
 * De ahi el parametro de tipo. El cuerpo de un exito ES un JSONObject, pero nombrarlo aqui
 * ataria este archivo a org.json y con el se iria la posibilidad de probar las dos funciones de
 * abajo. Quien sabe leer el cuerpo es ClienteMenu08, que si depende de org.json y por eso fija
 * alli el tipo con el alias RespuestaMenu08. Los tres casos del tipo sellado son los mismos
 * de #5; lo unico que se mudo es donde se lee el JSON.
 *
 * Issue #14 · Fase 2 - Aplicacion Android
 */
sealed class Resultado<out T> {

    /** 200 o 201. `datos` es el cuerpo ya leido por quien abrio la conexion. */
    data class Exito<out T>(val datos: T) : Resultado<T>()

    /** El servidor contesto, pero con un codigo de error. `error` y `mensaje` van tal cual. */
    data class ErrorHttp(
        val codigo: Int,
        val error: String,
        val mensaje: String,
    ) : Resultado<Nothing>() {

        /**
         * La sesion local ya no sirve y hay que volver a ingresar.
         *
         * Pone nombre a una regla que hasta ahora estaba escrita dos veces —en ClienteMenu08,
         * para vaciar SesionMovil, y en ActividadUbicacion, para mandar al usuario al
         * formulario—, con el riesgo de que una de las dos copias cambiara sin la otra.
         *
         * Se mira el NOMBRE del error y no solo el codigo, porque los dos codigos cubren cosas
         * distintas: un 401 credenciales_invalidas es un ingreso fallido, que no tiene ninguna
         * sesion que cerrar, y un 403 rol_no_autorizado es una cuenta que entro bien pero no
         * administra la agenda. A esa no se la echa: se le dice que no le corresponde.
         */
        val exigeReingreso: Boolean
            get() = (codigo == 401 && error == "no_autenticado") ||
                (codigo == 403 && error == "token_invalido")
    }

    /** No hubo respuesta: sin red, tiempo de espera agotado, certificado rechazado. */
    data class ErrorRed(val causa: Exception) : Resultado<Nothing>()
}

/**
 * Si el codigo de respuesta cuenta como exito.
 *
 * El 201 entra porque es lo que devuelve el reporte del punto cuando asienta una parada nueva,
 * frente al 200 de la que ya existia; #8 los pinta con textos distintos, pero los dos son un
 * viaje que salio bien.
 *
 * No mira que ruta se llamo: cualquier codigo que anadan despues los servicios se clasifica
 * igual sin tocar esta funcion.
 */
fun esExito(codigo: Int): Boolean = codigo == 200 || codigo == 201

/**
 * Construye el error ya clasificado a partir de lo que traia el cuerpo.
 *
 * Recibe el codigo y los dos campos YA EXTRAIDOS, y no el texto de la respuesta, que es
 * justamente lo que la hace pura y comprobable: leer el JSON es trabajo de ClienteMenu08.
 *
 * El codigo y el mensaje se conservan intactos. El 422 del validador de coordenadas trae en su
 * `mensaje` la frase exacta del campo que fallo, y esa frase dice mas que cualquier texto propio
 * que se pudiera poner aqui: la pantalla la pinta tal cual.
 */
fun clasificarError(codigo: Int, error: String, mensaje: String): Resultado.ErrorHttp =
    Resultado.ErrorHttp(codigo, error, mensaje)
