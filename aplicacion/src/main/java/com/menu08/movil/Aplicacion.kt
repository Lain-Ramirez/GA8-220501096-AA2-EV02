package com.menu08.movil

import android.app.Application
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * Punto de arranque del proceso. Lo unico que hace es dejar puesto el almacen de cookies
 * antes de que se abra la primera conexion.
 *
 * HttpsURLConnection consulta el CookieHandler por omision en cada peticion: si hay uno
 * instalado, guarda solo lo que llegue en Set-Cookie y lo devuelve en las peticiones
 * siguientes al mismo anfitrion. De ahi sale el encadenamiento que necesita el modulo, porque
 * POST /movil/ubicacion exige la cookie menu08_sesion que dejo POST /movil/ingresar.
 *
 * El almacen se construye sin persistencia (el primer argumento es null), asi que la sesion
 * vive en memoria y muere con el proceso: nada de esto llega al disco del dispositivo.
 */
class Aplicacion : Application() {

    override fun onCreate() {
        super.onCreate()
        CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
    }
}
