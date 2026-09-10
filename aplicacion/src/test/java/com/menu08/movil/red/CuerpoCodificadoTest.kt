package com.menu08.movil.red

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba local del cuerpo de formulario. Corre en la maquina de desarrollo, sin dispositivo y sin
 * red, porque cuerpoCodificado() es una funcion pura: lo unico que usa es URLEncoder.
 *
 * Un matiz que conviene dejar escrito, porque no se ve a simple vista. La funcion vive en
 * ClienteMenu08.kt, y ESE ARCHIVO SI importa android.util.Log y org.json —desde el #14 es donde
 * se lee el cuerpo de la respuesta—, que en una prueba de JVM son cuerpos reducidos a excepcion.
 * Aun asi la prueba no los toca: al ser una funcion de nivel superior, cuerpoCodificado() compila
 * a la clase fachada ClienteMenu08Kt, que es distinta del `object ClienteMenu08`. Cargar la
 * fachada no carga el objeto, asi que ninguno de esos cuerpos llega a ejecutarse.
 */
class CuerpoCodificadoTest {

    @Test
    fun laComaYElEspacioViajanCodificados() {
        val cuerpo = cuerpoCodificado(listOf("nombre" to "a,b", "_token" to "a b"))

        assertEquals("nombre=a%2Cb&_token=a+b", cuerpo)
    }

    @Test
    fun elOrdenDeLosCamposSeConserva() {
        val cuerpo = cuerpoCodificado(
            listOf("latitud" to "4.6767000", "longitud" to "-74.0483000", "_token" to "abc"),
        )

        assertEquals("latitud=4.6767000&longitud=-74.0483000&_token=abc", cuerpo)
    }

    @Test
    fun elTextoNoAsciiViajaEnUtf8() {
        // La ene va escapada para no meter un caracter no ASCII en el codigo: es la letra n con
        // virgulilla, que en UTF-8 son dos bytes y sale como %C3%B1.
        val cuerpo = cuerpoCodificado(listOf("correo" to "ni\u00f1o@menu08.local"))

        assertEquals("correo=ni%C3%B1o%40menu08.local", cuerpo)
    }

    @Test
    fun unaListaVaciaProduceUnCuerpoVacio() {
        assertEquals("", cuerpoCodificado(emptyList()))
    }
}
