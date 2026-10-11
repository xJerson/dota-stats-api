package com.dotastats.analizadores;

import com.dotastats.draft.RendimientoJugador;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Estado del draft ya resuelto, construido UNA VEZ por petición en {@link ServicioSugerencias} y compartido
 * por todos los {@link AnalizadorDraft}, para que ninguno tenga que repetir la resolución de héroes, el
 * cálculo de candidatos por posición ni el de rivales directos.
 *
 * @param miRol            posición del usuario (1-5)
 * @param enemigosPorSlot  slot (1-5) del rival -> heroId ya resuelto; sin entrada si ese slot está vacío
 * @param aliados          heroIds de los aliados ya elegidos (sin slot: el motor de meta no lo necesita)
 * @param accountId        account_id del jugador si la petición pidió personalizar; null si no
 * @param historial        historial personal por héroe si se pudo obtener; null sin cuenta o si OpenDota falló
 *                         (un perfil privado no llega a construir el contexto: la excepción se propaga antes)
 * @param rivalesDirectos  heroIds de los enemigos en los slots que juegan contra la línea de {@code miRol}
 *                         (ver {@code MotorSugerenciasPuntaje#oponentesDirectos}); una regla de habilidad
 *                         solo cuenta contra estos, nunca contra un enemigo en otro slot
 * @param candidatos       candidatos de la posición (misma lógica que el motor de meta, vía
 *                         {@code CandidatosPorPosicion}), ya sin los héroes elegidos por cualquiera de los dos
 *                         equipos y ordenados de mejor a peor meta por posición, para que cualquier fila que
 *                         desempate por ese criterio solo tenga que respetar este orden
 * @param datos            snapshot de meta vigente (para nombres de héroe, línea, etc.)
 * @param advertencias     única parte mutable del contexto: un analizador puede añadir aquí un aviso en texto
 *                         (por ejemplo, que no se pudieron consultar los duelos de un enemigo) para que
 *                         {@link ServicioSugerencias} lo incluya en la respuesta; no es parte de la interfaz
 *                         {@link AnalizadorDraft#sugerir} porque no todas las filas generan advertencias
 */
public record ContextoDraft(
        int miRol,
        Map<Integer, Integer> enemigosPorSlot,
        List<Integer> aliados,
        Long accountId,
        RendimientoJugador historial,
        Set<Integer> rivalesDirectos,
        List<HeroeMeta> candidatos,
        DatosMeta datos,
        List<String> advertencias) {

    public ContextoDraft {
        enemigosPorSlot = Map.copyOf(enemigosPorSlot);
        aliados = List.copyOf(aliados);
        rivalesDirectos = Set.copyOf(rivalesDirectos);
        candidatos = List.copyOf(candidatos);
    }
}
