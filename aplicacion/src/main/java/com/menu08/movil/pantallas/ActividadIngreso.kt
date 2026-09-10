package com.menu08.movil.pantallas

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputLayout
import com.menu08.movil.R
import com.menu08.movil.red.RespuestaMenu08
import com.menu08.movil.red.Resultado
import com.menu08.movil.red.SesionMovil

/**
 * Pantalla de ingreso: la primera del APK y la unica puerta hacia el resto.
 *
 * La aplicacion no guarda credenciales, asi que cada arranque empieza aqui. De la red no sabe
 * nada: se lo pide a ClienteMenu08, que ya devuelve el resultado clasificado en tres casos, y lo
 * unico que hace esta clase es traducir cada uno a un estado de la pantalla. La peticion en si
 * no cuelga de la actividad sino de LlamadaIngreso, por lo que explica ese archivo.
 */
class ActividadIngreso : AppCompatActivity() {

    companion object {

        /** El correo con el que se entro, para no hacerlo teclear otra vez. */
        const val EXTRA_CORREO = "correo"

        /** Por que se volvio al ingreso: se pinta en la zona de mensaje. */
        const val EXTRA_MOTIVO = "motivo"

        private const val CLAVE_MENSAJE = "mensaje"

        /**
         * El unico archivo que esta aplicacion escribe en el dispositivo, y la unica clave que
         * guarda: el correo del ultimo ingreso correcto, para no teclearlo cada vez.
         *
         * La contrasena NO se guarda aqui ni en ningun otro sitio. Tampoco el token ni el rol:
         * viven en SesionMovil, en memoria, y se pierden al cerrar la aplicacion, que es
         * exactamente lo que se quiere. Se comprueba sobre el APK de depuracion con
         * `adb shell run-as com.menu08.movil ls shared_prefs`.
         */
        private const val PREFERENCIAS = "menu08_movil"
        private const val CLAVE_CORREO_RECORDADO = "correo_recordado"

        /**
         * La vuelta al ingreso desde una pantalla ya autenticada. La usa quien descubra que la
         * sesion no sirve —el token vive 120 minutos y el servidor responde 401 no_autenticado
         * cuando caduca—, para que el usuario vea el motivo en vez de un formulario en blanco.
         *
         * El correo se le pasa desde SesionMovil, que es el unico sitio donde vive; la
         * contrasena no viaja aqui porque no se guarda en ninguna parte.
         */
        fun intencion(origen: Context, correo: String?, motivo: String?): Intent =
            Intent(origen, ActividadIngreso::class.java)
                .putExtra(EXTRA_CORREO, correo)
                .putExtra(EXTRA_MOTIVO, motivo)
    }

    private lateinit var campoCorreo: TextInputLayout
    private lateinit var campoContrasena: TextInputLayout
    private lateinit var entradaCorreo: EditText
    private lateinit var entradaContrasena: EditText
    private lateinit var botonIngresar: Button
    private lateinit var progreso: ProgressBar
    private lateinit var mensaje: TextView

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        setContentView(R.layout.pantalla_ingreso)

        val raiz = findViewById<View>(R.id.pantalla_ingreso)

        // Con targetSdk 35 el sistema dibuja la ventana de borde a borde en Android 15 y
        // posteriores: sin este relleno el titulo queda debajo de la barra de estado.
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insercion ->
            val barras = insercion.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insercion
        }

        campoCorreo = findViewById(R.id.campo_correo)
        campoContrasena = findViewById(R.id.campo_contrasena)
        entradaCorreo = findViewById(R.id.entrada_correo)
        entradaContrasena = findViewById(R.id.entrada_contrasena)
        botonIngresar = findViewById(R.id.boton_ingresar)
        progreso = findViewById(R.id.progreso)
        mensaje = findViewById(R.id.texto_mensaje)

        botonIngresar.setOnClickListener { intentarIngreso() }

        // La tecla de accion del teclado entra igual que el boton: es el gesto que espera quien
        // acaba de teclear la contrasena.
        entradaContrasena.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_GO) {
                intentarIngreso()
                true
            } else {
                false
            }
        }

        if (estadoGuardado == null) {
            // Primera creacion. Si se llega por sesion caducada, el correo viene hecho y el
            // motivo se explica; la contrasena se queda vacia, que es lo unico que se teclea.
            //
            // Y si se llega en frio, desde el lanzador, se recupera el correo del ultimo ingreso
            // correcto. El extra manda sobre lo guardado: viene de una sesion que acaba de
            // caducar, asi que es mas reciente.
            val correoInicial = intent.getStringExtra(EXTRA_CORREO) ?: correoRecordado()

            correoInicial?.let {
                entradaCorreo.setText(it)
                entradaContrasena.requestFocus()
            }

            pintarMensaje(intent.getStringExtra(EXTRA_MOTIVO))
        } else {
            // Vuelta de un giro. El correo lo restaura el propio campo; el mensaje no, porque un
            // TextView no guarda su texto ni su visibilidad, asi que se rehace desde el Bundle.
            pintarMensaje(estadoGuardado.getString(CLAVE_MENSAJE))
        }
    }

    override fun onStart() {
        super.onStart()

        // Si se giro con la peticion volando, sigue viva: la pantalla nueva se pinta cargando y
        // se apunta a ese mismo resultado en vez de lanzar otro POST.
        pintarCargando(LlamadaIngreso.enCurso)
        LlamadaIngreso.escuchar(::pintarResultado)
    }

    override fun onStop() {
        // Sin esto el objeto retendria una actividad ya destruida despues del giro.
        LlamadaIngreso.escuchar(null)
        super.onStop()
    }

    override fun onSaveInstanceState(estado: Bundle) {
        super.onSaveInstanceState(estado)
        estado.putString(CLAVE_MENSAJE, mensaje.text?.toString())
    }

    override fun onDestroy() {
        // Girar no cuenta como irse: solo se abandona la peticion cuando la pantalla se va de
        // verdad, sea porque el ingreso acerto o porque el usuario salio con el boton atras.
        if (isFinishing) {
            LlamadaIngreso.olvidar()
        }
        super.onDestroy()
    }

    /**
     * Un intento de ingreso: se limpia lo pintado antes, se valida en el telefono y solo si eso
     * pasa se toca la red.
     */
    private fun intentarIngreso() {
        if (LlamadaIngreso.enCurso) return

        campoCorreo.error = null
        campoContrasena.error = null
        pintarMensaje(null)

        // El correo se recorta igual que lo recorta MovilControlador::ingresar() con su trim(),
        // para que un espacio pegado al pegar el correo no cuente como un correo distinto.
        val correo = entradaCorreo.text.toString().trim()
        val contrasena = entradaContrasena.text.toString()

        val falloCorreo = revisarCorreo(correo)
        val falloContrasena = revisarContrasena(contrasena)

        campoCorreo.error = falloCorreo?.let { getString(textoDe(it)) }
        campoContrasena.error = falloContrasena?.let { getString(textoDe(it)) }

        // Aqui se corta: con un campo mal no sale ninguna peticion hacia adso.menu08.com.
        if (falloCorreo != null) {
            entradaCorreo.requestFocus()
            return
        }
        if (falloContrasena != null) {
            entradaContrasena.requestFocus()
            return
        }

        pintarCargando(true)
        LlamadaIngreso.ingresar(correo, contrasena)
    }

    /**
     * Los tres desenlaces posibles del cliente de #5, cada uno con su estado de pantalla.
     *
     * El when es exhaustivo sobre una clase sellada: si algun dia aparece un cuarto caso, esto
     * deja de compilar en vez de dejar al usuario mirando un boton que no responde.
     */
    private fun pintarResultado(resultado: RespuestaMenu08) {
        pintarCargando(false)

        when (resultado) {
            is Resultado.Exito -> irAUbicacion()
            is Resultado.ErrorHttp -> pintarMensaje(mensajeDe(resultado))
            is Resultado.ErrorRed -> pintarMensaje(getString(R.string.ingreso_error_red))
        }
    }

    /**
     * El texto de un error del servidor.
     *
     * Se elige por codigo y no se pinta el campo `mensaje` que trae la respuesta: en produccion
     * un 500 llega como {"error":"fallo_interno","codigo":500}, sin mensaje ninguno, y la
     * pantalla se quedaria muda. Ademas asi todos los textos siguen estando en strings.xml.
     *
     * El 401 dice lo mismo que el panel, palabra por palabra, sin distinguir correo inexistente
     * de contrasena equivocada ni de cuenta desactivada: los tres motivos suenan igual a
     * proposito, para no delatar que cuentas existen.
     */
    private fun mensajeDe(error: Resultado.ErrorHttp): String = when (error.codigo) {
        401 -> getString(R.string.ingreso_error_credenciales)
        422 -> getString(R.string.ingreso_error_datos_incompletos)
        else -> getString(R.string.ingreso_error_servidor, error.codigo)
    }

    @StringRes
    private fun textoDe(fallo: FalloIngreso): Int = when (fallo) {
        FalloIngreso.CORREO_VACIO -> R.string.ingreso_falta_correo
        FalloIngreso.CORREO_SIN_ARROBA -> R.string.ingreso_correo_sin_arroba
        FalloIngreso.CONTRASENA_VACIA -> R.string.ingreso_falta_contrasena
    }

    /**
     * El correo del ultimo ingreso correcto, o nulo si nunca hubo uno.
     */
    private fun correoRecordado(): String? =
        getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
            .getString(CLAVE_CORREO_RECORDADO, null)
            ?.takeIf { it.isNotBlank() }

    /**
     * Se recuerda el correo solo cuando el ingreso ACERTO.
     *
     * Guardarlo al pulsar el boton dejaria memorizado un correo equivocado, que es justo el que
     * no conviene volver a ofrecer. Se escribe una clave y ninguna mas: la contrasena que se
     * acaba de teclear no se toca.
     */
    private fun recordarCorreo(correo: String) {
        getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
            .edit()
            .putString(CLAVE_CORREO_RECORDADO, correo)
            .apply()
    }

    private fun irAUbicacion() {
        SesionMovil.correo?.let { recordarCorreo(it) }

        startActivity(ActividadUbicacion.intencion(this, SesionMovil.nombre, SesionMovil.rol))

        // Con finish() el boton atras ya no devuelve al formulario de una sesion abierta.
        finish()
    }

    /**
     * El estado de espera. El boton deshabilitado es lo que impide la segunda peticion, y los
     * campos acompanan para que no se teclee encima de lo que ya viajo.
     */
    private fun pintarCargando(cargando: Boolean) {
        botonIngresar.isEnabled = !cargando
        entradaCorreo.isEnabled = !cargando
        entradaContrasena.isEnabled = !cargando
        progreso.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    private fun pintarMensaje(texto: String?) {
        mensaje.text = texto.orEmpty()
        mensaje.visibility = if (texto.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}
