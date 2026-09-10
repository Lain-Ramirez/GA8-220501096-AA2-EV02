package com.menu08.movil.pantallas

/**
 * El dia de la semana del contrato del servicio, traducido a indice de array.
 *
 * La numeracion que manda es la de Ubicacion::DIAS del servidor: 1 lunes … 7 domingo. El array
 * `dias_semana` de strings.xml tiene esos siete en el mismo orden, asi que el indice es el dia
 * menos uno.
 *
 * Existe como archivo aparte, y devuelve el INDICE y no el texto, por una restriccion concreta:
 * leer el array de strings.xml exige un Context, y Context es una de las clases que el jar de
 * pruebas de la JVM trae con el cuerpo reducido a excepcion. Con el indice fuera, la regla se
 * comprueba sin dispositivo y la actividad se queda solo con la lectura del recurso.
 *
 * Issue #14 · Fase 2 - Aplicacion Android
 */
object DiaSemana {

    /** Cuantos dias tiene el array de strings.xml, y el CHECK de la tabla `ubicaciones`. */
    const val DIAS = 7

    /**
     * El indice del dia dentro del array, o -1 si el numero no es un dia.
     *
     * El -1 no es un valor de relleno: es lo que hace que la pantalla diga «dia no definido» en
     * vez de quedarse en blanco. La tabla tiene un CHECK que impide guardar fuera de 1 a 7, pero
     * un JSON sin la clave llega como 0, y ese es el caso que esto ataja.
     */
    fun indiceDia(dia: Int): Int = if (dia in 1..DIAS) dia - 1 else -1
}
