package com.menu08.movil.red

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El cuarto caso del contrato del cliente: sin red la llamada no se cuelga.
 *
 * Va en una clase aparte porque exige lo contrario que las demas, el dispositivo con la red
 * apagada, y asi se puede lanzar sola. Si al arrancar resulta que el dispositivo si tiene red,
 * la prueba se salta en vez de fallar: lo que se quiere comprobar no se puede provocar.
 *
 * Se lanza con la red apagada y:
 *   ./gradlew aplicacion:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.menu08.movil.red.PruebaClienteSinRed
 */
@RunWith(AndroidJUnit4::class)
class PruebaClienteSinRed {

    private fun servidorAlcanzable(): Boolean = try {
        InetAddress.getByName("adso.menu08.com")
        true
    } catch (e: UnknownHostException) {
        false
    }

    @Test
    fun sinRedLaLlamadaTerminaEnErrorRedYNoSeCuelga() = runBlocking {
        assumeTrue("Se salta: este dispositivo tiene red y no se puede provocar el caso.", !servidorAlcanzable())

        SesionMovil.cerrar()

        val comienzo = System.currentTimeMillis()
        val resultado = ClienteMenu08.ingresar("foodtruck@menu08.local", "Menu08*Demo2026")
        val duracion = System.currentTimeMillis() - comienzo

        assertTrue("Se esperaba ErrorRed y llego $resultado", resultado is Resultado.ErrorRed)
        assertTrue("Tardo $duracion ms, por encima de los 30000 del criterio", duracion < 30_000)
        assertFalse("Sin respuesta no puede quedar sesion abierta", SesionMovil.abierta)
    }
}
