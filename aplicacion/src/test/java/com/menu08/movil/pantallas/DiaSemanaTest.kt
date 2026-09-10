package com.menu08.movil.pantallas

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La numeracion del dia del contrato, traducida a indice de array.
 *
 * Se comprueba el INDICE y no el texto: leer el array de strings.xml exige un Context, y Context
 * es una de las clases que en una prueba de JVM tiene el cuerpo reducido a excepcion.
 *
 * Issue #14 · Fase 2 - Aplicacion Android
 */
class DiaSemanaTest {

    /** La numeracion de Ubicacion::DIAS del servidor: 1 lunes … 7 domingo. */
    @Test
    fun elLunesEsElPrimeroDelArray() {
        assertEquals(0, DiaSemana.indiceDia(1))
    }

    @Test
    fun elDomingoEsElSeptimo() {
        assertEquals(6, DiaSemana.indiceDia(7))
    }

    /**
     * El -1 es lo que hace que la pantalla diga «dia no definido» en vez de quedarse en blanco:
     * la tabla tiene un CHECK de 1 a 7, pero un JSON sin la clave llega aqui como 0.
     */
    @Test
    fun fueraDeRangoNoHayIndice() {
        assertEquals(-1, DiaSemana.indiceDia(0))
        assertEquals(-1, DiaSemana.indiceDia(8))
        assertEquals(-1, DiaSemana.indiceDia(-3))
    }
}
