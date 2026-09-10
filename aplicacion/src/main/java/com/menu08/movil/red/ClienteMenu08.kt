package com.menu08.movil.red

import android.util.Log
import com.menu08.movil.BuildConfig
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Lo que devuelven las llamadas de este cliente: el cuerpo de un exito es un objeto JSON.
 *
 * El alias vive aqui y no en Resultado.kt a proposito. Resultado quedo generico para no
 * arrastrar org.json —sin eso sus dos funciones puras no se podrian probar en la JVM—, y este
 * archivo es el unico que de verdad lee el cuerpo, asi que es el que puede fijar el tipo. Las
 * pantallas escriben RespuestaMenu08 y no Resultado<JSONObject>: dice lo mismo y se lee mejor.
 */
typealias RespuestaMenu08 = Resultado<JSONObject>

private const val CODIFICACION = "UTF-8"

/** Etiqueta unica de la capa de red, para poder filtrarla en el registro del dispositivo. */
private const val ETIQUETA = "Menu08Red"

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
    suspend fun ingresar(correo: String, contrasena: String): RespuestaMenu08 {
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
                // Null de verdad y no cadena vacia: optString devuelve "" tanto si la clave viene
                // nula —el rol plataforma— como si un servidor viejo no la manda, y la cabecera
                // tiene que poder distinguir «sin truck» de «no lo se» para no enseñar un hueco.
                foodTruck = usuario?.takeIf { !it.isNull("food_truck") }
                    ?.optString("food_truck")?.ifEmpty { null },
            )
        }

        return resultado
    }

    /**
     * Reporta el punto del truck. La latitud y la longitud llegan ya formateadas: aqui solo se
     * codifican. El _token sale de la sesion, nunca de quien llama.
     */
    suspend fun enviarUbicacion(latitud: String, longitud: String): RespuestaMenu08 =
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
    suspend fun peticionPost(ruta: String, campos: List<Pair<String, String>>): RespuestaMenu08 =
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

                registrar(ruta, codigo)

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
     * La unica traza de la capa de red: la ruta y el codigo, y solo en depuracion.
     *
     * Lo que NO sale de aqui es la lista completa: el cuerpo enviado —que en el ingreso lleva la
     * contrasena—, el cuerpo recibido —que en el ingreso lleva el token—, la cookie de sesion y
     * las coordenadas. Con la ruta y el codigo se sigue el recorrido de una prueba en dispositivo
     * sin que el registro guarde nada que no deba: el registro de Android lo puede leer cualquier
     * aplicacion con permiso de depuracion, y en un telefono prestado eso no es hipotetico.
     *
     * La guarda de BuildConfig.DEBUG deja el APK de entrega mudo. R8 ademas la resuelve como
     * constante falsa y elimina la llamada entera del bytecode de la variante release.
     *
     * Nivel INFO y no DEBUG: el dispositivo de pruebas del proyecto —un TECNO BG7— filtra el
     * nivel DEBUG en su registro, asi que una traza escrita con Log.d no se ve por mucho que se
     * busque. Como la guarda ya la deja fuera de la variante release, el nivel no cambia nada
     * de lo que se entrega; solo decide si se ve mientras se prueba, que es para lo que existe.
     */
    private fun registrar(ruta: String, codigo: Int) {
        if (BuildConfig.DEBUG) {
            Log.i(ETIQUETA, "POST $ruta -> $codigo")
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
    private fun cerrarSesionSiElServidorLaRechaza(resultado: RespuestaMenu08) {
        if (resultado is Resultado.ErrorHttp && resultado.exigeReingreso) {
            SesionMovil.cerrar()
        }
    }

    /**
     * El par (codigo, cuerpo) convertido en uno de los tres casos.
     *
     * Vive aqui, y no en Resultado.kt de donde vino, porque es el unico paso que necesita leer
     * JSON: en cuanto una funcion toca JSONObject deja de poder comprobarse en una prueba local,
     * y Resultado tiene que poder. Lo que se quedo alli son las dos reglas puras —esExito() y
     * clasificarError()— que esta funcion usa.
     *
     * El analisis del cuerpo no tiene prueba de JVM por esa misma razon; se comprueba contra el
     * servidor vivo en las pruebas de dispositivo de #12.
     */
    private fun traducirRespuesta(codigo: Int, cuerpo: String): RespuestaMenu08 {
        val objeto = try {
            JSONObject(cuerpo)
        } catch (e: JSONException) {
            null
        }

        if (esExito(codigo)) {
            // Un exito sin cuerpo JSON no es utilizable: quien llama espera leer campos de el.
            return objeto?.let { Resultado.Exito(it) }
                ?: clasificarError(
                    codigo,
                    "respuesta_ilegible",
                    "El servidor respondio $codigo con un cuerpo que no es JSON.",
                )
        }

        return clasificarError(
            codigo,
            objeto?.optString("error").orEmpty(),
            objeto?.optString("mensaje").orEmpty(),
        )
    }
}
