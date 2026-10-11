package com.dotastats.analizadores.habilidad;

import com.dotastats.draft.HeroeAliasResolver;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carga las reglas de habilidad desde JSON ({@code dotastats.habilidad.reglas-archivo}) y las indexa por
 * héroe enemigo para que {@code AnalizadorHabilidad} las consulte en O(1).
 * <p>
 * La estructura del JSON se valida al arrancar (falla rápido, con un mensaje claro, si falta un campo
 * obligatorio o la confianza no es "alta"/"media"/"baja"). Resolver los NOMBRES de héroe a su id, en cambio,
 * se hace de forma perezosa contra el snapshot de meta vigente ({@link DatosMeta#heroes()}, la fuente
 * canónica: ver AGENTS.md §7) la primera vez que se piden reglas, y no contra el catálogo de OpenDota en el
 * arranque de Spring: así ni este repositorio ni los tests necesitan red para construirse, y la resolución
 * usa exactamente los mismos nombres canónicos que ya usa el motor de meta. Un nombre de héroe que no exista
 * en ese snapshot sigue siendo un error (se lanza en el primer uso, con el id de la regla y el nombre).
 */
@Component
public class RepositorioReglas {

    private static final Set<String> CONFIANZAS_VALIDAS = Set.of("alta", "media", "baja");

    private final ResourceLoader resourceLoader;
    private final HabilidadProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile List<ReglaHabilidad> reglas = List.of();
    private volatile DatosMeta resueltoContra;
    private volatile Map<Integer, List<ReglaResuelta>> indice = Map.of();

    public RepositorioReglas(ResourceLoader resourceLoader, HabilidadProperties props) {
        this.resourceLoader = resourceLoader;
        this.props = props;
    }

    /** Una regla ya resuelta contra el catálogo: el id del héroe que aplica {@link ReglaHabilidad#counter()}. */
    public record ReglaResuelta(ReglaHabilidad regla, int counterHeroId) {
    }

    @PostConstruct
    void cargar() {
        List<ReglaHabilidad> leidas = leerJson();
        validarCampos(leidas);
        reglas = List.copyOf(leidas);
    }

    /** Todas las reglas tal como están en el archivo, sin resolver contra ningún catálogo. */
    public List<ReglaHabilidad> todas() {
        return reglas;
    }

    /** Reglas cuyo enemigo incluye a {@code enemigoHeroId}; vacía si ninguna aplica. */
    public List<ReglaResuelta> reglasPara(int enemigoHeroId, DatosMeta datos) {
        asegurarResuelto(datos);
        return indice.getOrDefault(enemigoHeroId, List.of());
    }

    private List<ReglaHabilidad> leerJson() {
        Resource recurso = resourceLoader.getResource(props.reglasArchivo());
        try (InputStream in = recurso.getInputStream()) {
            return mapper.readValue(in, new TypeReference<List<ReglaHabilidad>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo de reglas de habilidad ("
                    + props.reglasArchivo() + "): " + e.getMessage(), e);
        }
    }

    private void validarCampos(List<ReglaHabilidad> leidas) {
        Set<String> ids = new HashSet<>();
        for (ReglaHabilidad r : leidas) {
            List<String> problemas = new ArrayList<>();
            if (esBlanco(r.id())) {
                problemas.add("falta id");
            } else if (!ids.add(r.id())) {
                problemas.add("id duplicado: " + r.id());
            }
            if (r.enemigos().isEmpty() || r.enemigos().stream().anyMatch(this::esBlanco)) {
                problemas.add("enemigos vacío o con algún nombre en blanco");
            }
            if (esBlanco(r.counter())) {
                problemas.add("falta counter");
            }
            if (esBlanco(r.habilidad())) {
                problemas.add("falta habilidad");
            }
            if (esBlanco(r.habilidadNombre())) {
                problemas.add("falta habilidadNombre");
            }
            if (esBlanco(r.motivo())) {
                problemas.add("falta motivo");
            }
            if (r.anula().stream().anyMatch(this::esBlanco)) {
                problemas.add("anula contiene algún nombre en blanco");
            }
            if (!CONFIANZAS_VALIDAS.contains(r.confianza())) {
                problemas.add("confianza inválida (debe ser alta, media o baja): " + r.confianza());
            }
            if (!problemas.isEmpty()) {
                throw new IllegalStateException("Regla de habilidad inválida ("
                        + (esBlanco(r.id()) ? "sin id" : r.id()) + "): " + String.join("; ", problemas));
            }
        }
    }

    private boolean esBlanco(String s) {
        return s == null || s.isBlank();
    }

    /** Indexa por heroId de enemigo, resolviendo nombres contra {@code datos}; se recalcula si cambia el snapshot. */
    private synchronized void asegurarResuelto(DatosMeta datos) {
        if (resueltoContra == datos) {
            return;
        }
        Map<String, Integer> idPorNombre = new HashMap<>();
        for (HeroeMeta h : datos.heroes().values()) {
            idPorNombre.put(HeroeAliasResolver.normalizar(h.nombre()), h.id());
        }
        Map<Integer, List<ReglaResuelta>> nuevo = new HashMap<>();
        for (ReglaHabilidad r : reglas) {
            Integer counterId = idPorNombre.get(HeroeAliasResolver.normalizar(r.counter()));
            if (counterId == null) {
                throw new IllegalStateException("La regla " + r.id() + " referencia un héroe desconocido como "
                        + "counter: '" + r.counter() + "'");
            }
            for (String nombreEnemigo : r.enemigos()) {
                Integer enemigoId = idPorNombre.get(HeroeAliasResolver.normalizar(nombreEnemigo));
                if (enemigoId == null) {
                    throw new IllegalStateException("La regla " + r.id() + " referencia un héroe enemigo "
                            + "desconocido: '" + nombreEnemigo + "'");
                }
                nuevo.computeIfAbsent(enemigoId, k -> new ArrayList<>()).add(new ReglaResuelta(r, counterId));
            }
        }
        indice = Map.copyOf(nuevo);
        resueltoContra = datos;
    }
}
