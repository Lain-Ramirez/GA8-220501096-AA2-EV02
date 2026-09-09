package com.menu08.movil.red

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Una peticion al servidor viviendo fuera de la pantalla que la disparo.
 *
 * Existe por el giro del dispositivo. Al girar, Android destruye la actividad y crea otra; si la
 * peticion colgara de la actividad se cancelaria a medias y la pantalla nueva tendria que
 * repetirla, que en el ingreso serian dos POST /movil/ingresar y en el reporte dos puntos
 * asentados. Aqui la peticion no se entera del giro: sigue viva, y la actividad que aparece
 * despues se vuelve a apuntar a su resultado con escuchar().
 *
 * Es la misma figura que SesionMovil —estado en memoria, nada en disco— en vez de un ViewModel,
 * que obligaria a anadir al catalogo de versiones una biblioteca que hoy no esta.
 *
 * El alcance se construye sobre Dispatchers.Main, asi que el resultado se entrega en el hilo de
 * la interfaz y la pantalla puede pintar sin saltar de hilo. Quien abre la conexion y elige el
 * hilo de fondo sigue siendo ClienteMenu08, con su withContext(Dispatchers.IO).
 */
class LlamadaEnVuelo {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var trabajo: Job? = null

    /** El resultado que llego mientras no habia ninguna pantalla escuchando (un giro). */
    private var pendiente: Resultado? = null

    private var oyente: ((Resultado) -> Unit)? = null

    /** Con esto la pantalla se pinta cargando y el boton no dispara una segunda peticion. */
    val enCurso: Boolean
        get() = trabajo?.isActive == true

    /**
     * Lanza la peticion, o no hace nada si ya hay una volando. Esa guarda es la que sostiene el
     * criterio de la segunda pulsacion: el boton deshabilitado tapa el caso normal y esto tapa el
     * resto, incluida la tecla de accion del teclado.
     */
    fun lanzar(peticion: suspend () -> Resultado) {
        if (enCurso) return

        pendiente = null
        trabajo = alcance.launch { entregar(intentar(peticion)) }
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
     * Se abandona la peticion. La llama la pantalla cuando se va para no volver, nunca al girar.
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
    private suspend fun intentar(peticion: suspend () -> Resultado): Resultado = try {
        peticion()
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
