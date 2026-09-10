package com.menu08.movil.ubicacion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El limite de los dos minutos del punto de reserva, comprobado con aritmetica.
 *
 * Las dos marcas se las pasa el llamador, asi que aqui no se toca SystemClock ni Location: las
 * dos son de android.* y en una prueba local lanzarian excepcion en vez de dar un valor.
 *
 * Issue #14 · Fase 2 - Aplicacion Android
 */
class AntiguedadPuntoTest {

    /**
     * Los dos limites van con el literal y no con AntiguedadPunto.DOS_MINUTOS_NANOS a proposito:
     * escritos contra la propia constante seguirian verdes aunque alguien la cambiara, que es
     * justo el error que esta prueba tiene que cazar.
     */
    @Test
    fun dosMinutosClavadosSiguenSiendoRecientes() {
        assertTrue(AntiguedadPunto.esReciente(0L, 120_000_000_000L))
    }

    @Test
    fun unNanosegundoMasYaNoLoEs() {
        assertFalse(AntiguedadPunto.esReciente(0L, 120_000_000_001L))
    }

    @Test
    fun cincoMinutosTampoco() {
        assertFalse(AntiguedadPunto.esReciente(0L, 300_000_000_000L))
    }

    /**
     * Un desajuste de nanosegundos entre la lectura del proveedor y la del reloj puede dejar la
     * marca del punto por delante de la de ahora. No hay forma de que un punto del futuro sea
     * viejo, y descartarlo dejaria a la pantalla sin reserva por nada.
     */
    @Test
    fun unaMarcaPosteriorAAhoraNoSeTomaPorAntigua() {
        assertTrue(AntiguedadPunto.esReciente(1_000L, 500L))
    }

    /** La constante es la que traia GestorUbicacion: 2 * 60 * 1_000_000_000. */
    @Test
    fun laConstanteSonDosMinutosEnNanosegundos() {
        assertTrue(AntiguedadPunto.DOS_MINUTOS_NANOS == 2L * 60L * 1_000_000_000L)
    }
}
