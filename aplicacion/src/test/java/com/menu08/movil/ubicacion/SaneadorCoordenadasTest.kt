package com.menu08.movil.ubicacion

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Los limites y el formato de la coordenada, comprobados en la JVM.
 *
 * Corre sin dispositivo, sin emulador y sin red porque SaneadorCoordenadas no importa nada de
 * android.* ni de org.json: en una prueba local esas clases tienen el cuerpo reducido a
 * excepcion y cualquier llamada revienta antes de llegar al aserto.
 *
 * Issue #14 · Fase 2 - Aplicacion Android
 */
class SaneadorCoordenadasTest {

    private lateinit var regionalOriginal: Locale

    /**
     * La prueba que mas sostiene al resto.
     *
     * Se pone la configuracion regional colombiana, que es la del telefono del truck, y se
     * restaura despues para no contagiar a las demas pruebas del mismo proceso de JVM: Gradle
     * las corre todas en el mismo, asi que una regional cambiada y no devuelta se filtraria.
     */
    @Before
    fun ponerLaRegionalColombiana() {
        regionalOriginal = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-CO"))
    }

    @After
    fun devolverLaRegional() {
        Locale.setDefault(regionalOriginal)
    }

    /** Los extremos ENTRAN: el servidor rechaza con `< minimo` o `> maximo`, no con `<=`. */
    @Test
    fun losExtremosDelRangoSeAceptan() {
        assertTrue(SaneadorCoordenadas.latitudValida(-90.0))
        assertTrue(SaneadorCoordenadas.latitudValida(90.0))
        assertTrue(SaneadorCoordenadas.longitudValida(-180.0))
        assertTrue(SaneadorCoordenadas.longitudValida(180.0))
    }

    @Test
    fun pasarseUnaDecimaSeRechaza() {
        assertFalse(SaneadorCoordenadas.latitudValida(90.1))
        assertFalse(SaneadorCoordenadas.latitudValida(-90.1))
        assertFalse(SaneadorCoordenadas.longitudValida(180.1))
        assertFalse(SaneadorCoordenadas.longitudValida(-180.1))
    }

    /** Un proveedor averiado puede entregar esto, y de aqui iria derecho al cuerpo del POST. */
    @Test
    fun niNaNNiInfinitoSonUnPunto() {
        assertFalse(SaneadorCoordenadas.latitudValida(Double.NaN))
        assertFalse(SaneadorCoordenadas.latitudValida(Double.POSITIVE_INFINITY))
        assertFalse(SaneadorCoordenadas.longitudValida(Double.NEGATIVE_INFINITY))
    }

    /**
     * Siete decimales exactos: los que admite el patron ^-?\d{1,3}(\.\d{1,7})?$ del validador
     * del servidor y los que caben en la columna DECIMAL(10,7) de `ubicaciones`.
     */
    @Test
    fun formatearDejaSieteDecimales() {
        assertEquals("-74.1234568", SaneadorCoordenadas.formatear(-74.12345678))
    }

    /**
     * Con la regional colombiana puesta, "%.7f" escribiria la COMA decimal si el formateo no
     * fijara Locale.US: el cuerpo saldria como "4,6767000". El str_replace(',', '.') del
     * servidor lo salvaria, pero #9 decidio no depender de eso, y en una regional con otro
     * sistema de numeracion —arabe oriental, devanagari— ya no habria salvacion posible.
     */
    @Test
    fun laRegionalDelTelefonoNoMeteLaComaDecimal() {
        assertEquals("4.6767000", SaneadorCoordenadas.formatear(4.6767))
    }
}
