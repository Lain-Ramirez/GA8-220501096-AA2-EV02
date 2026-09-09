package com.menu08.movil.red

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.CookieHandler
import java.net.CookieManager
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val CORREO = "foodtruck@menu08.local"
private const val CONTRASENA = "Menu08*Demo2026"

/**
 * Prueba instrumentada del cliente contra https://adso.menu08.com.
 *
 * Va en androidTest y no en test porque ejercita el camino completo (HttpsURLConnection, el
 * almacen de cookies que instala Aplicacion y la lectura del cuerpo con org.json) y esas tres
 * piezas solo existen de verdad en un dispositivo: en una prueba de JVM el android.jar las trae
 * con el cuerpo reducido a excepcion. Necesita red.
 *
 * Ninguna de las tres pruebas escribe nada en el servidor: el reporte solo se ejercita en el
 * caso en que el servidor lo rechaza.
 */
@RunWith(AndroidJUnit4::class)
class PruebaClienteEnServidor {

    private fun almacen() = (CookieHandler.getDefault() as CookieManager).cookieStore

    @Before
    fun partirSinSesion() {
        SesionMovil.cerrar()
        almacen().removeAll()
    }

    @Test
    fun elIngresoCorrectoAbreSesionYDejaLaCookie() = runBlocking {
        val resultado = ClienteMenu08.ingresar(CORREO, CONTRASENA)

        assertTrue("Se esperaba Exito y llego $resultado", resultado is Resultado.Exito)
        val datos = (resultado as Resultado.Exito).datos
        val usuario = datos.getJSONObject("usuario")

        assertEquals(CORREO, usuario.getString("correo"))
        assertEquals("food_truck", usuario.getString("rol"))
        assertFalse("El token no puede venir vacio", datos.getString("token_csrf").isEmpty())

        assertEquals(datos.getString("token_csrf"), SesionMovil.token)
        assertEquals("food_truck", SesionMovil.rol)
        assertEquals(CORREO, SesionMovil.correo)
        assertTrue(SesionMovil.abierta)

        val cookies = almacen().get(URI(ClienteMenu08.BASE))
        assertTrue(
            "El almacen no guardo menu08_sesion para adso.menu08.com: $cookies",
            cookies.any { it.name == "menu08_sesion" },
        )
    }

    @Test
    fun unCorreoInexistenteDevuelve401YNoAbreSesion() = runBlocking {
        val resultado = ClienteMenu08.ingresar("nadie@menu08.local", CONTRASENA)

        assertTrue("Se esperaba ErrorHttp y llego $resultado", resultado is Resultado.ErrorHttp)
        val error = resultado as Resultado.ErrorHttp

        assertEquals(401, error.codigo)
        assertEquals("credenciales_invalidas", error.error)
        assertEquals("Correo o contrase\u00f1a incorrectos.", error.mensaje)
        assertFalse(SesionMovil.abierta)
        assertNull(SesionMovil.token)
    }

    @Test
    fun laSesionRechazadaPorElServidorVaciaSesionMovil() = runBlocking {
        assertTrue(ClienteMenu08.ingresar(CORREO, CONTRASENA) is Resultado.Exito)
        assertTrue(SesionMovil.abierta)

        // Se tira la cookie sin tocar la sesion local: es lo que le pasa al token cuando vence a
        // los 120 minutos, visto desde el cliente.
        almacen().removeAll()

        val resultado = ClienteMenu08.enviarUbicacion("4.6767000", "-74.0483000")

        assertTrue("Se esperaba ErrorHttp y llego $resultado", resultado is Resultado.ErrorHttp)
        val error = resultado as Resultado.ErrorHttp
        assertEquals(401, error.codigo)
        assertEquals("no_autenticado", error.error)

        assertFalse("El cliente tiene que vaciar la sesion", SesionMovil.abierta)
        assertNull(SesionMovil.token)
    }
}
