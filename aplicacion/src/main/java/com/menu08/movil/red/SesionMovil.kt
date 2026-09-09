package com.menu08.movil.red

/**
 * La sesion de la aplicacion, en memoria y solo en memoria.
 *
 * Guarda lo que devuelve POST /movil/ingresar (el token contra falsificacion de peticiones, el
 * rol y el nombre) y el correo con el que se entro, que es el unico sitio donde vive: las
 * pantallas lo pasan como extra cuando hay que volver al ingreso.
 *
 * La contrasena no se guarda en ningun caso. Si el servidor rechaza la sesion, el cliente llama
 * a cerrar() y hay que volver a ingresar: no se reintenta nada en silencio.
 */
object SesionMovil {

    var token: String? = null
        private set

    var rol: String? = null
        private set

    var nombre: String? = null
        private set

    var correo: String? = null
        private set

    /** Hay sesion cuando hay token: sin el, POST /movil/ubicacion responde 403. */
    val abierta: Boolean
        get() = token != null

    fun abrir(token: String, rol: String, nombre: String, correo: String) {
        this.token = token
        this.rol = rol
        this.nombre = nombre
        this.correo = correo
    }

    fun cerrar() {
        token = null
        rol = null
        nombre = null
        correo = null
    }
}
