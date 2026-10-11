package com.dotastats.analizadores.habilidad;

import java.util.List;
import java.util.Map;

/**
 * Lo que {@link CatalogoHabilidades} detectó en las habilidades de un héroe: sus {@link Rasgo}s (para cuando
 * es el ENEMIGO) y sus {@link Capacidad}es (para cuando es el CANDIDATO). Un héroe puede aparecer en ambos
 * roles en la misma petición (p. ej. dos héroes con hex se contrarrestan mutuamente), así que el perfil no
 * distingue rol: lo decide quien lo consulta.
 *
 * @param heroId       id del héroe
 * @param rasgos       rasgo -> habilidades en las que se detectó (normalmente una sola; varias si el héroe
 *                     tiene más de una habilidad con el mismo rasgo)
 * @param capacidades  capacidad -> habilidades en las que se detectó
 */
public record PerfilHabilidadesHeroe(int heroId, Map<Rasgo, List<HabilidadDetectada>> rasgos,
                                     Map<Capacidad, List<HabilidadDetectada>> capacidades) {

    public PerfilHabilidadesHeroe {
        rasgos = Map.copyOf(rasgos);
        capacidades = Map.copyOf(capacidades);
    }
}
