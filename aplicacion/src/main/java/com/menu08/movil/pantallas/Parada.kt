package com.menu08.movil.pantallas

import android.os.Bundle
import org.json.JSONObject

/**
 * La parada que devuelve POST /movil/ubicacion, quedandose con lo que la ficha pinta.
 *
 * Son las columnas de la tabla `ubicaciones` que publica el contrato del servicio, y ni una mas:
 * `id` y `activa` viajan en la respuesta pero la ficha no los muestra, asi que no se leen. Lo que
 * no esta aqui no se puede pintar por descuido.
 *
 * Las tres columnas que admiten nulo en la tabla —`referencia`, `latitud` y `longitud`— son
 * String? y no String: un nulo tiene que llegar hasta la pantalla como nulo para que se lea «Sin
 * referencia» o «Sin punto registrado». Convertirlo antes, a cadena vacia o a 0.0, seria perder
 * aqui la unica diferencia que la pantalla necesita.
 *
 * La latitud y la longitud se guardan como texto, que es como viajan: el servicio las relee de la
 * columna DECIMAL(10,7) dentro de la transaccion, asi que la cadena es exactamente lo que quedo
 * escrito. Pasarlas por Double las redondearia sin necesidad, porque aqui solo se muestran.
 */
data class Parada(
    val nombre: String,
    val referencia: String?,
    val diaSemana: Int,
    val horaInicio: String,
    val horaFin: String,
    val latitud: String?,
    val longitud: String?,
) {

    /** El estado de la pantalla se guarda al girar, y la ficha es parte de ese estado. */
    fun aBundle(): Bundle = Bundle().apply {
        putString(NOMBRE, nombre)
        putString(REFERENCIA, referencia)
        putInt(DIA_SEMANA, diaSemana)
        putString(HORA_INICIO, horaInicio)
        putString(HORA_FIN, horaFin)
        putString(LATITUD, latitud)
        putString(LONGITUD, longitud)
    }

    companion object {

        private const val NOMBRE = "nombre"
        private const val REFERENCIA = "referencia"
        private const val DIA_SEMANA = "dia_semana"
        private const val HORA_INICIO = "hora_inicio"
        private const val HORA_FIN = "hora_fin"
        private const val LATITUD = "latitud"
        private const val LONGITUD = "longitud"

        /**
         * Lee el objeto `parada` de la respuesta del servicio.
         *
         * Las claves son las del contrato, escritas una sola vez arriba, de modo que leer el JSON
         * y volver a armar la ficha tras un giro usan los mismos nombres.
         */
        fun desdeJson(objeto: JSONObject): Parada = Parada(
            nombre = objeto.optString(NOMBRE),
            referencia = textoONulo(objeto, REFERENCIA),
            diaSemana = objeto.optInt(DIA_SEMANA),
            horaInicio = objeto.optString(HORA_INICIO),
            horaFin = objeto.optString(HORA_FIN),
            latitud = textoONulo(objeto, LATITUD),
            longitud = textoONulo(objeto, LONGITUD),
        )

        fun deBundle(origen: Bundle?): Parada? {
            if (origen == null) return null

            return Parada(
                nombre = origen.getString(NOMBRE).orEmpty(),
                referencia = origen.getString(REFERENCIA),
                diaSemana = origen.getInt(DIA_SEMANA),
                horaInicio = origen.getString(HORA_INICIO).orEmpty(),
                horaFin = origen.getString(HORA_FIN).orEmpty(),
                latitud = origen.getString(LATITUD),
                longitud = origen.getString(LONGITUD),
            )
        }

        /**
         * El nulo de la columna, ya sea porque la clave trae `null` o porque no viene.
         *
         * Hace falta mirarlo a mano: optString() devuelve cadena vacia en los dos casos, y una
         * cadena vacia pintada en la ficha es un hueco mudo, no un «Sin referencia».
         */
        private fun textoONulo(objeto: JSONObject, clave: String): String? =
            if (objeto.isNull(clave)) null else objeto.optString(clave).ifEmpty { null }
    }
}
