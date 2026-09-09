package com.menu08.movil.red

import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CODIFICACION = "UTF-8"

/**
 * Compone el cuerpo de un POST de formulario: pares nombre=valor unidos con &, cada parte
 * codificada con URLEncoder.
 *
 * Se envia asi, y no como JSON, porque el servidor lee el token unicamente de $_POST['_token']
 * en Controlador::csrfValido(), sin mirar ninguna cabecera, y $_POST solo se llena con un cuerpo
 * de este tipo. Enviandolo asi el nucleo del servidor no se toca.
 *
 * Es una funcion aparte, y pura, para que se pueda comprobar en una prueba de JVM sin abrir
 * ninguna conexion ni tocar el dispositivo.
 */
fun cuerpoCodificado(campos: List<Pair<String, String>>): String =
    campos.joinToString("&") { (nombre, valor) ->
        URLEncoder.encode(nombre, CODIFICACION) + "=" + URLEncoder.encode(valor, CODIFICACION)
    }

/**
 * La unica puerta de salida a la red del modulo movil.
 *
 * Toda conexion sale por HttpsURLConnection contra https://adso.menu08.com y se ejecuta sobre
 * Dispatchers.IO, de modo que ninguna pantalla puede provocar una NetworkOnMainThreadException
 * ni abrir conexiones por su cuenta.
 */
object ClienteMenu08 {

    const val BASE = "https://adso.menu08.com"

    const val RUTA_INGRESO = "/movil/ingresar"
    const val RUTA_UBICACION = "/movil/ubicacion"

    // Con 10 y 15 segundos, una llamada sin red termina en 25 como mucho: devuelve ErrorRed en
    // vez de dejar la pantalla colgada esperando.
    private const val ESPERA_CONEXION_MS = 10_000
    private const val ESPERA_LECTURA_MS = 15_000

    /**
     * Abre la sesion. Al acertar guarda en SesionMovil el token, el rol y el nombre que devuelve
     * el servicio, y el correo con el que se entro. La contrasena no se guarda: viaja en este
     * cuerpo y no se vuelve a usar, asi que un reintento exige teclearla otra vez.
     */
    suspend fun ingresar(correo: String, contrasena: String): Resultado {
        val resultado = peticionPost(
            RUTA_INGRESO,
            listOf("correo" to correo, "contrasena" to contrasena),
        )

        if (resultado is Resultado.Exito) {
            val usuario = resultado.datos.optJSONObject("usuario")
            SesionMovil.abrir(
                token = resultado.datos.optString("token_csrf"),
                rol = usuario?.optString("rol").orEmpty(),
                nombre = usuario?.optString("nombre").orEmpty(),
                correo = correo,
            )
        }

        return resultado
    }

    /**
     * Reporta el punto del truck. La latitud y la longitud llegan ya formateadas: aqui solo se
     * codifican. El _token sale de la sesion, nunca de quien llama.
     */
    suspend fun enviarUbicacion(latitud: String, longitud: String): Resultado =
        peticionPost(
            RUTA_UBICACION,
            listOf(
                "latitud" to latitud,
                "longitud" to longitud,
                "_token" to SesionMovil.token.orEmpty(),
            ),
        )

    /**
     * Un POST de formulario contra la ruta indicada. Las cookies las pone y las lee el
     * CookieHandler que instala Aplicacion.onCreate(), asi que aqui no se manipula ninguna.
     */
    suspend fun peticionPost(ruta: String, campos: List<Pair<String, String>>): Resultado =
        withContext(Dispatchers.IO) {
            val cuerpo = cuerpoCodificado(campos).toByteArray(Charsets.UTF_8)
            var conexion: HttpsURLConnection? = null

            try {
                conexion = (URL(BASE + ruta).openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = ESPERA_CONEXION_MS
                    readTimeout = ESPERA_LECTURA_MS
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=utf-8",
                    )
                    setRequestProperty("Accept", "application/json")
                }

                conexion.outputStream.use { it.write(cuerpo) }

                val codigo = conexion.responseCode
                // Un codigo de error no llega por inputStream: hay que leer errorStream, que es
                // donde viaja el JSON con el nombre del error.
                val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
                val texto = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                val resultado = traducirRespuesta(codigo, texto)
                cerrarSesionSiElServidorLaRechaza(resultado)
                resultado
            } catch (e: IOException) {
                Resultado.ErrorRed(e)
            } finally {
                conexion?.disconnect()
            }
        }

    /**
     * El token vive 120 minutos. Cuando caduca, o cuando se llama sin sesion, el servidor
     * responde 401 no_autenticado o 403 token_invalido, y entonces la sesion local ya no sirve
     * para nada: se vacia aqui, en un solo sitio, y quien llama recibe el error para mandar al
     * usuario de vuelta al ingreso.
     *
     * Se mira el nombre del error y no solo el codigo: un 401 credenciales_invalidas es un
     * ingreso fallido, que no tiene ninguna sesion que cerrar, y un 403 rol_no_autorizado es una
     * cuenta que entro bien pero no administra la agenda.
     */
    private fun cerrarSesionSiElServidorLaRechaza(resultado: Resultado) {
        if (resultado !is Resultado.ErrorHttp) return

        val sesionRechazada = (resultado.codigo == 401 && resultado.error == "no_autenticado") ||
            (resultado.codigo == 403 && resultado.error == "token_invalido")

        if (sesionRechazada) {
            SesionMovil.cerrar()
        }
    }
}
