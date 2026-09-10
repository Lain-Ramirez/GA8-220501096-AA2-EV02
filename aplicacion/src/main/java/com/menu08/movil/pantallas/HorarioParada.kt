package com.menu08.movil.pantallas

/**
 * La franja horaria de la parada, leida como la lee la web.
 *
 * Son las dos mismas reglas que `panel/ubicaciones.php` tiene como cierres locales, traidas aqui
 * para que los dos modulos digan lo mismo de la misma fila:
 *
 *     $hm    = substr((string) $hora, 0, 5);
 *     $cruza = (string) $u['hora_fin'] <= (string) $u['hora_inicio'];
 *
 * Antes el movil pintaba `hora_inicio` y `hora_fin` crudos del JSON, y salia «10:49:00 a
 * 10:49:00»: con los segundos que la web recorta, y sin decir que esa franja se cierra al dia
 * siguiente. Una parada creada por el propio reporte del GPS es justo ese caso —`asentarPunto()`
 * la escribe con las dos horas iguales a proposito, para que el reporte siguiente actualice la
 * fila en vez de sembrar una parada por pulsacion—, asi que era la que peor se leia.
 *
 * No importa nada de android.* ni de org.json: son dos funciones puras y se comprueban en la JVM.
 *
 * Issue #21 · Fase 2 - Aplicacion Android
 */
object HorarioParada {

    /** Cuantos caracteres tiene "HH:MM", que es lo que se enseña. */
    private const val LARGO_HORA_Y_MINUTO = 5

    /**
     * La hora sin los segundos. La base devuelve TIME como `18:00:00` y en la ficha sobran.
     *
     * take() y no substring(): con una cadena mas corta de lo esperado —una clave ausente llega
     * como vacia— substring reventaria, y una ficha no es sitio para eso.
     */
    fun corta(hora: String): String = hora.take(LARGO_HORA_Y_MINUTO)

    /**
     * La franja termina al dia siguiente.
     *
     * Es el caso normal de un truck nocturno —abre a las 18:00 y cierra a las 02:00— y tambien el
     * de la parada que asienta el propio reporte del GPS, con las dos horas iguales: esa queda
     * vigente 24 horas, que es lo que hace que el siguiente reporte la actualice.
     *
     * Se comparan las formas cortas para que `10:49` y `10:49:00` no cuenten como distintas, y se
     * exige que las dos vengan: sin esa guarda, dos cadenas vacias cumplirian `fin <= inicio` y
     * una parada sin horario se anunciaria como nocturna.
     */
    fun cierraAlDiaSiguiente(inicio: String, fin: String): Boolean =
        inicio.isNotBlank() && fin.isNotBlank() && corta(fin) <= corta(inicio)
}
