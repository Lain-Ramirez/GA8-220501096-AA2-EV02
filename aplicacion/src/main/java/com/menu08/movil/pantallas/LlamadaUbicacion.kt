package com.menu08.movil.pantallas

import com.menu08.movil.red.ClienteMenu08
import com.menu08.movil.red.LlamadaEnVuelo
import com.menu08.movil.red.RespuestaMenu08

/**
 * El reporte del punto en curso, sobreviviendo al giro del dispositivo.
 *
 * El mismo mecanismo que el ingreso, y por la misma razon con mas motivo: girar el telefono
 * mientras el punto viaja no puede acabar en dos POST /movil/ubicacion.
 *
 * La latitud y la longitud llegan ya formateadas por GestorUbicacion, y el _token lo pone
 * ClienteMenu08 desde la sesion: aqui no se compone nada.
 */
object LlamadaUbicacion {

    private val llamada = LlamadaEnVuelo()

    val enCurso: Boolean
        get() = llamada.enCurso

    fun enviar(latitud: String, longitud: String) =
        llamada.lanzar { ClienteMenu08.enviarUbicacion(latitud, longitud) }

    fun escuchar(nuevo: ((RespuestaMenu08) -> Unit)?) = llamada.escuchar(nuevo)

    fun olvidar() = llamada.olvidar()
}
