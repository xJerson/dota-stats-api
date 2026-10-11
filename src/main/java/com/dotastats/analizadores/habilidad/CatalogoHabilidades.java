package com.dotastats.analizadores.habilidad;

import com.dotastats.meta.DatosHabilidad;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.HeroeMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Convierte las habilidades crudas de OpenDota ({@code /constants/abilities} + {@code /constants/hero_abilities},
 * vía {@link DatosMetaService#datosHabilidad()}) en un {@link PerfilHabilidadesHeroe} por héroe: sus
 * {@link Rasgo}s (detectados por {@link DetectorHabilidades}, para cuando el héroe es el ENEMIGO) y sus
 * {@link Capacidad}es (para cuando es el CANDIDATO). Es la base de las interacciones automáticas de
 * {@code AnalizadorHabilidad}; las reglas escritas a mano no dependen de esta clase.
 * <p>
 * Se resuelve de forma perezosa (nada de red en el arranque ni en los tests) y se memoiza mientras no cambie
 * el snapshot de {@link DatosMeta} ({@code ==} por referencia, igual que {@code RepositorioReglas}). Si
 * OpenDota no responde (o no hay caché todavía), el catálogo queda "no disponible": no lanza, pero
 * {@link #perfilDe} siempre vacío y {@link #disponible} en {@code false}, para que quien lo use pueda caer de
 * vuelta en las reglas escritas a mano con una advertencia.
 */
@Component
public class CatalogoHabilidades {

    private static final Logger log = LoggerFactory.getLogger(CatalogoHabilidades.class);
    private static final String PREFIJO_TALENTO = "special_bonus";
    private static final String CLAVE_OCULTA = "generic_hidden";

    private final DatosMetaService metaService;

    private volatile DatosMeta resueltoContra;
    private volatile Map<Integer, PerfilHabilidadesHeroe> perfilesPorHeroe = Map.of();
    private volatile boolean disponible = true;

    public CatalogoHabilidades(DatosMetaService metaService) {
        this.metaService = metaService;
    }

    /** true si la última resolución contra {@code datos} obtuvo las habilidades de OpenDota. */
    public boolean disponible(DatosMeta datos) {
        asegurarResuelto(datos);
        return disponible;
    }

    /** Perfil de habilidades del héroe; vacío si no tiene nombre interno, OpenDota no lo conoce, o el catálogo
     *  no está disponible (ver {@link #disponible}). */
    public Optional<PerfilHabilidadesHeroe> perfilDe(int heroId, DatosMeta datos) {
        asegurarResuelto(datos);
        return Optional.ofNullable(perfilesPorHeroe.get(heroId));
    }

    private synchronized void asegurarResuelto(DatosMeta datos) {
        if (resueltoContra == datos) {
            return;
        }
        try {
            DatosHabilidad dh = metaService.datosHabilidad();
            perfilesPorHeroe = construir(dh, datos);
            disponible = true;
        } catch (RuntimeException e) {
            log.warn("No se pudieron obtener las habilidades de OpenDota; la fila de jugabilidad usa solo "
                    + "las reglas escritas a mano: {}", e.getMessage());
            perfilesPorHeroe = Map.of();
            disponible = false;
        }
        resueltoContra = datos;
    }

    private Map<Integer, PerfilHabilidadesHeroe> construir(DatosHabilidad dh, DatosMeta datos) {
        Map<Integer, PerfilHabilidadesHeroe> resultado = new HashMap<>();
        for (HeroeMeta heroe : datos.heroes().values()) {
            String nombreInterno = heroe.nombreInterno();
            if (nombreInterno == null) {
                continue;
            }
            Object entradaHeroe = dh.habilidadesPorHeroe().get(nombreInterno);
            if (!(entradaHeroe instanceof Map<?, ?> mapaHeroe)) {
                continue;
            }
            Object abilitiesObj = mapaHeroe.get("abilities");
            if (!(abilitiesObj instanceof List<?> claves)) {
                continue;
            }
            resultado.put(heroe.id(), perfilDeHeroe(heroe.id(), claves, dh.abilidades()));
        }
        return Map.copyOf(resultado);
    }

    @SuppressWarnings("unchecked")
    private PerfilHabilidadesHeroe perfilDeHeroe(int heroId, List<?> claves, Map<String, Object> abilidades) {
        Map<Rasgo, List<HabilidadDetectada>> rasgos = new EnumMap<>(Rasgo.class);
        Map<Capacidad, List<HabilidadDetectada>> capacidades = new EnumMap<>(Capacidad.class);
        for (Object claveObj : claves) {
            String clave = String.valueOf(claveObj);
            if (CLAVE_OCULTA.equals(clave) || clave.startsWith(PREFIJO_TALENTO)) {
                continue;
            }
            Object datosHabilidadObj = abilidades.get(clave);
            if (!(datosHabilidadObj instanceof Map)) {
                continue;
            }
            Map<String, Object> habilidad = (Map<String, Object>) datosHabilidadObj;
            HabilidadDetectada hd = new HabilidadDetectada(clave, DetectorHabilidades.nombre(habilidad, clave),
                    DetectorHabilidades.esUlti(habilidad));
            for (Rasgo r : Rasgo.values()) {
                if (DetectorHabilidades.tieneRasgo(r, habilidad)) {
                    rasgos.computeIfAbsent(r, k -> new ArrayList<>()).add(hd);
                }
            }
            for (Capacidad c : Capacidad.values()) {
                if (DetectorHabilidades.tieneCapacidad(c, habilidad)) {
                    capacidades.computeIfAbsent(c, k -> new ArrayList<>()).add(hd);
                }
            }
        }
        // Cuando un héroe tiene varias habilidades con el mismo rasgo/capacidad, la ulti va primero: es la
        // evidencia más fuerte y es la que AnalizadorHabilidad nombra en el motivo (ver CatalogoHabilidadesTest).
        rasgos.values().forEach(CatalogoHabilidades::ordenarPorFuerzaDeEvidencia);
        capacidades.values().forEach(CatalogoHabilidades::ordenarPorFuerzaDeEvidencia);
        return new PerfilHabilidadesHeroe(heroId, rasgos, capacidades);
    }

    /** Ulti primero (orden estable: entre dos no-ulti, o dos ulti, se conserva el orden de hero_abilities). */
    private static void ordenarPorFuerzaDeEvidencia(List<HabilidadDetectada> habilidades) {
        habilidades.sort(Comparator.comparing(HabilidadDetectada::esUlti).reversed());
    }
}
