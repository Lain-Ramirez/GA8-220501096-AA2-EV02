package com.menu08.movil.red

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los ocho desenlaces del contrato de #2 y #3, sin abrir una sola conexion.
 *
 * esExito() y clasificarError() reciben el codigo y los campos YA extraidos, asi que son
 * funciones puras y se comprueban en la JVM. Leer el cuerpo con JSONObject se quedo en
 * ClienteMenu08 y se comprueba contra el servidor vivo en las pruebas de dispositivo de #12.
 *
 * Issue #14 · Fase 2 - Aplicacion Android
 */
class ResultadoTest {

    /** El 201 es la parada nueva que #8 pinta con otro texto; los dos son un viaje que salio bien. */
    @Test
    fun elDoscientosYElDoscientosUnoSonExito() {
        assertTrue(esExito(200))
        assertTrue(esExito(201))
    }

    @Test
    fun elCuatrocientosVeintidosNoEsExito() {
        assertFalse(esExito(422))
    }

    /** Los dos casos que disparan SesionMovil.cerrar() en el cliente de #5. */
    @Test
    fun laSesionRechazadaExigeVolverAIngresar() {
        assertTrue(clasificarError(401, "no_autenticado", "Debe ingresar.").exigeReingreso)
        assertTrue(clasificarError(403, "token_invalido", "El token expiro.").exigeReingreso)
    }

    /**
     * Al cajero no se le echa por no administrar la agenda, y un ingreso fallido no tiene
     * ninguna sesion que cerrar: el mismo codigo, otro nombre, otra decision.
     */
    @Test
    fun elRolSinPermisoYElIngresoFallidoNoLaExigen() {
        assertFalse(clasificarError(403, "rol_no_autorizado", "Ese rol no entra.").exigeReingreso)
        assertFalse(
            clasificarError(401, "credenciales_invalidas", "Correo o contrasena.").exigeReingreso,
        )
    }

    /** El 422 trae la frase del validador para la coordenada que fallo: se pinta tal cual. */
    @Test
    fun elCodigoYElMensajeDelCuerpoViajanIntactos() {
        val invalidas = clasificarError(
            422,
            "coordenadas_invalidas",
            "La latitud debe estar entre -90 y 90.",
        )

        assertEquals(422, invalidas.codigo)
        assertEquals("coordenadas_invalidas", invalidas.error)
        assertEquals("La latitud debe estar entre -90 y 90.", invalidas.mensaje)

        val fallo = clasificarError(500, "fallo_interno", "Ocurrio un error inesperado.")

        assertEquals(500, fallo.codigo)
        assertEquals("fallo_interno", fallo.error)
        assertEquals("Ocurrio un error inesperado.", fallo.mensaje)
        assertFalse(fallo.exigeReingreso)
    }
}
