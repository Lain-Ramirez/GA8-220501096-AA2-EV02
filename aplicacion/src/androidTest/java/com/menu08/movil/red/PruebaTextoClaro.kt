package com.menu08.movil.red

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El modulo no puede hablar por HTTP, ni queriendo.
 *
 * La aplicacion maneja tres cosas que no pueden viajar en claro: la contrasena del usuario, la
 * cookie de sesion con su token, y la posicion del food truck. Que el codigo use https:// no
 * basta como garantia —una constante se cambia sin querer—, asi que la prohibicion esta
 * declarada en el manifiesto y en res/xml/seguridad_red.xml, y esto lo comprueba sobre el
 * dispositivo, que es donde la politica se aplica de verdad.
 *
 * Se lanza con:
 *   ./gradlew aplicacion:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.menu08.movil.red.PruebaTextoClaro
 */
@RunWith(AndroidJUnit4::class)
class PruebaTextoClaro {

    private companion object {
        const val ANFITRION = "adso.menu08.com"
    }

    /**
     * La politica que el sistema leyo del manifiesto y de seguridad_red.xml.
     *
     * Es la comprobacion mas directa del criterio: no mira el codigo del cliente, mira lo que
     * Android decidio al instalar el paquete.
     */
    @Test
    fun elSistemaProhibeElTextoClaro() {
        val politica = NetworkSecurityPolicy.getInstance()

        assertFalse(
            "La politica general permite texto claro: falta usesCleartextTraffic=false o el base-config",
            politica.isCleartextTrafficPermitted(),
        )

        assertFalse(
            "El dominio $ANFITRION permite texto claro: revisar el domain-config de seguridad_red.xml",
            politica.isCleartextTrafficPermitted(ANFITRION),
        )
    }

    /**
     * Y el intento real, que es lo que veria un atacante que lograra redirigir la aplicacion a
     * http://. La conexion no llega a salir: el sistema la corta antes.
     */
    @Test
    fun abrirElSitioPorHttpTerminaEnIOException() {
        val conexion = URL("http://$ANFITRION/movil/ingresar").openConnection() as HttpURLConnection

        try {
            conexion.connectTimeout = 10_000
            conexion.readTimeout = 10_000
            conexion.connect()

            fail("La conexion en claro se abrio y respondio ${conexion.responseCode}: no deberia haber salido")
        } catch (e: IOException) {
            // Este es el desenlace correcto. El mensaje de Android nombra el texto claro; se
            // comprueba para no dar por buena una IOException de otra causa —el servidor caido,
            // por ejemplo— que dejaria pasar una politica mal declarada.
            val motivo = e.message.orEmpty()

            assertTrue(
                "Fallo por IOException, pero no por texto claro sino por: $motivo",
                motivo.contains("Cleartext", ignoreCase = true),
            )
        } finally {
            conexion.disconnect()
        }
    }

    /**
     * La otra mitad: ninguna ruta del cliente puede intentarlo, porque su base es https.
     *
     * Si alguna vez esta constante cambiara a http://, la prueba de arriba seguiria pasando —la
     * politica del sistema no cambia— pero la aplicacion se quedaria muda, con todas las
     * llamadas cayendo en ErrorRed. Esto lo delata en la bateria en vez de en la ventanilla.
     */
    @Test
    fun laBaseDelClienteEsHttps() {
        assertTrue(
            "La base del cliente no es https: ${ClienteMenu08.BASE}",
            ClienteMenu08.BASE.startsWith("https://"),
        )
    }
}
