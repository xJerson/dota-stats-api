package com.dotastats.analizadores.habilidad;

import com.dotastats.analizadores.AnalizadorDraft;
import com.dotastats.analizadores.ContextoDraft;
import com.dotastats.analizadores.habilidad.dependencias.Contrarresto;
import com.dotastats.analizadores.habilidad.dependencias.RepositorioDependencias;
import com.dotastats.analizadores.habilidad.dependencias.TablaContrarrestos;
import com.dotastats.draft.Sugerencia;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fila "Sugerencias por jugabilidad": evalúa SOLO habilidades, como piezas de ajedrez (nada de objetos, farm
 * ni habilidad del jugador). Combina tres fuentes, solo contra los rivales DIRECTOS de {@code ctx.miRol()}
 * (nunca por un enemigo en otro slot) y solo sobre los candidatos de la posición:
 * <ul>
 *   <li>Reglas ESCRITAS A MANO ({@link RepositorioReglas}): tienen prioridad. Un candidato suma puntos cuando
 *       es el {@code counter} de una regla cuyo enemigo es un rival directo.</li>
 *   <li>Tabla CURADA de dependencias ({@link RepositorioDependencias}, {@link TablaContrarrestos}): para los
 *       enemigos que tienen dependencias curadas ("contrarrestar la dependencia, no al héroe"), el candidato
 *       suma por las herramientas que atacan esas dependencias. La tabla se precalcula una vez por snapshot:
 *       aquí solo se consulta. Para estos enemigos NO se usan las interacciones automáticas por palabra clave.</li>
 *   <li>Interacciones AUTOMÁTICAS ({@link CatalogoHabilidades}, {@link TablaInteraccionesAutomaticas}):
 *       derivadas por palabra clave de {@code /constants/abilities}, solo para los enemigos SIN dependencias
 *       curadas y los candidatos que no tienen una regla escrita a mano que ya los cubra. SIEMPRE confianza
 *       "media" (se presentan como "Posible: ...") y con un peso base por debajo de una regla a mano de esa
 *       misma confianza (ver {@link HabilidadProperties#pesoBaseAutomatico()}): son una heurística, pueden fallar.</li>
 * </ul>
 * Cuando un mismo candidato coincide por las dos fuentes (misma regla a mano o distinta), sus puntos SUMAN y
 * el motivo prioriza las coincidencias de mayor peso primero: como las reglas a mano pesan más, su motivo y
 * confianza ganan el desempate de forma natural, sin tratarlas como un caso especial.
 * <p>
 * Sin rivales directos elegidos, sin candidatos que coincidan, o si {@link CatalogoHabilidades} no pudo
 * obtener las habilidades de OpenDota (se añade una advertencia y se sigue solo con las reglas a mano), la
 * fila puede salir vacía: no es un error.
 * <p>
 * Los empates de puntaje se resuelven por el orden de {@link ContextoDraft#candidatos()}, que ya viene
 * ordenado de mejor a peor meta por posición (ver {@code ServicioSugerencias}): no se recalcula aquí para no
 * duplicar esa lógica, y {@link java.util.stream.Stream#sorted(Comparator)} es estable, así que un empate
 * conserva ese orden.
 */
@Component
public class AnalizadorHabilidad implements AnalizadorDraft {

    private static final int MAX_REGLAS_EN_MOTIVO = 2;
    private static final String CONFIANZA_ALTA = "alta";
    private static final String CONFIANZA_AUTOMATICA = "media";
    private static final String ADVERTENCIA_SIN_AUTOMATICO = "No se pudieron obtener las habilidades desde "
            + "OpenDota para las interacciones automáticas; la fila de jugabilidad usa solo las reglas "
            + "escritas a mano.";
    private static final String ADVERTENCIA_SIN_DEPENDENCIAS = "No se pudieron obtener las habilidades desde "
            + "OpenDota para las dependencias curadas de los héroes enemigos; la fila de jugabilidad usa solo las "
            + "reglas escritas a mano para ellos.";

    private final RepositorioReglas repositorio;
    private final HabilidadProperties props;
    private final CatalogoHabilidades catalogo;
    private final RepositorioDependencias dependencias;
    private final TablaContrarrestos tabla;

    public AnalizadorHabilidad(RepositorioReglas repositorio, HabilidadProperties props, CatalogoHabilidades catalogo,
                               RepositorioDependencias dependencias, TablaContrarrestos tabla) {
        this.repositorio = repositorio;
        this.props = props;
        this.catalogo = catalogo;
        this.dependencias = dependencias;
        this.tabla = tabla;
    }

    @Override
    public String id() {
        return "habilidad";
    }

    @Override
    public String titulo() {
        return "Sugerencias por jugabilidad";
    }

    @Override
    public int orden() {
        return 2;
    }

    /** Se muestra debajo de meta, pero se queda con el héroe repetido: aquí tiene un motivo concreto. */
    @Override
    public int prioridadRepetidos() {
        return 0;
    }

    @Override
    public List<Sugerencia> sugerir(ContextoDraft ctx) {
        if (ctx.rivalesDirectos().isEmpty()) {
            return List.of();
        }
        // Rivales directos con dependencias curadas: usan la tabla precalculada, nunca lo automático.
        Set<Integer> curados = ctx.rivalesDirectos().stream()
                .filter(id -> dependencias.tieneDependencias(id, ctx.datos()))
                .collect(Collectors.toSet());
        // Lo automático (y su advertencia) solo importa si algún rival directo NO es curado.
        boolean hayNoCurados = ctx.rivalesDirectos().stream().anyMatch(id -> !curados.contains(id));
        boolean automaticoDisponible = hayNoCurados && catalogo.disponible(ctx.datos());
        if (hayNoCurados && !automaticoDisponible) {
            ctx.advertencias().add(ADVERTENCIA_SIN_AUTOMATICO);
        }
        boolean tablaDisponible = !curados.isEmpty() && tabla.disponible(ctx.datos());
        if (!curados.isEmpty() && !tablaDisponible) {
            ctx.advertencias().add(ADVERTENCIA_SIN_DEPENDENCIAS);
        }
        List<Sugerencia> sinOrdenar = new ArrayList<>();
        for (HeroeMeta candidato : ctx.candidatos()) {
            List<Coincidencia> coincidencias = coincidenciasPara(candidato, ctx, automaticoDisponible, curados,
                    tablaDisponible);
            double puntos = coincidencias.stream().mapToDouble(Coincidencia::peso).sum();
            if (puntos <= 0) {
                continue;
            }
            sinOrdenar.add(new Sugerencia(candidato.id(), candidato.nombre(), motivo(coincidencias), puntos,
                    mejorConfianza(coincidencias)));
        }
        // sinOrdenar respeta el orden de ctx.candidatos() (meta por posición); sorted() es estable, así que un
        // empate de puntos no se reordena arbitrariamente.
        return sinOrdenar.stream()
                .sorted(Comparator.comparingDouble(Sugerencia::puntaje).reversed())
                .toList();
    }

    private List<Coincidencia> coincidenciasPara(HeroeMeta candidato, ContextoDraft ctx, boolean automaticoDisponible,
                                                 Set<Integer> curados, boolean tablaDisponible) {
        List<Coincidencia> coincidencias = new ArrayList<>();
        for (int rivalId : ctx.rivalesDirectos()) {
            List<Coincidencia> manos = new ArrayList<>();
            for (RepositorioReglas.ReglaResuelta rr : repositorio.reglasPara(rivalId, ctx.datos())) {
                if (rr.counterHeroId() != candidato.id()) {
                    continue;
                }
                double peso = props.peso(rr.regla().confianza());
                if (peso <= 0) {
                    continue;
                }
                manos.add(new Coincidencia(rr.regla().motivo(), rr.regla().confianza(), rr.regla().esUlti(), peso,
                        rr.regla().habilidad()));
            }
            List<Coincidencia> curadas = new ArrayList<>();
            if (curados.contains(rivalId)) {
                if (tablaDisponible) {
                    for (Contrarresto c : tabla.contrarrestos(rivalId, candidato.id(), ctx.datos())) {
                        // El motivo curado ya trae "(desde nivel 6)" dentro de la oración: no se repite al final.
                        curadas.add(new Coincidencia(c.motivo(), c.confianza(), false, c.peso(), c.habilidad()));
                    }
                }
            }
            coincidencias.addAll(unirManoYCurada(manos, curadas));
            if (!curados.contains(rivalId) && automaticoDisponible) {
                coincidencias.addAll(coincidenciasAutomaticas(candidato.id(), rivalId, ctx.datos()));
            }
        }
        return coincidencias;
    }

    /**
     * Una regla a mano y una herramienta curada de la MISMA habilidad (mismo enemigo y candidato) cuentan una sola
     * vez: se queda el mayor de los dos pesos (no la suma) y solo se muestra el motivo de la regla a mano. Las
     * habilidades distintas siguen sumando.
     */
    private static List<Coincidencia> unirManoYCurada(List<Coincidencia> manos, List<Coincidencia> curadas) {
        List<Coincidencia> resultado = new ArrayList<>(manos);
        for (Coincidencia curada : curadas) {
            int igual = -1;
            for (int i = 0; i < manos.size(); i++) {
                if (curada.habilidad() != null && curada.habilidad().equals(manos.get(i).habilidad())) {
                    igual = i;
                    break;
                }
            }
            if (igual < 0) {
                resultado.add(curada);
            } else if (curada.peso() > resultado.get(igual).peso()) {
                Coincidencia mano = resultado.get(igual);
                resultado.set(igual, new Coincidencia(mano.motivoBase(), mano.confianza(), mano.esUltiCandidato(),
                        curada.peso(), mano.habilidad()));
            }
        }
        return resultado;
    }

    /** Una interacción automática por cada par (rasgo del rival, capacidad del candidato) con multiplicador > 0. */
    private List<Coincidencia> coincidenciasAutomaticas(int candidatoId, int rivalId, DatosMeta datos) {
        double base = props.pesoBaseAutomatico();
        if (base <= 0) {
            return List.of();
        }
        Optional<PerfilHabilidadesHeroe> perfilCandidato = catalogo.perfilDe(candidatoId, datos);
        Optional<PerfilHabilidadesHeroe> perfilRival = catalogo.perfilDe(rivalId, datos);
        if (perfilCandidato.isEmpty() || perfilRival.isEmpty()) {
            return List.of();
        }
        List<Coincidencia> resultado = new ArrayList<>();
        for (Map.Entry<Rasgo, List<HabilidadDetectada>> delRival : perfilRival.get().rasgos().entrySet()) {
            for (Map.Entry<Capacidad, List<HabilidadDetectada>> delCandidato : perfilCandidato.get().capacidades().entrySet()) {
                double multiplicador = TablaInteraccionesAutomaticas.multiplicador(delRival.getKey(), delCandidato.getKey());
                if (multiplicador <= 0) {
                    continue;
                }
                HabilidadDetectada habilidadRival = delRival.getValue().get(0);
                HabilidadDetectada habilidadCandidato = delCandidato.getValue().get(0);
                String motivo = motivoAutomatico(delRival.getKey(), delCandidato.getKey(), habilidadCandidato, habilidadRival);
                resultado.add(new Coincidencia(motivo, CONFIANZA_AUTOMATICA, habilidadCandidato.esUlti(),
                        base * multiplicador, null));
            }
        }
        return resultado;
    }

    private static String motivoAutomatico(Rasgo rasgo, Capacidad capacidad, HabilidadDetectada delCandidato,
                                           HabilidadDetectada delRival) {
        String base = switch (capacidad) {
            case HEX -> delCandidato.nombre() + " anula " + delRival.nombre() + sufijoRasgo(rasgo);
            case SILENCIO -> delCandidato.nombre() + " lo silencia antes de que use " + delRival.nombre() + sufijoRasgo(rasgo);
            case STUN -> delCandidato.nombre() + " lo detiene antes de que use " + delRival.nombre() + sufijoRasgo(rasgo);
            case ROOT -> delCandidato.nombre() + " lo inmoviliza antes de que use " + delRival.nombre() + sufijoRasgo(rasgo);
            case TRUE_SIGHT -> delCandidato.nombre() + " revela su invisibilidad (" + delRival.nombre() + ")";
            case AREA -> delCandidato.nombre() + " daña en área y limpia sus ilusiones (" + delRival.nombre() + ")";
            case DISPEL_ALIADO -> delCandidato.nombre() + " te quita " + delRival.nombre() + " con un dispel";
        };
        if (delRival.esUlti()) {
            base = base + " (ulti)";
        }
        return base + ".";
    }

    /** Texto que conecta el motivo con POR QUÉ importa el rasgo del rival; vacío si no añade nada. */
    private static String sufijoRasgo(Rasgo rasgo) {
        return switch (rasgo) {
            case ESCAPE -> ", su escape";
            case CANALIZA -> ", interrumpiendo su canalización";
            default -> "";
        };
    }

    private static String mejorConfianza(List<Coincidencia> coincidencias) {
        return coincidencias.stream()
                .max(Comparator.comparingDouble(Coincidencia::peso))
                .map(Coincidencia::confianza)
                .orElse(null);
    }

    private static String motivo(List<Coincidencia> coincidencias) {
        return coincidencias.stream()
                .sorted(Comparator.comparingDouble(Coincidencia::peso).reversed())
                .limit(MAX_REGLAS_EN_MOTIVO)
                .map(AnalizadorHabilidad::frase)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    private static String frase(Coincidencia c) {
        String base = c.motivoBase();
        if (!CONFIANZA_ALTA.equals(c.confianza())) {
            base = "Posible: " + base;
        }
        if (c.esUltiCandidato()) {
            base = base + " (desde nivel 6)";
        }
        return base;
    }

    /**
     * Una coincidencia (de una regla a mano o de una interacción automática) a favor de un candidato, ya con
     * el peso resuelto. {@code motivoBase} es el texto crudo (sin el prefijo "Posible:" ni el sufijo de
     * ultimate, que añade {@link #frase}); {@code esUltiCandidato} es si la habilidad DEL CANDIDATO (no la
     * del rival) es su ultimate; {@code habilidad} es la clave de la habilidad del candidato (null en lo automático),
     * para no contar dos veces la misma habilidad por regla a mano y por herramienta curada.
     */
    private record Coincidencia(String motivoBase, String confianza, boolean esUltiCandidato, double peso,
                                String habilidad) {
    }
}
