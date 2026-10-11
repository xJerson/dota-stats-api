package com.dotastats.analizadores;

import com.dotastats.draft.Sugerencia;

import java.util.List;

/**
 * Punto de extensión de las filas de sugerencias del draft. Cada analizador produce UNA fila (por ejemplo,
 * "Sugerencias por meta" o "Sugerencias por jugabilidad"); {@link ServicioSugerencias} descubre todos los
 * {@code @Component} que implementan esta interfaz, los muestra según {@link #orden()}, reparte los héroes
 * repetidos según {@link #prioridadRepetidos()} y recorta cada fila al máximo de sugerencias configurado.
 * <p>
 * Sumar un análisis nuevo (por ejemplo, sinergia con aliados) es escribir una clase nueva con
 * {@code @Component} que implemente esta interfaz: ni {@link ServicioSugerencias} ni el controlador cambian.
 */
public interface AnalizadorDraft {

    /** Identificador estable de la fila (va en la respuesta; no se traduce). */
    String id();

    /** Título legible de la fila, en español, tal como lo muestra la interfaz. */
    String titulo();

    /** Posición de la fila en la respuesta: menor número se muestra primero. */
    int orden();

    /**
     * Qué fila se queda con un héroe cuando varias lo sugerirían: menor número gana y las demás no lo repiten
     * (rellenan con su siguiente candidato). Por defecto coincide con {@link #orden()}.
     */
    default int prioridadRepetidos() {
        return orden();
    }

    /** Sugerencias de esta fila para el contexto del draft, sin recortar ni deduplicar entre filas todavía. */
    List<Sugerencia> sugerir(ContextoDraft ctx);
}
