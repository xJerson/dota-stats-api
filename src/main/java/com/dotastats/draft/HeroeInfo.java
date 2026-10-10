package com.dotastats.draft;

import java.util.List;

/**
 * Héroe del catálogo tal como lo consume la interfaz web.
 *
 * @param img   retrato grande (URL absoluta) o null si no se pudo determinar
 * @param icono miniatura (URL absoluta) o null si no se pudo determinar
 * @param alias abreviaturas conocidas que resuelven a este héroe
 */
public record HeroeInfo(int id, String nombre, String img, String icono, List<String> roles, List<String> alias) {
}
