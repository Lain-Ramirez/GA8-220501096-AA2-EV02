package com.menu08.movil.red

import java.net.CookieHandler
import java.net.CookieManager

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

    /**
     * El NOMBRE del food truck de la cuenta, no su identificador.
     *
     * Es lo que la pantalla de ubicacion enseña en la cabecera para que quien atiende vea sobre que
     * negocio esta reportando. Nace del ingreso —el servicio lo devuelve en usuario.food_truck— y
     * es null en el rol plataforma, que no esta asociado a ningun truck.
     */
    var foodTruck: String? = null
        private set

    /** Hay sesion cuando hay token: sin el, POST /movil/ubicacion responde 403. */
    val abierta: Boolean
        get() = token != null

    fun abrir(token: String, rol: String, nombre: String, correo: String, foodTruck: String?) {
        this.token = token
        this.rol = rol
        this.nombre = nombre
        this.correo = correo
        this.foodTruck = foodTruck
    }

    /**
     * Cierra la sesion por completo: lo que hay en memoria y la cookie.
     *
     * Vaciar los campos no basta. La cookie menu08_sesion la guarda el CookieManager que instala
     * Aplicacion.onCreate(), fuera de este objeto, y sigue viajando en cada peticion aunque el
     * token ya no este: el servidor recibiria una sesion identificada y sin token, que es un
     * estado que no deberia poder darse desde este cliente.
     *
     * removeAll() sobre el almacen, y no un borrado por nombre: si el dia de manana el servidor
     * pone una segunda cookie, seguiria quedando limpia sin tocar esto.
     *
     * El as? absorbe el caso de que el CookieHandler por omision no sea un CookieManager. No
     * ocurre —lo instala Aplicacion.onCreate() antes de la primera pantalla—, pero una prueba
     * instrumentada que corriera sin pasar por ahi encontraria un nulo, y cerrar la sesion no es
     * sitio para reventar.
     */
    fun cerrar() {
        token = null
        rol = null
        nombre = null
        correo = null
        foodTruck = null

        (CookieHandler.getDefault() as? CookieManager)?.cookieStore?.removeAll()
    }
}
