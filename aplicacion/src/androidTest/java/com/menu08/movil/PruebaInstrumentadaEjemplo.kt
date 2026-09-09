package com.menu08.movil

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba instrumentada de ejemplo, la que trae la plantilla: se ejecuta en un dispositivo Android.
 */
@RunWith(AndroidJUnit4::class)
class PruebaInstrumentadaEjemplo {

    @Test
    fun elPaqueteDeLaAplicacionEsElEsperado() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.menu08.movil", contexto.packageName)
    }
}
