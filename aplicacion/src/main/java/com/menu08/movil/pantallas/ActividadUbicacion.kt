package com.menu08.movil.pantallas

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
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.menu08.movil.R
import com.menu08.movil.red.SesionMovil
import java.util.Date

/**
 * Pantalla de ubicacion: que parada tiene el sistema y el boton que reporta el punto.
 *
 * Aqui no hay GPS ni red. La pantalla sabe pintar cuatro estados del boton, los dos desenlaces
 * del servicio de reporte y cuatro avisos, y nada mas; quien decide cuando pintar cada cosa es el
 * issue #8, que llama a las funciones marcadas abajo como puntos de enganche.
 *
 * Mientras #8 no llegue, el boton avanza el recorrido de RecorridoUbicacion, que enseña esos
 * estados uno a uno en el dispositivo.
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
        private const val CLAVE_PASO = "paso_del_recorrido"

        fun intencion(origen: Context, nombre: String?, rol: String?): Intent =
            Intent(origen, ActividadUbicacion::class.java)
                .putExtra(EXTRA_NOMBRE, nombre)
                .putExtra(EXTRA_ROL, rol)
    }

    private lateinit var raiz: View
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

    /** Solo del recorrido provisional. Lo borra el issue #8 junto con RecorridoUbicacion. */
    private var pasoDelRecorrido = 0

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
            pasoDelRecorrido = estadoGuardado.getInt(CLAVE_PASO)
            pintar(EstadoUbicacion.leerDe(estadoGuardado))
            restaurarAvisos(estadoGuardado.getStringArray(CLAVE_AVISOS))
        }
    }

    override fun onSaveInstanceState(destino: Bundle) {
        super.onSaveInstanceState(destino)

        estado.guardarEn(destino)
        destino.putString(CLAVE_CORREO, correoDeLaSesion)
        destino.putInt(CLAVE_PASO, pasoDelRecorrido)
        destino.putStringArray(CLAVE_AVISOS, avisosVisibles.map { it.name }.toTypedArray())
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
        notaPermiso.post { raiz.scrollTo(0, notaPermiso.top) }
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
        valorHorario.text =
            getString(R.string.ubicacion_horario, parada.horaInicio, parada.horaFin)
        valorLatitud.text = parada.latitud ?: getString(R.string.ubicacion_sin_punto)
        valorLongitud.text = parada.longitud ?: getString(R.string.ubicacion_sin_punto)
    }

    /**
     * El dia en letra, leido del array de siete por el indice `dia_semana - 1`.
     *
     * La numeracion es la de Ubicacion::DIAS en el servidor —1 lunes … 7 domingo—, de modo que el
     * 7 cae en «Domingo». La tabla tiene un CHECK que impide guardar un valor fuera de 1 a 7, pero
     * un JSON sin la clave llega aqui como 0: en ese caso el campo dice que el dia no esta
     * definido en vez de quedarse en blanco.
     */
    private fun nombreDelDia(dia: Int): String =
        resources.getStringArray(R.array.dias_semana).getOrNull(dia - 1)
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
    // Provisional: lo sustituye el issue #8 por la captura y el envio de verdad.
    // ---------------------------------------------------------------------------------------

    private fun alPulsarElBoton() {
        if (pasoDelRecorrido == 0) ocultarAvisos()

        val paso = pasoDelRecorrido % RecorridoUbicacion.pasos.size

        RecorridoUbicacion.pasos[paso](this)
        pasoDelRecorrido = (paso + 1) % RecorridoUbicacion.pasos.size
    }
}
