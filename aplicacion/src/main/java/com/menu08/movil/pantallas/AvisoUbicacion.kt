package com.menu08.movil.pantallas

import androidx.annotation.IdRes
import com.menu08.movil.R

/**
 * Los cuatro casos que la pantalla de ubicacion tiene reservados y ocultos de partida.
 *
 * Quien los detecta es el issue #8 —el permiso, el proveedor de ubicacion y la precision del
 * punto son suyos—; aqui solo estan el aviso y su sitio en la pantalla, de modo que #8 no tenga
 * que buscar identificadores ni componer textos: llama a mostrarAviso() con uno de estos cuatro.
 */
enum class AvisoUbicacion(@IdRes val vista: Int) {

    /** El usuario dijo que no. Se puede volver a pedir. */
    PERMISO_DENEGADO(R.id.aviso_permiso_denegado),

    /** El sistema ya no va a preguntar: el aviso ofrece abrir los ajustes de la aplicacion. */
    PERMISO_DENEGADO_SIEMPRE(R.id.aviso_permiso_denegado_siempre),

    /** Sin proveedor encendido no hay de donde leer: el aviso abre los ajustes de ubicacion. */
    PROVEEDOR_APAGADO(R.id.aviso_proveedor_apagado),

    /** Se concedio solo la ubicacion aproximada: se reporta igual, pero conviene decirlo. */
    PUNTO_APROXIMADO(R.id.aviso_punto_aproximado),
}
