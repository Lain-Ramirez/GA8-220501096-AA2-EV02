package com.menu08.movil.ubicacion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

/**
 * El punto donde esta parado el truck, ya formateado para el servicio.
 *
 * Se guarda como texto y no como Double porque asi es como viaja y como se relee: el servidor lo
 * escribe en una columna DECIMAL(10,7) y `Validador::coordenada()` exige un numero de hasta siete
 * decimales, asi que el redondeo se hace una sola vez, aqui, y lo que se manda es exactamente lo
 * que se vio.
 */
data class PuntoCapturado(val latitud: String, val longitud: String)

/**
 * Todo lo que hay entre el boton y un punto: el permiso, el proveedor y la lectura.
 *
 * No abre ninguna conexion —de eso se encarga LlamadaUbicacion con el cliente de la capa de red—
 * y no pinta nada: entrega un punto, o nulo si no lo hubo, y la pantalla decide que decir.
 *
 * Guarda la captura en curso en el propio objeto, igual que LlamadaEnVuelo guarda la peticion,
 * porque una lectura del GPS puede tardar veinte segundos y en ese rato cabe un giro de pantalla
 * de sobra. Si la captura colgara de la actividad, girar la mataria a medias y dejaria el boton
 * deshabilitado esperando un punto que ya no va a llegar.
 *
 * Solo el SDK de Android: LocationManager y nada mas. Sin servicios de Google.
 */
object GestorUbicacion {

    /**
     * Los dos permisos, juntos y en este orden, para una sola llamada a requestPermissions().
     *
     * Van juntos porque desde Android 12 el sistema descarta una solicitud del permiso fino que
     * no venga acompanada del aproximado en la misma peticion, y ademas deja que el usuario
     * conceda solo el aproximado. Son los mismos dos que declara el manifiesto.
     */
    val PERMISOS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /**
     * Lo que se espera por una fijacion antes de rendirse.
     *
     * Con solo el GPS encendido y sin cobertura de red, un arranque en frio tarda decenas de
     * segundos o no llega nunca. A los veinte se corta y se le dice al usuario que reintente, que
     * es mejor que dejarle el boton bloqueado sin explicacion.
     */
    private const val ESPERA_MS = 20_000L

    /** Dos minutos. Mas viejo que esto, el punto de reserva ya no dice donde esta el truck. */
    private const val VEJEZ_MAXIMA_NANOS = 2L * 60L * 1_000_000_000L

    /**
     * Los dos unicos proveedores que se usan, en orden de preferencia.
     *
     * El GPS primero aunque tarde mas: la parada de un food truck es una esquina concreta, y el
     * proveedor de red puede errar por manzanas. El de red queda de alternativa para cuando el
     * GPS esta apagado.
     */
    private val PROVEEDORES = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    )

    private var enCurso = false

    /** El punto que llego mientras no habia pantalla escuchando (un giro). */
    private var pendiente: PuntoCapturado? = null

    private var hayPendiente = false

    private var oyente: ((PuntoCapturado?) -> Unit)? = null

    /** Con esto la pantalla se vuelve a pintar capturando despues de un giro. */
    val capturando: Boolean
        get() = enCurso

    // -------------------------------------------------------------------------------------
    // Lo que hay que comprobar antes de leer.
    // -------------------------------------------------------------------------------------

    /** Hay permiso si hay cualquiera de los dos: con el aproximado tambien se reporta. */
    fun hayPermiso(contexto: Context): Boolean =
        PERMISOS.any { concedido(contexto, it) }

    /** El fino da la esquina; el aproximado, la manzana. La pantalla lo avisa cuando falta. */
    fun permisoFino(contexto: Context): Boolean =
        concedido(contexto, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun concedido(contexto: Context, permiso: String): Boolean =
        ContextCompat.checkSelfPermission(contexto, permiso) == PackageManager.PERMISSION_GRANTED

    /**
     * Los proveedores utilizables que estan encendidos.
     *
     * Se cruzan dos listas a proposito: getAllProviders() dice cuales existe en ese dispositivo
     * —hay telefonos sin proveedor de red— y isProviderEnabled() dice cuales estan encendidos.
     * Preguntar por uno que el dispositivo no tiene devuelve false y se confundiria con apagado.
     */
    fun proveedoresEncendidos(contexto: Context): List<String> {
        val gestor = gestorDe(contexto)
        val existentes = gestor.allProviders

        return PROVEEDORES.filter { it in existentes && gestor.isProviderEnabled(it) }
    }

    // -------------------------------------------------------------------------------------
    // La lectura.
    // -------------------------------------------------------------------------------------

    /**
     * Lee un punto y se lo entrega a quien este escuchando. Entrega nulo cuando no lo hubo: sin
     * proveedor encendido, sin fijacion en veinte segundos y sin un punto de reserva reciente.
     *
     * No hace nada si ya hay una captura volando, para que dos pulsaciones no abran dos lecturas.
     */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ]
    )
    fun capturar(contexto: Context) {
        if (enCurso) return

        // El contexto de la aplicacion, no el de la actividad: la captura dura mas que la
        // pantalla que la pidio, y guardar la actividad aqui la retendria tras un giro.
        val aplicacion = contexto.applicationContext
        val proveedor = proveedoresEncendidos(aplicacion).firstOrNull()

        if (proveedor == null) {
            entregar(null)
            return
        }

        enCurso = true
        pendiente = null
        hayPendiente = false

        leer(aplicacion, proveedor)
    }

    /**
     * La pantalla se apunta al punto mientras esta visible y se borra al dejar de estarlo. Si el
     * punto llego durante ese hueco, se entrega aqui mismo.
     */
    fun escuchar(nuevo: ((PuntoCapturado?) -> Unit)?) {
        oyente = nuevo

        if (!hayPendiente || nuevo == null) return

        val guardado = pendiente
        hayPendiente = false
        pendiente = null
        nuevo(guardado)
    }

    /** Se abandona la captura. La pantalla la llama cuando se va para no volver, no al girar. */
    fun olvidar() {
        enCurso = false
        hayPendiente = false
        pendiente = null
        oyente = null
    }

    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ]
    )
    private fun leer(aplicacion: Context, proveedor: String) {
        val gestor = gestorDe(aplicacion)
        val manejador = Handler(Looper.getMainLooper())

        // Todo llega por el Looper principal, asi que esta bandera no necesita sincronizacion:
        // solo esta para que el corte de los veinte segundos y la lectura que llega justo
        // despues no terminen la captura dos veces.
        var terminada = false

        lateinit var corte: Runnable

        // Como se suelta el GPS, sea porque llego el punto o porque se agoto la espera. Lo pone
        // cada rama, porque una se cancela con la senal y la otra dandose de baja del flujo.
        var soltarElGps: () -> Unit = {}

        fun terminar(punto: Location?) {
            if (terminada) return
            terminada = true

            manejador.removeCallbacks(corte)

            // Se suelta aqui, en el unico sitio por donde se sale, y no con un temporizador
            // aparte: asi el GPS deja de gastar bateria en el instante en que ya no hace falta.
            soltarElGps()

            entregar(formatear(punto ?: reserva(gestor, proveedor)))
        }

        corte = Runnable { terminar(null) }
        manejador.postDelayed(corte, ESPERA_MS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val senal = CancellationSignal()
            soltarElGps = { senal.cancel() }

            // El consumidor puede entregar nulo cuando el proveedor se rinde: eso es falta de
            // punto, y se trata como tal, no como un fallo.
            gestor.getCurrentLocation(
                proveedor,
                senal,
                ContextCompat.getMainExecutor(aplicacion),
            ) { punto -> terminar(punto) }
        } else {
            // Por debajo de API 30 no hay lectura de una sola vez: se pide el flujo de puntos y
            // uno se da de baja en cuanto llega el primero, que es lo que hace terminar().
            val oyenteDelSistema = object : LocationListener {

                override fun onLocationChanged(punto: Location) = terminar(punto)

                // Los tres siguientes son metodos por omision desde API 30, pero abstractos por
                // debajo: sin escribirlos, en un Android 9 la clase saldria incompleta y la
                // primera lectura reventaria con AbstractMethodError.
                override fun onStatusChanged(proveedor: String?, estado: Int, extras: Bundle?) = Unit

                override fun onProviderEnabled(proveedor: String) = Unit

                override fun onProviderDisabled(proveedor: String) = Unit
            }

            soltarElGps = { gestor.removeUpdates(oyenteDelSistema) }

            gestor.requestLocationUpdates(
                proveedor,
                0L,
                0f,
                oyenteDelSistema,
                Looper.getMainLooper(),
            )
        }
    }

    /**
     * El punto de reserva: el ultimo que conoce el sistema, y solo si sigue valiendo.
     *
     * La vejez se mide con getElapsedRealtimeNanos(), que cuenta desde que arranco el telefono y
     * no se puede mover, y no con getTime(), que sigue el reloj de pared: cambiar la hora a mano
     * o un ajuste por la red harian pasar por reciente un punto de ayer, y el truck acabaria
     * reportado donde estuvo y no donde esta.
     */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ]
    )
    private fun reserva(gestor: LocationManager, proveedor: String): Location? {
        val punto = gestor.getLastKnownLocation(proveedor) ?: return null
        val edad = SystemClock.elapsedRealtimeNanos() - punto.elapsedRealtimeNanos

        return if (edad <= VEJEZ_MAXIMA_NANOS) punto else null
    }

    /**
     * El punto del proveedor, comprobado y escrito en el formato del servidor.
     *
     * El rango y el formateo viven en SaneadorCoordenadas, que no depende del marco de Android y
     * por eso se puede probar en la JVM. Aqui solo se decide que hacer con su veredicto.
     *
     * Un punto fuera de rango se descarta como si no se hubiera capturado: la pantalla dira que
     * no se obtuvo posicion, que es la verdad, en vez de gastar una peticion para que el
     * servidor conteste 422. No deberia ocurrir con un proveedor sano, pero un punto con NaN o
     * infinito llegaria hasta aqui, y de ahi al cuerpo del POST, sin este corte.
     */
    private fun formatear(punto: Location?): PuntoCapturado? {
        if (punto == null) return null

        if (!SaneadorCoordenadas.latitudValida(punto.latitude) ||
            !SaneadorCoordenadas.longitudValida(punto.longitude)
        ) {
            return null
        }

        return PuntoCapturado(
            latitud = SaneadorCoordenadas.formatear(punto.latitude),
            longitud = SaneadorCoordenadas.formatear(punto.longitude),
        )
    }

    private fun entregar(punto: PuntoCapturado?) {
        enCurso = false

        val actual = oyente

        if (actual == null) {
            pendiente = punto
            hayPendiente = true
        } else {
            actual(punto)
        }
    }

    private fun gestorDe(contexto: Context): LocationManager =
        contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
}
