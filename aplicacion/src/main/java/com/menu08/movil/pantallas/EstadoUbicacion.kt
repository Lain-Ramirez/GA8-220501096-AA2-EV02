package com.menu08.movil.pantallas

import android.os.Bundle

/**
 * En cual de sus cuatro situaciones esta la pantalla de ubicacion.
 *
 * Es lo que decide como se ve el boton, y se guarda entero al girar el dispositivo: sin esto, un
 * giro despues de reportar borraria la ficha y la hora del envio, que es justamente lo que quien
 * atiende el truck acaba de pedir ver.
 */
sealed class EstadoUbicacion {

    /** De partida, y tras cada reporte terminado: el boton invita a pulsarlo. */
    object Disponible : EstadoUbicacion()

    /** El punto se esta leyendo o enviando: boton deshabilitado e indicador a la vista. */
    object Capturando : EstadoUbicacion()

    /**
     * El punto quedo asentado. `creada` distingue los dos desenlaces del servicio: false es la
     * parada que ya estaba vigente y se acaba de corregir (200), true es la parada nueva que se
     * registro porque no habia ninguna vigente (201).
     */
    data class Correcto(
        val hora: String,
        val parada: Parada,
        val creada: Boolean,
    ) : EstadoUbicacion()

    /** No se pudo: el motivo se pinta tal cual, venga del telefono o del servidor. */
    data class Error(val motivo: String) : EstadoUbicacion()

    fun guardarEn(destino: Bundle) {
        when (this) {
            Disponible -> destino.putString(CUAL, DISPONIBLE)

            Capturando -> destino.putString(CUAL, CAPTURANDO)

            is Correcto -> {
                destino.putString(CUAL, CORRECTO)
                destino.putString(HORA, hora)
                destino.putBoolean(CREADA, creada)
                destino.putBundle(PARADA, parada.aBundle())
            }

            is Error -> {
                destino.putString(CUAL, ERROR)
                destino.putString(MOTIVO, motivo)
            }
        }
    }

    companion object {

        // Los cuatro estados se guardan por un nombre escrito a mano, no por el de su clase:
        // el nombre de una clase lo puede cambiar el minificador al armar el APK de entrega, y
        // entonces un giro dejaria de restaurar nada sin que nada mas se queje.
        private const val DISPONIBLE = "disponible"
        private const val CAPTURANDO = "capturando"
        private const val CORRECTO = "correcto"
        private const val ERROR = "error"

        private const val CUAL = "estado_cual"
        private const val HORA = "estado_hora"
        private const val CREADA = "estado_creada"
        private const val PARADA = "estado_parada"
        private const val MOTIVO = "estado_motivo"

        /**
         * Rehace el estado tras un giro. Devuelve Disponible ante cualquier cosa que no cuadre
         * —un Bundle de una version anterior, una ficha que no se pudo leer—: la pantalla arranca
         * de cero, que es un desenlace correcto, en vez de caerse.
         */
        fun leerDe(origen: Bundle): EstadoUbicacion = when (origen.getString(CUAL)) {
            CAPTURANDO -> Capturando

            CORRECTO -> {
                val parada = Parada.deBundle(origen.getBundle(PARADA))

                if (parada == null) {
                    Disponible
                } else {
                    Correcto(
                        hora = origen.getString(HORA).orEmpty(),
                        parada = parada,
                        creada = origen.getBoolean(CREADA),
                    )
                }
            }

            ERROR -> Error(origen.getString(MOTIVO).orEmpty())

            else -> Disponible
        }
    }
}
