package com.menu08.movil.pantallas

/**
 * Lo que le puede faltar al formulario de ingreso antes de que salga nada hacia la red.
 *
 * Es un valor, no un texto: la pantalla lo traduce a la cadena de strings.xml que corresponde y
 * la pega al campo que falla. Asi la regla no arrastra ninguna cadena fija ni ningun recurso de
 * Android.
 */
enum class FalloIngreso {
    CORREO_VACIO,
    CORREO_SIN_ARROBA,
    CONTRASENA_VACIA,
}

/**
 * Revisa el correo tecleado. Devuelve null cuando sirve para intentarlo.
 *
 * La comprobacion es deliberadamente corta —que no este vacio y que lleve una arroba— y no una
 * expresion regular de correo: el que dice de verdad si la cuenta existe es el servidor, y una
 * regla mas estricta aqui solo consigue rechazar correos validos que el servidor si aceptaria.
 *
 * Va aparte de la actividad, y sin importar nada de android.*, para que una prueba de JVM la
 * pueda ejercitar sin dispositivo, igual que cuerpoCodificado() en la capa de red.
 */
fun revisarCorreo(correo: String): FalloIngreso? = when {
    correo.isBlank() -> FalloIngreso.CORREO_VACIO
    !correo.contains('@') -> FalloIngreso.CORREO_SIN_ARROBA
    else -> null
}

/**
 * Revisa la contrasena tecleada. Solo mira que no este vacia: la longitud minima y las reglas de
 * composicion las pone el panel cuando se crea la cuenta, no esta pantalla.
 *
 * No se recorta con trim(): un espacio puede ser parte de la contrasena.
 */
fun revisarContrasena(contrasena: String): FalloIngreso? =
    if (contrasena.isEmpty()) FalloIngreso.CONTRASENA_VACIA else null
