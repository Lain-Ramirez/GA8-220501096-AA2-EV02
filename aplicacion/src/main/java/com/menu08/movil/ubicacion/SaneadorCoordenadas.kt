package com.menu08.movil.ubicacion

import java.util.Locale

/**
 * La ultima aduana antes de que un punto salga hacia el servidor.
 *
 * Dos trabajos, los dos puros: comprobar que la coordenada cae dentro del rango que admite la
 * columna, y escribirla en el unico formato que acepta Validador::coordenada() del servidor.
 *
 * Por que aqui y no solo en el servidor: un punto fuera de rango o mal formateado ya ha gastado
 * la captura del GPS y un viaje por la red para volver con un 422 que el usuario no entiende.
 * Cortarlo en el telefono da un mensaje inmediato y ahorra la peticion.
 *
 * No importa nada de android.*: es una clase de Kotlin y Java a secas, asi que se puede probar
 * en la JVM sin dispositivo ni emulador. Esa es la razon de que sea un archivo aparte y no un
 * par de metodos privados dentro de GestorUbicacion, que si depende del marco de Android.
 */
object SaneadorCoordenadas {

    /**
     * Los mismos limites que pasa UbicacionControlador a Validador::coordenada(), y los que
     * caben en ubicaciones.latitud y ubicaciones.longitud, que son DECIMAL(10,7).
     *
     * Los extremos ENTRAN: el servidor rechaza con `< minimo` o `> maximo`, no con `<=`.
     */
    const val LATITUD_MINIMA = -90.0
    const val LATITUD_MAXIMA = 90.0
    const val LONGITUD_MINIMA = -180.0
    const val LONGITUD_MAXIMA = 180.0

    /**
     * Siete decimales: el maximo que admite el patron del validador del servidor
     * —^-?\d{1,3}(\.\d{1,7})?$— y la precision exacta de la columna. Con mas, el servidor
     * responderia 422; con menos se perderia precision sin ganar nada.
     */
    private const val FORMATO = "%.7f"

    fun latitudValida(grados: Double): Boolean = enRango(grados, LATITUD_MINIMA, LATITUD_MAXIMA)

    fun longitudValida(grados: Double): Boolean = enRango(grados, LONGITUD_MINIMA, LONGITUD_MAXIMA)

    /**
     * La coordenada como cadena, lista para el cuerpo del POST.
     *
     * Locale.US no es un adorno. Con la configuracion regional en espanol, "%.7f" escribe la
     * COMA decimal: el servidor la salva con su str_replace, pero de eso no se depende. Y en
     * configuraciones con otro sistema de numeracion —arabe oriental, devanagari— %f emite
     * digitos que el patron del validador rechaza de plano, y ahi ya no habria salvacion
     * posible. Fijando la configuracion, el cuerpo enviado es el mismo en cualquier telefono.
     */
    fun formatear(grados: Double): String = String.format(Locale.US, FORMATO, grados)

    /**
     * Fuera de rango, infinito y NaN se tratan igual: no son un punto.
     *
     * isFinite() esta primero a proposito. Una comparacion contra NaN es falsa en los dos
     * sentidos, asi que sin el la condicion funcionaria por accidente; escrito asi, se lee la
     * intencion.
     */
    private fun enRango(grados: Double, minimo: Double, maximo: Double): Boolean =
        grados.isFinite() && grados >= minimo && grados <= maximo
}
