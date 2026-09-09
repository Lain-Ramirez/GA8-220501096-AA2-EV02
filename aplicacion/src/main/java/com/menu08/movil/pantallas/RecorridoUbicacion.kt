package com.menu08.movil.pantallas

import org.json.JSONObject

/**
 * El recorrido por los estados de la pantalla de ubicacion, con el GPS y la red sin usar.
 *
 * Mientras el boton no haga lo suyo —eso es el issue #8—, cada pulsacion avanza un paso de esta
 * lista y pinta el siguiente estado. Sirve para ver los cuatro estados del boton, los dos
 * desenlaces del servicio y los cuatro avisos en un dispositivo, que es lo que hay que capturar
 * para las pruebas del issue #12.
 *
 * **Este archivo lo borra el issue #8**, que sustituye el paso del recorrido por la captura de
 * verdad. Esta aparte, y no dentro de la actividad, para que quitarlo sea borrar un archivo y una
 * llamada, sin dejar restos.
 *
 * Las fichas salen de un JSON con la forma exacta que devuelve POST /movil/ubicacion, y no de
 * objetos armados a mano: asi el recorrido ejercita tambien la lectura de Parada.desdeJson(),
 * incluido el caso de las tres columnas nulas, que es el unico sitio donde se ve si «Sin
 * referencia» y «Sin punto registrado» estan bien puestos.
 */
internal object RecorridoUbicacion {

    /** Parada vigente corregida: 200, con todas sus columnas llenas. */
    private const val VIGENTE = """
        {
          "nombre": "Parque programado por el dueno",
          "referencia": "costado sur, junto al kiosco",
          "latitud": "4.6512345",
          "longitud": "-74.0987654",
          "dia_semana": 2,
          "hora_inicio": "07:00:00",
          "hora_fin": "23:00:00"
        }
    """

    /**
     * Parada nueva: 201, la que registra el servicio cuando no habia ninguna vigente.
     *
     * El dia y la fecha del nombre tienen que concordar, porque el servicio los saca del mismo
     * instante: el 9 de septiembre de 2026 es miercoles, asi que `dia_semana` es 3. Y de paso el
     * recorrido resuelve dos dias distintos —el 2 de la parada vigente y este 3—, en vez de
     * enseñar dos veces la misma casilla del array.
     */
    private const val NUEVA = """
        {
          "nombre": "Punto reportado 2026-09-09 08:09",
          "referencia": "Registrado desde la aplicacion movil",
          "latitud": "4.6767000",
          "longitud": "-74.0483000",
          "dia_semana": 3,
          "hora_inicio": "08:09:00",
          "hora_fin": "08:09:00"
        }
    """

    /**
     * Una parada programada desde el panel sin referencia ni coordenadas: las tres columnas que
     * admiten nulo en la tabla, llegando como nulo. Ademas `dia_semana` viene fuera del rango de
     * 1 a 7, para ver que el campo del dia dice algo en vez de quedarse en blanco.
     */
    private const val INCOMPLETA = """
        {
          "nombre": "Parada sin punto todavia",
          "referencia": null,
          "latitud": null,
          "longitud": null,
          "dia_semana": 0,
          "hora_inicio": "18:00:00",
          "hora_fin": "17:59:59"
        }
    """

    private fun parada(json: String): Parada = Parada.desdeJson(JSONObject(json))

    /**
     * Los pasos, en orden. Cada uno recibe la pantalla y llama a la funcion que pinta ese estado,
     * que es la misma que llamara el issue #8.
     *
     * El estado «capturando» no es un paso: lo pinta la actividad al pulsar el boton, y este paso
     * es lo que llega despues. Asi el recorrido ve el indicador en cada vuelta sin quedarse
     * encallado en el, porque capturando deshabilita el boton y el boton es lo que avanza.
     *
     * Los cuatro avisos empiezan por devolver el boton a disponible, igual que tendra que hacer
     * el issue #8: un aviso no reactiva el boton por su cuenta —solo dice que ocurrio—, asi que
     * quien lo enseña tiene que decir tambien en que estado se queda la pantalla. Y se enseña de
     * uno en uno, para que la captura del issue #12 salga con un aviso y no con los cuatro.
     */
    val pasos: List<(ActividadUbicacion) -> Unit> = listOf(
        { pantalla -> pantalla.pintarParada(parada(VIGENTE), creada = false) },
        { pantalla -> pantalla.pintarParada(parada(NUEVA), creada = true) },
        { pantalla -> pantalla.pintarParada(parada(INCOMPLETA), creada = false) },
        { pantalla -> pantalla.pintarErrorSinPunto() },
        { pantalla ->
            pantalla.pintarDisponible()
            pantalla.ocultarAvisos()
            pantalla.mostrarAviso(AvisoUbicacion.PERMISO_DENEGADO)
        },
        { pantalla ->
            pantalla.pintarDisponible()
            pantalla.ocultarAvisos()
            pantalla.mostrarAviso(AvisoUbicacion.PERMISO_DENEGADO_SIEMPRE)
        },
        { pantalla ->
            pantalla.pintarDisponible()
            pantalla.ocultarAvisos()
            pantalla.mostrarAviso(AvisoUbicacion.PROVEEDOR_APAGADO)
        },
        { pantalla ->
            pantalla.pintarDisponible()
            pantalla.ocultarAvisos()
            pantalla.mostrarAviso(AvisoUbicacion.PUNTO_APROXIMADO)
        },
        { pantalla ->
            pantalla.pintarDisponible()
            pantalla.ocultarAvisos()
            pantalla.explicarPermiso()
        },
    )
}
