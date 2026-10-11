package com.dotastats.analizadores.habilidad;

import java.util.List;

/**
 * Una regla de interacción de habilidades, convertida de {@code docs/reglas-habilidades-borrador.md}: un
 * héroe (el {@code counter}) tiene una habilidad que anula, revela o castiga algo de uno o más héroes
 * enemigos, solo en la fase de línea.
 *
 * @param id              identificador estable de la regla (p. ej. "r1"); único dentro del archivo
 * @param enemigos        nombres de los héroes enemigos a los que aplica (ya expandidos: una fila "Zeus, Lina"
 *                        del borrador se guarda como dos nombres, no como texto libre)
 * @param rasgo           etiqueta opcional del enemigo (p. ej. "invisible"); solo informativo, no se usa para
 *                        puntuar
 * @param counter         nombre del héroe que aplica el counter
 * @param habilidad       clave interna de la habilidad del counter (p. ej. {@code lion_voodoo}), tal como la
 *                        usa OpenDota en {@code /constants/abilities}
 * @param habilidadNombre nombre mostrado de esa habilidad (p. ej. "Hex")
 * @param anula           claves internas de la o las habilidades del enemigo que esta regla anula/revela/
 *                        castiga; vacía cuando la regla es sobre un rasgo general (p. ej. "depende del maná")
 *                        y no de una habilidad concreta
 * @param anulaEsUlti     true si alguna de las habilidades de {@code anula} es la ULTIMATE del enemigo (para que
 *                        el motivo pueda decir "su ulti" en vez de solo el nombre)
 * @param esUlti          true si la habilidad del COUNTER ({@code habilidad}) es su ultimate: solo existe desde
 *                        nivel 6, así que el motivo generado lo aclara en vez de darlo por sentado desde el
 *                        minuto 0
 * @param confianza       "alta", "media" o "baja"; las de "media"/"baja" no se presentan como un counter seguro
 * @param motivo          texto en español, tal como lo vería el usuario
 */
public record ReglaHabilidad(
        String id,
        List<String> enemigos,
        String rasgo,
        String counter,
        String habilidad,
        String habilidadNombre,
        List<String> anula,
        boolean anulaEsUlti,
        boolean esUlti,
        String confianza,
        String motivo) {

    public ReglaHabilidad {
        enemigos = enemigos == null ? List.of() : List.copyOf(enemigos);
        anula = anula == null ? List.of() : List.copyOf(anula);
    }
}
