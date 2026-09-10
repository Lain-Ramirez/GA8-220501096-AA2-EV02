package com.menu08.movil.pantallas

import com.menu08.movil.red.ClienteMenu08
import com.menu08.movil.red.LlamadaEnVuelo
import com.menu08.movil.red.RespuestaMenu08

/**
 * El ingreso en curso, sobreviviendo al giro del dispositivo.
 *
 * Todo el mecanismo —la corrutina, el resultado que espera a que vuelva a haber pantalla, la
 * cancelacion— lo pone LlamadaEnVuelo, que es el mismo que usa el reporte del punto en
 * LlamadaUbicacion. Aqui solo queda que peticion se lanza.
 */
object LlamadaIngreso {

    private val llamada = LlamadaEnVuelo()

    val enCurso: Boolean
        get() = llamada.enCurso

    fun ingresar(correo: String, contrasena: String) =
        llamada.lanzar { ClienteMenu08.ingresar(correo, contrasena) }

    fun escuchar(nuevo: ((RespuestaMenu08) -> Unit)?) = llamada.escuchar(nuevo)

    fun olvidar() = llamada.olvidar()
}
