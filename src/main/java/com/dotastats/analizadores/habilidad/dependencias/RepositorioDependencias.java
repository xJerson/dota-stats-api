package com.dotastats.analizadores.habilidad.dependencias;

import com.dotastats.analizadores.habilidad.HabilidadProperties;
import com.dotastats.draft.HeroeAliasResolver;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Carga el archivo curado de dependencias de héroes enemigos y herramientas de candidatos
 * ({@code dotastats.habilidad.dependencias.archivo}, por defecto {@code dependencias-heroes.json}).
 * <p>
 * Igual que {@code RepositorioReglas}: la ESTRUCTURA se valida al arrancar (falla rápido, con un mensaje claro,
 * si falta un campo obligatorio, hay un valor de enum desconocido, un héroe repetido, etc.), y los NOMBRES de
 * héroe se resuelven de forma perezosa contra el snapshot de meta vigente (la fuente canónica de nombres, ver
 * AGENTS.md §7), no contra la red, para que ni el arranque ni los tests necesiten OpenDota. Un nombre que no
 * exista en ese snapshot es un error (se lanza la primera vez que se resuelve). Las claves de habilidad se
 * validan contra el snapshot de habilidades en {@link TablaContrarrestos}.
 */
@Component
public class RepositorioDependencias {

    private static final Logger log = LoggerFactory.getLogger(RepositorioDependencias.class);
    static final int MAX_DEPENDENCIAS_POR_HEROE = 3;
    private static final Set<String> CONFIANZAS_VALIDAS = Set.of("alta", "media", "baja");

    private final ResourceLoader resourceLoader;
    private final HabilidadProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile ArchivoDependencias archivo = new ArchivoDependencias(List.of(), List.of());
    private volatile DatosMeta resueltoContra;
    private volatile Resueltas resueltas = new Resueltas(Map.of(), Map.of(), List.of());

    public RepositorioDependencias(ResourceLoader resourceLoader, HabilidadProperties props) {
        this.resourceLoader = resourceLoader;
        this.props = props;
    }

    /**
     * Dependencias y herramientas ya indexadas por id de héroe (resueltas contra un snapshot de meta).
     * {@code problemas} lista las entradas descartadas porque su nombre de héroe no existe en el snapshot.
     */
    public record Resueltas(Map<Integer, List<Dependencia>> dependenciasPorHeroe,
                            Map<Integer, List<Herramienta>> herramientasPorHeroe, List<String> problemas) {
    }

    @PostConstruct
    public void cargar() {
        ArchivoDependencias leido = leerJson();
        validarCampos(leido);
        archivo = leido;
    }

    /** Las dependencias tal como están en el archivo, sin resolver contra ningún catálogo. */
    public List<DependenciasHeroe> todasLasDependencias() {
        return archivo.dependencias();
    }

    /** Las herramientas tal como están en el archivo, sin resolver contra ningún catálogo. */
    public List<HerramientasHeroe> todasLasHerramientas() {
        return archivo.herramientas();
    }

    /** true si el héroe enemigo tiene dependencias curadas (entonces no se usan las interacciones por palabra clave). */
    public boolean tieneDependencias(int enemigoId, DatosMeta datos) {
        return !resueltas(datos).dependenciasPorHeroe().getOrDefault(enemigoId, List.of()).isEmpty();
    }

    /**
     * Dependencias y herramientas indexadas por id de héroe contra {@code datos}; se recalcula solo si cambia el
     * snapshot ({@code ==} por referencia).
     *
     * Un nombre de héroe que no existe en el snapshot NO lanza: esa entrada se salta, se avisa por log y queda en
     * {@link Resueltas#problemas()}, para no romper toda la petición por un error del archivo curado.
     */
    public synchronized Resueltas resueltas(DatosMeta datos) {
        if (resueltoContra == datos) {
            return resueltas;
        }
        Map<String, Integer> idPorNombre = new HashMap<>();
        for (HeroeMeta h : datos.heroes().values()) {
            idPorNombre.put(HeroeAliasResolver.normalizar(h.nombre()), h.id());
        }
        List<String> problemas = new ArrayList<>();
        Map<Integer, List<Dependencia>> dependencias = new HashMap<>();
        for (DependenciasHeroe d : archivo.dependencias()) {
            Integer id = idPorNombre.get(HeroeAliasResolver.normalizar(d.heroe()));
            if (id == null) {
                problemas.add("Las dependencias referencian un héroe desconocido: '" + d.heroe() + "' (entrada descartada)");
                continue;
            }
            dependencias.put(id, d.dependencias());
        }
        Map<Integer, List<Herramienta>> herramientas = new HashMap<>();
        for (HerramientasHeroe h : archivo.herramientas()) {
            Integer id = idPorNombre.get(HeroeAliasResolver.normalizar(h.heroe()));
            if (id == null) {
                problemas.add("Las herramientas referencian un héroe desconocido: '" + h.heroe() + "' (entrada descartada)");
                continue;
            }
            herramientas.put(id, h.herramientas());
        }
        if (!problemas.isEmpty()) {
            log.warn("El archivo de dependencias de héroes tiene entradas que no existen en el snapshot: {}", problemas);
        }
        resueltas = new Resueltas(Map.copyOf(dependencias), Map.copyOf(herramientas), List.copyOf(problemas));
        resueltoContra = datos;
        return resueltas;
    }

    private ArchivoDependencias leerJson() {
        String ubicacion = props.dependencias().archivo();
        Resource recurso = resourceLoader.getResource(ubicacion);
        try (InputStream in = recurso.getInputStream()) {
            return mapper.readValue(in, ArchivoDependencias.class);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo de dependencias de héroes (" + ubicacion
                    + "): " + e.getMessage(), e);
        }
    }

    private void validarCampos(ArchivoDependencias leido) {
        Set<String> heroesConDependencias = new HashSet<>();
        for (DependenciasHeroe d : leido.dependencias()) {
            List<String> problemas = new ArrayList<>();
            if (esBlanco(d.heroe())) {
                problemas.add("falta heroe");
            } else if (!heroesConDependencias.add(d.heroe())) {
                problemas.add("héroe repetido");
            }
            if (d.dependencias().size() > MAX_DEPENDENCIAS_POR_HEROE) {
                problemas.add("tiene " + d.dependencias().size() + " dependencias (máximo " + MAX_DEPENDENCIAS_POR_HEROE + ")");
            }
            Set<Mecanismo> vistos = new HashSet<>();
            for (Dependencia dep : d.dependencias()) {
                if (dep.mecanismo() == null) {
                    problemas.add("falta mecanismo");
                } else if (!vistos.add(dep.mecanismo())) {
                    problemas.add("mecanismo repetido: " + dep.mecanismo());
                }
                if (esBlanco(dep.descripcion())) {
                    problemas.add("falta descripcion" + (dep.mecanismo() == null ? "" : " en " + dep.mecanismo()));
                }
            }
            lanzarSiHayProblemas("Dependencias de héroe inválidas", d.heroe(), problemas);
        }
        Set<String> heroesConHerramientas = new HashSet<>();
        for (HerramientasHeroe h : leido.herramientas()) {
            List<String> problemas = new ArrayList<>();
            if (esBlanco(h.heroe())) {
                problemas.add("falta heroe");
            } else if (!heroesConHerramientas.add(h.heroe())) {
                problemas.add("héroe repetido");
            }
            Set<String> vistas = new HashSet<>();
            for (Herramienta t : h.herramientas()) {
                validarHerramienta(t, vistas, problemas);
            }
            lanzarSiHayProblemas("Herramientas de héroe inválidas", h.heroe(), problemas);
        }
    }

    private void validarHerramienta(Herramienta t, Set<String> vistas, List<String> problemas) {
        String nombre = esBlanco(t.habilidad()) ? "sin habilidad" : t.habilidad();
        if (esBlanco(t.habilidad())) {
            problemas.add("falta habilidad (" + t.efecto() + ")");
        }
        if (t.efecto() == null) {
            problemas.add(nombre + ": falta efecto");
        } else {
            if (t.efecto() == Efecto.RANGO_MAYOR) {
                problemas.add(nombre + ": RANGO_MAYOR se deriva de las estadísticas y no se cura aquí");
            }
            if (!vistas.add(nombre + "/" + t.efecto())) {
                problemas.add(nombre + ": efecto repetido " + t.efecto());
            }
            if (t.efecto().exigeDuracion() && esBlanco(t.claveDuracion())) {
                problemas.add(nombre + ": el efecto " + t.efecto() + " exige claveDuracion");
            } else if (esBlanco(t.claveDuracion()) && esBlanco(t.claveEvidencia())) {
                problemas.add(nombre + ": falta claveDuracion o claveEvidencia que respalde el efecto " + t.efecto());
            }
        }
        if (!CONFIANZAS_VALIDAS.contains(t.confianza())) {
            problemas.add(nombre + ": confianza inválida (debe ser alta, media o baja): " + t.confianza());
        }
    }

    private void lanzarSiHayProblemas(String titulo, String heroe, List<String> problemas) {
        if (!problemas.isEmpty()) {
            throw new IllegalStateException(titulo + " (" + (esBlanco(heroe) ? "sin héroe" : heroe) + "): "
                    + String.join("; ", problemas));
        }
    }

    private boolean esBlanco(String s) {
        return s == null || s.isBlank();
    }
}
