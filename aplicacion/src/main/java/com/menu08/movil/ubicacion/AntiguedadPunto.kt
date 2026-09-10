package com.menu08.movil.ubicacion

/**
 * Si un punto de reserva todavia dice donde esta el truck.
 *
 * La regla es la de #8 y no cambia: mas de dos minutos y el ultimo punto que conoce el sistema
 * ya no vale como respuesta, porque en dos minutos un food truck se ha movido de esquina.
 *
 * Vive aparte de GestorUbicacion, y no como un par de lineas dentro de reserva(), por lo mismo
 * que SaneadorCoordenadas: aqui no se importa nada de android.*, asi que la regla se puede
 * comprobar en una prueba de JVM sin dispositivo ni emulador. El gestor le pasa las dos marcas
 * ya leidas —la del punto y la del reloj— y esta funcion solo resta y compara.
 *
 * Las marcas son del reloj monotono, no del de pared. Quien llama es responsable de sacarlas de
 * Location.getElapsedRealtimeNanos() y de SystemClock.elapsedRealtimeNanos(), que cuentan desde
 * que arranco el telefono y no se pueden mover: con getTime() un cambio de hora a mano haria
 * pasar por reciente un punto de ayer.
 *
 * Issue #14 · Fase 2 - Aplicacion Android
 */
object AntiguedadPunto {

    /** Dos minutos en nanosegundos. Es el mismo valor que traia GestorUbicacion. */
    const val DOS_MINUTOS_NANOS: Long = 120_000_000_000

    /**
     * El limite ENTRA: exactamente dos minutos sigue contando como reciente, igual que antes,
     * porque la comparacion es `<=` y no `<`.
     *
     * Una marca de punto POSTERIOR a la de ahora da una diferencia negativa y se toma por
     * reciente. No es un descuido: no hay ninguna forma de que un punto del futuro sea un punto
     * viejo, y descartarlo dejaria a la pantalla sin reserva por un desajuste de nanosegundos
     * entre la lectura del proveedor y la del reloj.
     */
    fun esReciente(nanosPunto: Long, nanosAhora: Long): Boolean =
        nanosAhora - nanosPunto <= DOS_MINUTOS_NANOS
}
