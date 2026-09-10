package com.menu08.movil.pantallas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las dos reglas del horario de la parada, las mismas que `panel/ubicaciones.php` tiene como
 * cierres locales. Corre en la JVM: HorarioParada no importa nada de android.* ni de org.json.
 *
 * Issue #21 · Fase 2 - Aplicacion Android
 */
class HorarioParadaTest {

    // ------------------------------------------------------------------ corta()

    /** La base devuelve TIME como `18:00:00`; en la ficha se enseña `18:00`. */
    @Test
    fun losSegundosSeRecortan() {
        assertEquals("18:00", HorarioParada.corta("18:00:00"))
        assertEquals("10:49", HorarioParada.corta("10:49:00"))
    }

    /** Si ya viene corta se queda igual: recortar dos veces no puede estropearla. */
    @Test
    fun unaHoraYaCortaNoCambia() {
        assertEquals("18:00", HorarioParada.corta("18:00"))
    }

    /**
     * Una clave ausente llega como cadena vacia. take() la devuelve vacia; substring habria
     * lanzado StringIndexOutOfBoundsException y tumbado la ficha entera.
     */
    @Test
    fun unaHoraVaciaNoRevienta() {
        assertEquals("", HorarioParada.corta(""))
        assertEquals("9:0", HorarioParada.corta("9:0"))
    }

    // ------------------------------------------------- cierraAlDiaSiguiente()

    /** El truck nocturno: abre a las 18:00 y cierra a las 02:00 del dia siguiente. */
    @Test
    fun laFranjaNocturnaCierraAlDiaSiguiente() {
        assertTrue(HorarioParada.cierraAlDiaSiguiente("18:00:00", "02:00:00"))
    }

    /**
     * Las dos horas iguales es el caso que motiva todo esto: asentarPunto() escribe asi la parada
     * que crea el reporte del GPS, para que quede vigente 24 horas y el reporte siguiente la
     * actualice en vez de sembrar otra.
     */
    @Test
    fun lasDosHorasIgualesTambien() {
        assertTrue(HorarioParada.cierraAlDiaSiguiente("10:49:00", "10:49:00"))
    }

    /** Una franja normal de dia no cierra al dia siguiente. */
    @Test
    fun laFranjaDeDiaNo() {
        assertFalse(HorarioParada.cierraAlDiaSiguiente("10:00:00", "18:00:00"))
        assertFalse(HorarioParada.cierraAlDiaSiguiente("00:00:00", "23:59:00"))
    }

    /** Se comparan las formas cortas: `10:49` y `10:49:00` son la misma hora. */
    @Test
    fun elLargoDeLaCadenaNoCambiaLaRespuesta() {
        assertTrue(HorarioParada.cierraAlDiaSiguiente("10:49", "10:49:00"))
        assertFalse(HorarioParada.cierraAlDiaSiguiente("10:00", "18:00:00"))
    }

    /**
     * Sin la guarda de vacio, dos cadenas vacias cumplirian `fin <= inicio` y una parada sin
     * horario se anunciaria como nocturna.
     */
    @Test
    fun sinHorarioNoSeAnunciaComoNocturna() {
        assertFalse(HorarioParada.cierraAlDiaSiguiente("", ""))
        assertFalse(HorarioParada.cierraAlDiaSiguiente("18:00:00", ""))
        assertFalse(HorarioParada.cierraAlDiaSiguiente("", "02:00:00"))
    }
}
