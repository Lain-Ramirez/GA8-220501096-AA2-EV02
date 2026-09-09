package com.menu08.movil.pantallas

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.menu08.movil.R

/**
 * Pantalla de arranque provisional.
 *
 * Solo sirve para comprobar que el APK se instala y abre. La sustituye ActividadIngreso
 * cuando se construya la pantalla de ingreso, que pasara a ser la actividad de arranque.
 */
class ActividadPrincipal : AppCompatActivity() {

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        setContentView(R.layout.pantalla_principal)

        // Con targetSdk 35 el sistema dibuja la ventana de borde a borde en Android 15 y
        // posteriores: sin este relleno el contenido queda debajo de la barra de estado.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pantalla_principal)) { vista, insercion ->
            val barras = insercion.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insercion
        }
    }
}
