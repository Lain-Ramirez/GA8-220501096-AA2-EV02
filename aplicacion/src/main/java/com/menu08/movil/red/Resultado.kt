package com.menu08.movil.red

import org.json.JSONException
import org.json.JSONObject

/**
 * Lo que devuelve cualquier llamada al servidor, ya clasificado.
 *
 * Los servicios de /movil/... responden JSON siempre, tambien al fallar, asi que un error del
 * servidor llega con su nombre (`error`) y su explicacion (`mensaje`) en vez de como un texto
 * suelto. Quien llama distingue los tres casos sin mirar codigos a mano.
 */
sealed class Resultado {

    /** 200 o 201. `datos` es el cuerpo ya leido. */
    data class Exito(val datos: JSONObject) : Resultado()

    /** El servidor contesto, pero con un codigo de error. `error` y `mensaje` van tal cual. */
    data class ErrorHttp(val codigo: Int, val error: String, val mensaje: String) : Resultado()

    /** No hubo respuesta: sin red, tiempo de espera agotado, certificado rechazado. */
    data class ErrorRed(val causa: Exception) : Resultado()
}

/**
 * Traduce el par (codigo, cuerpo) a uno de los tres casos.
 *
 * No mira que ruta se llamo: cualquier codigo que anadan despues los servicios de ubicacion se
 * mapea igual sin tocar esta funcion. El 201 cuenta como exito porque es la respuesta del
 * reporte que registra una parada nueva.
 */
fun traducirRespuesta(codigo: Int, cuerpo: String): Resultado {
    val objeto = try {
        JSONObject(cuerpo)
    } catch (e: JSONException) {
        null
    }

    if (codigo == 200 || codigo == 201) {
        // Un exito sin cuerpo JSON no es utilizable: quien llama espera leer campos de el.
        return objeto?.let { Resultado.Exito(it) }
            ?: Resultado.ErrorHttp(
                codigo,
                "respuesta_ilegible",
                "El servidor respondio $codigo con un cuerpo que no es JSON.",
            )
    }

    return Resultado.ErrorHttp(
        codigo,
        objeto?.optString("error").orEmpty(),
        objeto?.optString("mensaje").orEmpty(),
    )
}
