package com.menu08.movil.red

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
 * Ninguna de las cuatro pruebas escribe nada en el servidor: el reporte solo se ejercita en los
 * casos en que el servidor lo rechaza.
 *
 * Necesitan red, y por eso las dos que hacen varios viajes se SALTAN —no fallan— cuando una
 * llamada no llega: un fallo por cobertura no dice nada del cliente y hace desconfiar de una
 * bateria sana.
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

    /**
     * Que cerrar() vacia tambien el almacen de cookies, y no solo los campos en memoria.
     *
     * La prueba de arriba no puede demostrarlo: alli las cookies se tiran a mano antes de la
     * llamada, asi que el almacen ya estaba vacio y encontrarlo vacio al final no prueba nada.
     * Aqui la cookie sigue en el telefono cuando llega el 401, de modo que si cerrar() no la
     * borrara, seguiria ahi al terminar.
     *
     * El 401 se provoca donde de verdad ocurre —en el servidor—: GET /salir destruye la sesion
     * de PHP y deja la cookie intacta en el cliente, que es exactamente lo que le pasa al token
     * cuando vence a los 120 minutos. La peticion siguiente viaja con una cookie que ya no
     * identifica ninguna sesion.
     */
    @Test
    fun cerrarVaciaElAlmacenDeCookies() = runBlocking {
        val ingreso = ClienteMenu08.ingresar(CORREO, CONTRASENA)

        // Si la red del dispositivo se estanca, esta prueba se salta en vez de fallar: lo que
        // viene a comprobar es que cerrar() vacia el almacen, no que el telefono tenga
        // cobertura. Es el mismo criterio de PruebaClienteSinRed, al reves.
        //
        // No es hipotetico: es la prueba de esta clase que mas viajes hace —ingreso, /salir y
        // reporte—, y con WiFi y datos moviles conectados a la vez, un cambio de red a mitad de
        // peticion deja el socket colgado muy por encima de sus propios tiempos de espera. Un
        // fallo asi no dice nada del codigo y hace desconfiar de una bateria que esta sana.
        assumeTrue(
            "Se salta: el ingreso no llego al servidor y volvio como $ingreso.",
            ingreso !is Resultado.ErrorRed,
        )

        assertTrue("Se esperaba Exito y llego $ingreso", ingreso is Resultado.Exito)
        assertTrue(
            "Sin cookie guardada esta prueba no comprueba nada",
            almacen().get(URI(ClienteMenu08.BASE)).any { it.name == "menu08_sesion" },
        )

        // El CookieHandler por omision adjunta la cookie a esta peticion sin que haya que
        // tocarla, asi que el servidor cierra ESTA sesion.
        // En Dispatchers.IO, igual que ClienteMenu08.peticionPost(): en este modulo NINGUNA
        // conexion sale del hilo que la pide. Hacerla aqui sobre el hilo de JUnit funcionaba,
        // pero saltarse la regla en una prueba es empezar a probar algo que no es la aplicacion.
        //
        // Con tiempos de espera, ademas: sin ellos una conexion que no responde deja la bateria
        // colgada sin limite, y una prueba automatica tiene que fallar, no esperar para siempre.
        val codigoSalir = withContext(Dispatchers.IO) {
            val salir = URL("${ClienteMenu08.BASE}/salir").openConnection() as HttpURLConnection

            try {
                salir.connectTimeout = 10_000
                salir.readTimeout = 15_000
                salir.instanceFollowRedirects = false
                salir.connect()
                salir.responseCode
            } catch (e: IOException) {
                // La red otra vez, por el mismo motivo de arriba. Se marca con un centinela para
                // saltar la prueba fuera de la corrutina: assumeTrue lanza, y lanzar aqui dentro
                // se veria como un fallo de la prueba en vez de como un salto.
                -1
            } finally {
                salir.disconnect()
            }
        }

        assumeTrue("Se salta: GET /salir no llego al servidor.", codigoSalir != -1)

        // 302 al ingreso: la sesion del servidor quedo destruida. Si respondiera otra cosa, lo
        // que sigue no probaria lo que dice probar, asi que se corta aqui con el motivo a la vista.
        assertEquals("GET /salir no cerro la sesion en el servidor", 302, codigoSalir)

        val resultado = ClienteMenu08.enviarUbicacion("4.6767000", "-74.0483000")

        assertTrue("Se esperaba ErrorHttp y llego $resultado", resultado is Resultado.ErrorHttp)
        assertEquals(401, (resultado as Resultado.ErrorHttp).codigo)
        assertEquals("no_autenticado", resultado.error)

        assertFalse(SesionMovil.abierta)
        assertTrue(
            "cerrar() dejo cookies en el almacen: ${almacen().cookies}",
            almacen().get(URI(ClienteMenu08.BASE)).isEmpty(),
        )
    }
}
