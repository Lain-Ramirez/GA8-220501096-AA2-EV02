package com.menu08.movil.pantallas

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.menu08.movil.R
import com.menu08.movil.red.RespuestaMenu08
import com.menu08.movil.red.Resultado
import com.menu08.movil.red.SesionMovil
import com.menu08.movil.ubicacion.GestorUbicacion
import com.menu08.movil.ubicacion.PuntoCapturado
import java.util.Date
import org.json.JSONObject

/**
 * Pantalla de ubicacion: que parada tiene el sistema y el boton que reporta el punto.
 *
 * La pantalla sabe pintar cuatro estados del boton, los dos desenlaces del servicio de reporte y
 * cuatro avisos. Debajo hay dos capas que no son suyas y a las que solo pide cosas: GestorUbicacion
 * se ocupa del permiso, del proveedor y de leer el punto, y LlamadaUbicacion de mandarlo. Aqui no
 * se abre ninguna conexion ni se toca el GPS: se encadenan los cuatro pasos y se pinta lo que
 * devuelve cada uno.
 *
 * Ni la captura ni el envio cuelgan de la actividad, porque entre las dos cabe medio minuto y en
 * ese rato entra un giro de pantalla de sobra: las dos viven en sus objetos y esta pantalla se
 * vuelve a apuntar a ellas en onStart().
 */
class ActividadUbicacion : AppCompatActivity() {

    companion object {

        /** Nombre de quien entro, para saludar. */
        const val EXTRA_NOMBRE = "nombre"

        /**
         * Rol de la cuenta. Viaja porque solo `food_truck` puede reportar el punto —es la misma
         * puerta estrecha que exige UbicacionControlador en el panel—, y #8 tendra que decir
         * «esta cuenta no administra la agenda» cuando el servicio responda 403.
         */
        const val EXTRA_ROL = "rol"

        private const val CLAVE_AVISOS = "avisos_visibles"
        private const val CLAVE_CORREO = "correo_de_la_sesion"
        private const val CLAVE_PEDIDO = "ya_se_pidio_el_permiso"
        private const val CLAVE_DENEGADO = "denegado_para_siempre"
        private const val CLAVE_LATITUD = "punto_pendiente_latitud"
        private const val CLAVE_LONGITUD = "punto_pendiente_longitud"

        private const val CODIGO_PERMISO = 1

        fun intencion(origen: Context, nombre: String?, rol: String?): Intent =
            Intent(origen, ActividadUbicacion::class.java)
                .putExtra(EXTRA_NOMBRE, nombre)
                .putExtra(EXTRA_ROL, rol)
    }

    private lateinit var raiz: View

    /**
     * El ScrollView, que desde el #21 ya no es la raiz: la raiz es el LinearLayout que sostiene
     * la cabecera fija con el control de salida. Se guarda aparte porque explicarPermiso() tiene
     * que desplazar ESTO, no la raiz.
     */
    private lateinit var desplazable: ScrollView

    private lateinit var notaPermiso: TextView
    private lateinit var textoSinFicha: TextView
    private lateinit var fichaParada: View
    private lateinit var textoDesenlace: TextView
    private lateinit var valorNombre: TextView
    private lateinit var valorReferencia: TextView
    private lateinit var valorDia: TextView
    private lateinit var valorHorario: TextView
    private lateinit var valorLatitud: TextView
    private lateinit var valorLongitud: TextView
    private lateinit var botonUbicacion: Button
    private lateinit var progreso: ProgressBar
    private lateinit var textoEstado: TextView

    private var estado: EstadoUbicacion = EstadoUbicacion.Disponible

    private val avisosVisibles = mutableSetOf<AvisoUbicacion>()

    /**
     * El correo con el que se entro, copiado al arrancar la pantalla.
     *
     * Se copia aqui, y no se lee de SesionMovil cuando hace falta, porque para entonces ya no
     * esta: ante un 401 no_autenticado o un 403 token_invalido el cliente de #5 llama a
     * SesionMovil.cerrar(), que vacia tambien el correo. Sin esta copia, la vuelta al ingreso por
     * sesion caducada llegaria sin el extra `correo` y el formulario saldria en blanco.
     */
    private var correoDeLaSesion: String? = null

    /**
     * Si ya se pidio el permiso alguna vez desde esta pantalla.
     *
     * Hace falta para leer bien shouldShowRequestPermissionRationale(): ese metodo devuelve false
     * en dos situaciones opuestas —antes de haber pedido nada, y cuando el usuario ya denego para
     * siempre—, asi que sin esta marca las dos se confundirian y la primera pulsacion pareceria
     * un rechazo definitivo.
     */
    private var yaSePidioElPermiso = false

    /** El sistema ya no va a enseñar el dialogo: solo quedan los ajustes de la aplicacion. */
    private var denegadoParaSiempre = false

    /**
     * El punto que se capturo y no se llego a asentar por un fallo de red.
     *
     * Se guarda para que reintentar sea mandarlo otra vez y no volver a leer el GPS: la lectura
     * puede tardar veinte segundos y el punto de hace un momento sigue siendo donde esta el truck.
     */
    private var puntoPendiente: PuntoCapturado? = null

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        setContentView(R.layout.pantalla_ubicacion)

        raiz = findViewById(R.id.pantalla_ubicacion)

        // Con targetSdk 35 el sistema dibuja la ventana de borde a borde en Android 15 y
        // posteriores: sin este relleno el titulo queda debajo de la barra de estado.
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insercion ->
            val barras = insercion.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insercion
        }

        desplazable = findViewById(R.id.desplazable_ubicacion)
        notaPermiso = findViewById(R.id.nota_permiso)
        textoSinFicha = findViewById(R.id.texto_sin_ficha)
        fichaParada = findViewById(R.id.ficha_parada)
        textoDesenlace = findViewById(R.id.texto_desenlace)
        valorNombre = findViewById(R.id.valor_nombre)
        valorReferencia = findViewById(R.id.valor_referencia)
        valorDia = findViewById(R.id.valor_dia)
        valorHorario = findViewById(R.id.valor_horario)
        valorLatitud = findViewById(R.id.valor_latitud)
        valorLongitud = findViewById(R.id.valor_longitud)
        botonUbicacion = findViewById(R.id.boton_ubicacion)
        progreso = findViewById(R.id.progreso_ubicacion)
        textoEstado = findViewById(R.id.texto_estado)

        // El saludo se calla si la sesion no trajo nombre, en vez de dejar la frase a medias.
        val nombre = intent.getStringExtra(EXTRA_NOMBRE).orEmpty()
        val saludo = findViewById<TextView>(R.id.texto_saludo)
        saludo.text = getString(R.string.ubicacion_saludo, nombre)
        saludo.visibility = if (nombre.isEmpty()) View.GONE else View.VISIBLE

        botonUbicacion.setOnClickListener { alPulsarElBoton() }
        findViewById<Button>(R.id.boton_salir).setOnClickListener { salir() }

        findViewById<Button>(R.id.boton_ajustes_aplicacion).setOnClickListener {
            abrirAjustesDeLaAplicacion()
        }
        findViewById<Button>(R.id.boton_ajustes_ubicacion).setOnClickListener {
            abrir(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

        if (estadoGuardado == null) {
            correoDeLaSesion = SesionMovil.correo
            pintar(EstadoUbicacion.Disponible)
        } else {
            // Vuelta de un giro: el estado del boton, la ficha y los avisos se rehacen tal cual.
            correoDeLaSesion = estadoGuardado.getString(CLAVE_CORREO)
            yaSePidioElPermiso = estadoGuardado.getBoolean(CLAVE_PEDIDO)
            denegadoParaSiempre = estadoGuardado.getBoolean(CLAVE_DENEGADO)
            puntoPendiente = puntoDe(estadoGuardado)
            pintar(EstadoUbicacion.leerDe(estadoGuardado))
            restaurarAvisos(estadoGuardado.getStringArray(CLAVE_AVISOS))
        }
    }

    override fun onSaveInstanceState(destino: Bundle) {
        super.onSaveInstanceState(destino)

        estado.guardarEn(destino)
        destino.putString(CLAVE_CORREO, correoDeLaSesion)
        destino.putBoolean(CLAVE_PEDIDO, yaSePidioElPermiso)
        destino.putBoolean(CLAVE_DENEGADO, denegadoParaSiempre)
        destino.putString(CLAVE_LATITUD, puntoPendiente?.latitud)
        destino.putString(CLAVE_LONGITUD, puntoPendiente?.longitud)
        destino.putStringArray(CLAVE_AVISOS, avisosVisibles.map { it.name }.toTypedArray())
    }

    /**
     * Vuelve a apuntarse a la captura y al envio que siguieran vivos tras un giro.
     *
     * Los dos pueden entregar aqui mismo lo que llego mientras no habia pantalla, asi que el
     * repaso de abajo va despues: para entonces el estado ya es el definitivo.
     */
    override fun onStart() {
        super.onStart()

        if (GestorUbicacion.capturando || LlamadaUbicacion.enCurso) pintarCapturando()

        GestorUbicacion.escuchar(::alTerminarLaCaptura)
        LlamadaUbicacion.escuchar(::alResponderElServidor)

        // Si se restauro «capturando» pero no hay ni captura ni envio volando, el trabajo murio
        // con el proceso: sin esto el boton se quedaria deshabilitado para siempre.
        val volando = GestorUbicacion.capturando || LlamadaUbicacion.enCurso
        if (estado is EstadoUbicacion.Capturando && !volando) pintarDisponible()
    }

    override fun onStop() {
        // Sin esto los dos objetos retendrian una actividad ya destruida despues del giro.
        GestorUbicacion.escuchar(null)
        LlamadaUbicacion.escuchar(null)
        super.onStop()
    }

    override fun onDestroy() {
        // Girar no cuenta como irse: solo se abandonan cuando la pantalla se va de verdad.
        if (isFinishing) {
            GestorUbicacion.olvidar()
            LlamadaUbicacion.olvidar()
        }
        super.onDestroy()
    }

    private fun puntoDe(origen: Bundle): PuntoCapturado? {
        val latitud = origen.getString(CLAVE_LATITUD) ?: return null
        val longitud = origen.getString(CLAVE_LONGITUD) ?: return null

        return PuntoCapturado(latitud, longitud)
    }

    // ---------------------------------------------------------------------------------------
    // Puntos de enganche del issue #8.
    //
    // Son las funciones que #8 encadena tras pedir el permiso, comprobar el proveedor, capturar
    // el punto y enviarlo con el cliente de #5. Ninguna abre conexiones ni toca el GPS: pintan.
    // ---------------------------------------------------------------------------------------

    /** El punto se esta leyendo o enviando. */
    internal fun pintarCapturando() = pintar(EstadoUbicacion.Capturando)

    /**
     * El servicio asento el punto. `creada` es el campo del mismo nombre de la respuesta: false
     * viene con el 200 de la parada vigente corregida, true con el 201 de la parada nueva.
     *
     * La hora se sella aqui, con el reloj del telefono y en el formato de 12 o 24 horas que tenga
     * puesto: asi #8 entrega la parada y no tiene que ocuparse de relojes.
     */
    internal fun pintarParada(parada: Parada, creada: Boolean) = pintar(
        EstadoUbicacion.Correcto(
            hora = DateFormat.getTimeFormat(this).format(Date()),
            parada = parada,
            creada = creada,
        )
    )

    /** No se pudo. El motivo se pinta tal cual, venga del telefono o del cuerpo del servidor. */
    internal fun pintarError(motivo: String) = pintar(EstadoUbicacion.Error(motivo))

    /** El motivo mas frecuente de #8: el GPS no entrego ningun punto reciente. */
    internal fun pintarErrorSinPunto() = pintarError(getString(R.string.ubicacion_error_sin_punto))

    /** Vuelta al arranque: sin ficha, con el boton disponible otra vez. */
    internal fun pintarDisponible() = pintar(EstadoUbicacion.Disponible)

    /**
     * Enseña uno de los cuatro avisos reservados. Se puede llamar varias veces: cada aviso tiene
     * su sitio y no tapa a los demas.
     *
     * **No cambia el estado del boton.** Un aviso solo cuenta que ocurrio algo, asi que quien lo
     * enseña tiene que decir ademas en que estado se queda la pantalla —normalmente disponible,
     * para que se pueda reintentar—. Si no, el boton se queda como estuviera, y si estaba
     * capturando queda deshabilitado con el indicador dando vueltas para siempre.
     */
    internal fun mostrarAviso(aviso: AvisoUbicacion) {
        avisosVisibles.add(aviso)
        findViewById<View>(aviso.vista).visibility = View.VISIBLE
    }

    /** Los esconde todos. #8 la llama al empezar cada intento, antes de volver a decidir. */
    internal fun ocultarAvisos() {
        avisosVisibles.clear()
        AvisoUbicacion.entries.forEach { findViewById<View>(it.vista).visibility = View.GONE }
    }

    /**
     * Vuelve a enseñar la frase de para que se usa la ubicacion.
     *
     * La frase esta a la vista desde que abre la pantalla, antes de que se pida nada; esta funcion
     * es para cuando el sistema pide justificar la peticion —shouldShowRequestPermissionRationale()
     * en #8—, y ademas la desplaza hasta ponerla delante, porque en ese momento el usuario esta
     * mirando el boton y la frase puede haber quedado arriba.
     */
    internal fun explicarPermiso() {
        notaPermiso.visibility = View.VISIBLE
        notaPermiso.post { desplazable.scrollTo(0, notaPermiso.top) }
    }

    /**
     * La sesion ya no sirve: se vuelve al ingreso con el correo y el motivo, y esta pantalla se
     * cierra con finish().
     *
     * El finish() es lo que impide que la parada de la sesion anterior siga a la vista: al entrar
     * de nuevo la actividad se crea de cero y arranca sin ficha.
     */
    internal fun volverAIngreso(motivo: String) {
        startActivity(ActividadIngreso.intencion(this, correoDeLaSesion, motivo))
        finish()
    }

    /**
     * La salida que pide el usuario, desde el control de la cabecera (issue #21).
     *
     * Es pariente de volverAIngreso() pero no es lo mismo, y las dos diferencias importan:
     *
     *   - NO se le pasa el correo. volverAIngreso() lo manda como extra porque viene de una
     *     sesion que acaba de caducar y es el dato mas fresco que hay; aqui no hace falta, y
     *     pasandolo se tomaria un camino distinto del arranque en frio. Con el extra en nulo,
     *     ActividadIngreso cae en correoRecordado(), que es de donde sale el correo cuando se
     *     abre la aplicacion desde el lanzador: mismo recorrido, un solo comportamiento.
     *
     *   - NO se le pasa motivo. La sesion no se rompio, la cerro quien la tenia abierta: un
     *     mensaje en rojo sobre el formulario diria que paso algo malo cuando no paso nada.
     *
     * Y antes de navegar se abandonan la captura y la peticion en vuelo. onDestroy() ya lo hace
     * cuando isFinishing, pero corre DESPUES del startActivity: un punto que llegara en ese hueco
     * intentaria enviarse con una sesion que ya se cerro. Abandonarlas aqui cierra esa ventana.
     */
    private fun salir() {
        GestorUbicacion.olvidar()
        LlamadaUbicacion.olvidar()

        SesionMovil.cerrar()

        startActivity(ActividadIngreso.intencion(this, null, null))
        finish()
    }

    // ---------------------------------------------------------------------------------------
    // Lo que pinta cada estado.
    // ---------------------------------------------------------------------------------------

    private fun pintar(nuevo: EstadoUbicacion) {
        estado = nuevo

        val capturando = nuevo is EstadoUbicacion.Capturando

        botonUbicacion.isEnabled = !capturando
        progreso.visibility = if (capturando) View.VISIBLE else View.GONE
        botonUbicacion.setText(textoDelBoton(nuevo))

        // La ficha es exactamente el estado correcto: en los otros tres no hay parada que pintar,
        // y la pantalla dice que todavia no hay ninguna a la vista.
        val correcto = nuevo as? EstadoUbicacion.Correcto
        fichaParada.visibility = if (correcto == null) View.GONE else View.VISIBLE
        textoSinFicha.visibility = if (correcto == null) View.VISIBLE else View.GONE
        correcto?.let { pintarFicha(it.parada, it.creada) }

        when (nuevo) {
            is EstadoUbicacion.Correcto -> pintarEstado(
                getString(R.string.ubicacion_enviado, nuevo.hora),
                R.color.md3_on_surface_variant,
            )
            is EstadoUbicacion.Error -> pintarEstado(nuevo.motivo, R.color.md3_error)
            EstadoUbicacion.Disponible, EstadoUbicacion.Capturando ->
                textoEstado.visibility = View.GONE
        }
    }

    @StringRes
    private fun textoDelBoton(estado: EstadoUbicacion): Int = when (estado) {
        EstadoUbicacion.Disponible -> R.string.boton_ubicacion_disponible
        EstadoUbicacion.Capturando -> R.string.boton_ubicacion_capturando
        is EstadoUbicacion.Correcto, is EstadoUbicacion.Error -> R.string.boton_ubicacion_de_nuevo
    }

    private fun pintarEstado(texto: String, color: Int) {
        textoEstado.text = texto
        textoEstado.setTextColor(ContextCompat.getColor(this, color))
        textoEstado.visibility = View.VISIBLE
    }

    /**
     * La ficha, con los campos del objeto `parada` del contrato del servicio y ninguno mas.
     *
     * Las tres columnas que admiten nulo se leen con su frase, nunca en blanco y nunca como 0.0,
     * que es un punto de verdad en el golfo de Guinea.
     */
    private fun pintarFicha(parada: Parada, creada: Boolean) {
        textoDesenlace.setText(
            if (creada) R.string.ubicacion_parada_creada else R.string.ubicacion_parada_actualizada
        )

        valorNombre.text = parada.nombre
        valorReferencia.text = parada.referencia ?: getString(R.string.ubicacion_sin_referencia)
        valorDia.text = nombreDelDia(parada.diaSemana)
        valorHorario.text = horarioDe(parada)
        valorLatitud.text = parada.latitud ?: getString(R.string.ubicacion_sin_punto)
        valorLongitud.text = parada.longitud ?: getString(R.string.ubicacion_sin_punto)
    }

    /**
     * La franja horaria, escrita como la escribe la web.
     *
     * Dos diferencias con lo que se pintaba antes, y las dos vienen de `panel/ubicaciones.php`:
     * los segundos se recortan —la base devuelve TIME como `18:00:00` y en la ficha sobran— y
     * una franja que termina al dia siguiente lo dice. Sin lo segundo, la parada que asienta el
     * propio reporte del GPS salia como «10:49 a 10:49», que no se entiende: son las dos horas
     * iguales con las que `asentarPunto()` la deja vigente 24 horas.
     */
    private fun horarioDe(parada: Parada): String {
        val plantilla = if (HorarioParada.cierraAlDiaSiguiente(parada.horaInicio, parada.horaFin)) {
            R.string.ubicacion_horario_nocturno
        } else {
            R.string.ubicacion_horario
        }

        return getString(
            plantilla,
            HorarioParada.corta(parada.horaInicio),
            HorarioParada.corta(parada.horaFin),
        )
    }

    /**
     * El dia en letra, leido del array de siete por el indice que resuelve DiaSemana.
     *
     * La numeracion es la de Ubicacion::DIAS en el servidor —1 lunes … 7 domingo—, de modo que el
     * 7 cae en «Domingo». Aqui solo queda la lectura del recurso: la correspondencia entre el
     * numero y el indice vive en DiaSemana.indiceDia(), que al no necesitar un Context se puede
     * comprobar en una prueba de JVM.
     *
     * Un dia fuera de 1 a 7 devuelve indice -1, getOrNull(-1) devuelve nulo y el campo dice que
     * el dia no esta definido en vez de quedarse en blanco. La tabla tiene un CHECK que lo impide,
     * pero un JSON sin la clave llega aqui como 0.
     */
    private fun nombreDelDia(dia: Int): String =
        resources.getStringArray(R.array.dias_semana).getOrNull(DiaSemana.indiceDia(dia))
            ?: getString(R.string.ubicacion_dia_desconocido)

    private fun restaurarAvisos(guardados: Array<String>?) {
        guardados.orEmpty()
            .mapNotNull { nombre -> AvisoUbicacion.entries.firstOrNull { it.name == nombre } }
            .forEach { mostrarAviso(it) }
    }

    // ---------------------------------------------------------------------------------------
    // Las dos acciones de los avisos.
    // ---------------------------------------------------------------------------------------

    private fun abrirAjustesDeLaAplicacion() = abrir(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    )

    /**
     * Abre una pantalla de ajustes del sistema.
     *
     * Se comprueba el fallo porque no todos los dispositivos traen las dos pantallas: en uno que
     * no las tenga, esto deja la aplicacion como esta en vez de cerrarla.
     */
    private fun abrir(intencion: Intent) {
        try {
            startActivity(intencion)
        } catch (e: ActivityNotFoundException) {
            pintarError(getString(R.string.ubicacion_ajustes_no_disponibles))
        }
    }

    // ---------------------------------------------------------------------------------------
    // El reporte del punto: permiso, proveedor, captura y envio.
    //
    // Este es todo el encadenamiento, y cada eslabon termina siempre en un estado de la pantalla:
    // ninguna rama deja el boton deshabilitado ni cierra la aplicacion.
    // ---------------------------------------------------------------------------------------

    private fun alPulsarElBoton() {
        ocultarAvisos()

        // Un fallo de red dejo el punto guardado: reintentar es volver a mandarlo, sin molestar
        // otra vez al GPS ni al usuario con el permiso.
        val guardado = puntoPendiente
        if (guardado != null) {
            enviar(guardado)
            return
        }

        if (GestorUbicacion.hayPermiso(this)) {
            reportarConElPermisoConcedido()
        } else {
            pedirElPermiso()
        }
    }

    /**
     * Pide los dos permisos de ubicacion en una sola llamada.
     *
     * Van juntos porque desde Android 12 el sistema descarta la solicitud del permiso fino cuando
     * viaja sola. Y si ya se denego para siempre no se llama al dialogo, que no apareceria: se
     * ofrecen los ajustes de la aplicacion, que es el unico camino que le queda al usuario.
     */
    private fun pedirElPermiso() {
        if (denegadoParaSiempre) {
            mostrarAviso(AvisoUbicacion.PERMISO_DENEGADO_SIEMPRE)
            pintarDisponible()
            return
        }

        // El sistema pide justificar la peticion: se vuelve a poner delante la frase de para que
        // se usa la ubicacion, que es la misma que esta a la vista desde que abre la pantalla.
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            explicarPermiso()
        }

        yaSePidioElPermiso = true
        requestPermissions(GestorUbicacion.PERMISOS, CODIGO_PERMISO)
    }

    override fun onRequestPermissionsResult(
        codigo: Int,
        permisos: Array<out String>,
        concesiones: IntArray,
    ) {
        super.onRequestPermissionsResult(codigo, permisos, concesiones)

        if (codigo != CODIGO_PERMISO) return

        // Se relee del sistema en vez de contar concesiones: es la misma respuesta y no depende
        // del orden en que llegue el array.
        if (GestorUbicacion.hayPermiso(this)) {
            reportarConElPermisoConcedido()
            return
        }

        // Denegado y sin justificacion que enseñar significa que el sistema ya no va a preguntar.
        // La marca es lo que separa este caso de la primera vez, donde ese metodo tambien
        // devuelve false porque todavia no se habia pedido nada.
        denegadoParaSiempre = yaSePidioElPermiso &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

        mostrarAviso(
            if (denegadoParaSiempre) {
                AvisoUbicacion.PERMISO_DENEGADO_SIEMPRE
            } else {
                AvisoUbicacion.PERMISO_DENEGADO
            }
        )

        // Denegado: no se captura ni se envia nada, y la aplicacion sigue en pie.
        pintarDisponible()
    }

    /**
     * Con el permiso ya concedido: se comprueba el proveedor y se lee.
     *
     * Si solo se concedio el aproximado se reporta igual —un punto a unas manzanas sigue diciendo
     * en que barrio para el truck— pero se avisa, y no se vuelve a abrir el dialogo del permiso
     * fino en cada pulsacion: para hayPermiso() el aproximado ya es permiso.
     */
    @SuppressLint("MissingPermission")
    private fun reportarConElPermisoConcedido() {
        if (!GestorUbicacion.permisoFino(this)) mostrarAviso(AvisoUbicacion.PUNTO_APROXIMADO)

        // Sin un proveedor encendido no hay de donde leer: no se llama a la captura.
        if (GestorUbicacion.proveedoresEncendidos(this).isEmpty()) {
            mostrarAviso(AvisoUbicacion.PROVEEDOR_APAGADO)
            pintarDisponible()
            return
        }

        pintarCapturando()

        // El permiso esta comprobado en la linea de arriba y en hayPermiso(), pero eso el
        // analizador no lo sigue a traves del gestor: de ahi la anotacion de esta funcion.
        GestorUbicacion.capturar(this)
    }

    /** Nulo es falta de punto —sin fijacion en veinte segundos, o solo uno viejo—, no un fallo. */
    private fun alTerminarLaCaptura(punto: PuntoCapturado?) {
        if (punto == null) {
            pintarErrorSinPunto()
            return
        }

        puntoPendiente = punto
        enviar(punto)
    }

    private fun enviar(punto: PuntoCapturado) {
        pintarCapturando()
        LlamadaUbicacion.enviar(punto.latitud, punto.longitud)
    }

    /**
     * Los tres desenlaces del cliente de la capa de red, cada uno con su estado de pantalla.
     *
     * El punto guardado solo sobrevive al fallo de red, que es el unico que se arregla mandando
     * lo mismo otra vez: si el servidor contesto, ya vio el punto, y repetirlo no cambiaria su
     * respuesta.
     */
    private fun alResponderElServidor(resultado: RespuestaMenu08) {
        when (resultado) {
            is Resultado.Exito -> {
                puntoPendiente = null
                pintarLaParadaAsentada(resultado)
            }

            is Resultado.ErrorHttp -> {
                puntoPendiente = null
                pintarElErrorDelServidor(resultado)
            }

            is Resultado.ErrorRed -> pintarError(getString(R.string.ubicacion_error_envio))
        }
    }

    /**
     * La parada que quedo asentada. `creada` es el campo del cuerpo y dice cual de los dos
     * desenlaces fue: false llega con el 200 de la parada vigente corregida, true con el 201 de
     * la parada nueva que se registro porque no habia ninguna vigente.
     */
    private fun pintarLaParadaAsentada(resultado: Resultado.Exito<JSONObject>) {
        val parada = resultado.datos.optJSONObject("parada")

        if (parada == null) {
            pintarError(getString(R.string.ubicacion_error_servidor, 200))
            return
        }

        pintarParada(Parada.desdeJson(parada), resultado.datos.optBoolean("creada"))
    }

    /**
     * Cada error del servicio a su sitio.
     *
     * Se mira el nombre del error y no solo el codigo, porque el 403 cubre dos cosas distintas:
     * un token vencido, que se arregla volviendo a ingresar, y un rol sin permiso, que no se
     * arregla con eso y donde echar al usuario al formulario seria mentirle.
     */
    private fun pintarElErrorDelServidor(error: Resultado.ErrorHttp) {
        when {
            // La regla vive en ErrorHttp.exigeReingreso: antes estaba escrita aqui y otra vez en
            // ClienteMenu08, con el riesgo de que una de las dos copias cambiara sin la otra.
            error.exigeReingreso -> volverAIngreso(getString(R.string.ubicacion_sesion_caducada))

            error.codigo == 403 && error.error == "rol_no_autorizado" ->
                pintarError(getString(R.string.ubicacion_rol_no_autorizado))

            // El 422 trae el texto del validador para la coordenada que fallo: se pinta tal cual,
            // que dice mas que cualquier frase propia.
            error.codigo == 422 && error.mensaje.isNotEmpty() -> pintarError(error.mensaje)

            else -> pintarError(getString(R.string.ubicacion_error_servidor, error.codigo))
        }
    }
}
