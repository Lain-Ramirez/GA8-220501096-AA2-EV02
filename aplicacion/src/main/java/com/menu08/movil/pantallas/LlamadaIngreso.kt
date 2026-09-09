package com.menu08.movil.pantallas

import com.menu08.movil.red.ClienteMenu08
import com.menu08.movil.red.Resultado
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * La peticion de ingreso en curso, viviendo fuera de la actividad que la disparo.
 *
 * Existe por un solo criterio del issue: girar el dispositivo con la peticion en curso no puede
 * provocar un segundo POST /movil/ingresar. Al girar, Android destruye la actividad y crea otra;
 * si la peticion colgara de la actividad se cancelaria a medias y la pantalla nueva tendria que
 * repetirla. Aqui la peticion no se entera del giro: sigue viva, y la actividad que aparece
 * despues se vuelve a apuntar a su resultado con escuchar().
 *
 * Es la misma figura que ya usa SesionMovil —un objeto en memoria, sin nada en disco— en vez de
 * un ViewModel, que obligaria a anadir una biblioteca al catalogo de versiones que hoy no esta.
 *
 * El alcance se construye sobre Dispatchers.Main, asi que el resultado se entrega en el hilo de
 * la interfaz y la actividad puede pintar sin saltar de hilo. Quien abre la conexion y elige el
 * hilo de fondo sigue siendo ClienteMenu08, que hace su withContext(Dispatchers.IO).
 */
object LlamadaIngreso {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var trabajo: Job? = null

    /** El resultado que llego mientras no habia ninguna pantalla escuchando (un giro). */
    private var pendiente: Resultado? = null

    private var oyente: ((Resultado) -> Unit)? = null

    /** Con esto la pantalla se pinta cargando y el boton no dispara una segunda peticion. */
    val enCurso: Boolean
        get() = trabajo?.isActive == true

    /**
     * Lanza el ingreso, o no hace nada si ya hay uno volando. Esa guarda es la que sostiene el
     * criterio de la segunda pulsacion: el boton deshabilitado tapa el caso normal y esto tapa
     * el resto, incluida la tecla de accion del teclado.
     */
    fun ingresar(correo: String, contrasena: String) {
        if (enCurso) return

        pendiente = null
        trabajo = alcance.launch {
            entregar(intentar(correo, contrasena))
        }
    }

    /**
     * La pantalla se apunta al resultado mientras esta visible y se borra al dejar de estarlo,
     * para no retener una actividad ya destruida. Si el resultado llego durante ese hueco, se
     * entrega aqui mismo.
     */
    fun escuchar(nuevo: ((Resultado) -> Unit)?) {
        oyente = nuevo

        val guardado = pendiente ?: return
        if (nuevo == null) return

        pendiente = null
        nuevo(guardado)
    }

    /**
     * Se abandona la peticion. La llama la actividad cuando se va para no volver —el ingreso
     * acerto y pasa a la ubicacion, o el usuario salio con el boton atras—, nunca al girar.
     */
    fun olvidar() {
        trabajo?.cancel()
        trabajo = null
        pendiente = null
        oyente = null
    }

    /**
     * La red de seguridad del criterio de que nada llegue al usuario como un cierre inesperado.
     *
     * ClienteMenu08 ya convierte en ErrorRed todo lo que hereda de IOException, que es por donde
     * salen la falta de red, el tiempo de espera agotado y el certificado rechazado. Lo que no
     * cubre es una excepcion de otra familia: sin este envoltorio subiria hasta el launch de
     * arriba, donde ya no hay nadie que la recoja, y el sistema cerraria la aplicacion.
     *
     * CancellationException se vuelve a lanzar a proposito: no es un fallo, es como Kotlin cuenta
     * que se abandono la peticion en olvidar(), y tragarsela dejaria la pantalla esperando un
     * resultado que nunca va a llegar.
     */
    private suspend fun intentar(correo: String, contrasena: String): Resultado = try {
        ClienteMenu08.ingresar(correo, contrasena)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resultado.ErrorRed(e)
    }

    private fun entregar(resultado: Resultado) {
        val actual = oyente

        if (actual == null) {
            pendiente = resultado
        } else {
            actual(resultado)
        }
    }
}
