package com.menu08.movil.pantallas

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.menu08.movil.R

/**
 * Esqueleto de la pantalla de ubicacion.
 *
 * De esta pantalla, el issue de la pantalla de ingreso deja aqui solo lo imprescindible para que
 * el ingreso tenga a donde salir y el modulo compile por si solo: la puerta de entrada con sus
 * extras y una actividad declarada en el manifiesto. La ficha de la parada vigente, el boton de
 * GPS y su maquetacion llegan en el issue #7, que sustituye el TextView provisional de abajo por
 * su setContentView(R.layout.pantalla_ubicacion).
 */
class ActividadUbicacion : AppCompatActivity() {

    companion object {

        /** Nombre de quien entro, para saludar. */
        const val EXTRA_NOMBRE = "nombre"

        /**
         * Rol de la cuenta. Viaja porque solo `food_truck` puede reportar el punto —es la misma
         * puerta estrecha que exige UbicacionControlador en el panel—, y esta pantalla tendra
         * que decir «esta cuenta no administra la agenda» en vez de dejar pulsar y fallar.
         */
        const val EXTRA_ROL = "rol"

        fun intencion(origen: Context, nombre: String?, rol: String?): Intent =
            Intent(origen, ActividadUbicacion::class.java)
                .putExtra(EXTRA_NOMBRE, nombre)
                .putExtra(EXTRA_ROL, rol)
    }

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)

        val relleno = (24 * resources.displayMetrics.density).toInt()

        setContentView(
            TextView(this).apply {
                text = getString(R.string.ubicacion_en_construccion)
                gravity = Gravity.CENTER
                setPadding(relleno, relleno, relleno, relleno)
            }
        )
    }
}
