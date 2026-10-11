package com.dotastats.analizadores.habilidad.dependencias;

import com.dotastats.analizadores.habilidad.DetectorHabilidades;
import com.dotastats.analizadores.habilidad.HabilidadProperties;
import com.dotastats.analizadores.habilidad.JuegoProperties;
import com.dotastats.juego.IndicadoresHabilidad;
import com.dotastats.meta.DatosHabilidad;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.HeroeMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tabla PRECALCULADA de contrarrestos ("contrarrestar la dependencia, no al héroe"): para cada enemigo con
 * dependencias curadas y cada candidato con herramientas curadas, qué herramienta del candidato ataca cada
 * dependencia, con su peso y su motivo ya escritos. Se construye UNA vez por snapshot de meta (referencia
 * {@code ==}, igual que {@code CatalogoHabilidades}: un parche nuevo trae un snapshot nuevo y por tanto una
 * tabla nueva), a partir de las dos fuentes que ya viven en memoria y en la caché de disco
 * ({@link DatosMetaService#datosHabilidad()} y el snapshot de meta). En la petición solo se consulta
 * ({@link #contrarrestos}): nada de red ni de cálculo pesado.
 * <p>
 * Qué es curado y qué sale de los datos:
 * <ul>
 *   <li>El TIPO de efecto (hex, silencio, stun...) y a qué mecanismo castiga se cura a mano en
 *       {@code dependencias-heroes.json}; OpenDota no lo dice y la descripción de texto se contradice con los
 *       atributos (p. ej. Inner Fire dice "silencing" pero solo trae {@code disarm_duration}).</li>
 *   <li>Los NÚMEROS (duración, punto de lanzamiento, retraso, bkbpierce) salen de los campos estructurados de
 *       OpenDota en su primer nivel; la fórmula de fiabilidad vive en {@link FiabilidadHerramienta}.</li>
 *   <li>La herramienta {@link Efecto#RANGO_MAYOR} no se cura: se deriva del {@code attack_range} base de
 *       {@code /constants/heroes} cuando el enemigo tiene la dependencia {@link Mecanismo#ACOSO_RANGO}.</li>
 * </ul>
 * Por cada (enemigo, candidato, mecanismo) solo cuenta la MEJOR herramienta, no la suma de todas. La herramienta
 * derivada RANGO_MAYOR exige una ventaja de alcance de al menos {@code dotastats.habilidad.dependencias.rango-minimo}.
 * <p>
 * Validación: un nombre de héroe desconocido NO rompe la petición: la entrada se salta y queda en {@link #problemas}
 * (ver {@code RepositorioDependencias}). Las claves
 * de habilidad y de atributo se validan contra el snapshot de habilidades (nunca contra la red): lo que no se
 * respalda ahí (clave desconocida, la habilidad no es del héroe, el atributo no existe o vale 0 en su primer nivel) se
 * descarta, se registra en {@link #problemas} y se avisa por log, para que un parche que renombre una habilidad
 * no rompa toda la fila. Si OpenDota no entrega las habilidades, la tabla queda no disponible (se memoiza por
 * snapshot, igual que {@code CatalogoHabilidades}).
 */
@Component
public class TablaContrarrestos {

    private static final Logger log = LoggerFactory.getLogger(TablaContrarrestos.class);
    private static final String CONFIANZA_ALCANCE = "media";
    /** Las dependencias derivadas de los archivos del juego (escape, pasivas) son heurística: siempre "baja". */
    private static final String CONFIANZA_DEPENDENCIA_DERIVADA = "baja";
    /** Las herramientas derivadas (disable duro) no distinguen stun de hex ni traen duración: "media". */
    private static final String CONFIANZA_HERRAMIENTA_DERIVADA = "media";

    private final DatosMetaService metaService;
    private final RepositorioDependencias repositorio;
    private final HabilidadProperties props;
    private final JuegoProperties juego;

    private volatile Construida construida;

    @Autowired
    public TablaContrarrestos(DatosMetaService metaService, RepositorioDependencias repositorio, HabilidadProperties props,
                              JuegoProperties juego) {
        this.metaService = metaService;
        this.repositorio = repositorio;
        this.props = props;
        this.juego = juego;
    }

    /** Con la configuración por defecto de lo derivado (sin pasivas). */
    public TablaContrarrestos(DatosMetaService metaService, RepositorioDependencias repositorio, HabilidadProperties props) {
        this(metaService, repositorio, props, new JuegoProperties(false));
    }

    /** {@code version}: versión de los indicadores del juego con la que se construyó (ver {@code DatosMetaService}). */
    private record Construida(DatosMeta datos, long version, Tabla tabla) {
    }

    private record Tabla(boolean disponible, Map<Integer, Map<Integer, List<Contrarresto>>> porEnemigo,
                         Set<Integer> cubiertos, boolean hayIndicadores, List<String> problemas) {
    }

    /** true si la tabla se pudo construir con las habilidades de OpenDota para este snapshot. */
    public boolean disponible(DatosMeta datos) {
        return tabla(datos).disponible();
    }

    /**
     * true si la tabla decide por este enemigo: tiene dependencias curadas O hay indicadores de los archivos del
     * juego para él (aunque de ellos no salga ninguna dependencia). Solo cuando es false (enemigo sin curar y sin
     * indicadores) la fila vuelve a las interacciones por palabra clave.
     */
    public boolean cubre(int enemigoId, DatosMeta datos) {
        return tabla(datos).cubiertos().contains(enemigoId);
    }

    /** true si el snapshot trae indicadores de los archivos del juego (de al menos un héroe). */
    public boolean hayIndicadoresDeJuego(DatosMeta datos) {
        return tabla(datos).hayIndicadores();
    }

    /** Contrarrestos del candidato contra las dependencias del enemigo (una por mecanismo); vacía si no hay. */
    public List<Contrarresto> contrarrestos(int enemigoId, int candidatoId, DatosMeta datos) {
        return tabla(datos).porEnemigo().getOrDefault(enemigoId, Map.of()).getOrDefault(candidatoId, List.of());
    }

    /** Problemas de validación del archivo curado contra el snapshot de habilidades (vacía si todo cuadra). */
    public List<String> problemas(DatosMeta datos) {
        return tabla(datos).problemas();
    }

    /**
     * Se memoiza por snapshot Y por versión de los indicadores del juego: si la tabla se construyó antes de que el
     * calentamiento trajera los archivos (o con GitHub caído), se reconstruye UNA vez cuando llegan en vez de quedarse
     * para siempre sin ellos. Consultar la versión es barato (sin red ni copias).
     */
    private Tabla tabla(DatosMeta datos) {
        long version = metaService.versionIndicadoresJuego();
        Construida actual = construida;
        if (actual != null && actual.datos() == datos && actual.version() == version) {
            return actual.tabla();
        }
        return construir(datos, version);
    }

    private synchronized Tabla construir(DatosMeta datos, long version) {
        Construida actual = construida;
        if (actual != null && actual.datos() == datos && actual.version() == version) {
            return actual.tabla();
        }
        // Un nombre de héroe inválido es un error del archivo: se propaga (no se confunde con "sin red").
        RepositorioDependencias.Resueltas resueltas = repositorio.resueltas(datos);
        DatosHabilidad habilidades = null;
        try {
            habilidades = metaService.datosHabilidad();
        } catch (RuntimeException e) {
            log.warn("No se pudieron obtener las habilidades de OpenDota; sin tabla de contrarrestos "
                    + "(la fila de jugabilidad usa solo las reglas escritas a mano): {}", e.getMessage());
        }
        Tabla nueva = habilidades == null
                ? new Tabla(false, Map.of(), Set.of(), false, resueltas.problemas())
                : new Constructor(datos, resueltas, habilidades).construir();
        construida = new Construida(datos, version, nueva);
        return nueva;
    }

    /** Construye la tabla para un snapshot; separado para no mezclar su estado temporal con el de la caché. */
    private final class Constructor {
        private final DatosMeta datos;
        private final RepositorioDependencias.Resueltas resueltas;
        private final DatosHabilidad dh;
        private final List<String> problemas = new ArrayList<>();

        Constructor(DatosMeta datos, RepositorioDependencias.Resueltas resueltas, DatosHabilidad dh) {
            this.datos = datos;
            this.resueltas = resueltas;
            this.dh = dh;
            this.problemas.addAll(resueltas.problemas());
        }

        Tabla construir() {
            Set<String> movimientoNoEscape = validarMovimientoNoEscape();
            Map<Integer, List<Herramienta>> herramientas = resueltas.herramientasPorHeroe();
            Map<Integer, List<HerramientaValida>> validas = new HashMap<>();
            herramientas.forEach((heroeId, lista) -> {
                List<HerramientaValida> ok = new ArrayList<>();
                for (Herramienta h : lista) {
                    validar(datos.heroes().get(heroeId), h).ifPresent(ok::add);
                }
                validas.put(heroeId, ok);
            });
            // Herramientas DERIVADAS de los archivos del juego (disable duro): la curada de la misma habilidad gana.
            for (HeroeMeta candidato : datos.heroes().values()) {
                List<HerramientaValida> derivadas = herramientasDerivadas(candidato,
                        herramientas.getOrDefault(candidato.id(), List.of()));
                if (!derivadas.isEmpty()) {
                    validas.computeIfAbsent(candidato.id(), k -> new ArrayList<>()).addAll(derivadas);
                }
            }

            Map<Integer, Map<Integer, List<Contrarresto>>> porEnemigo = new HashMap<>();
            Set<Integer> cubiertos = new HashSet<>();
            resueltas.dependenciasPorHeroe().forEach((enemigoId, dependencias) -> {
                if (dependencias.isEmpty()) {
                    return;
                }
                // Con dependencias curadas NUNCA se usan las derivadas: el curado gana entero.
                cubiertos.add(enemigoId);
                porEnemigo.put(enemigoId, contrarrestosDe(datos.heroes().get(enemigoId), dependencias, false, validas));
            });
            for (HeroeMeta enemigo : datos.heroes().values()) {
                if (cubiertos.contains(enemigo.id()) || indicadoresDe(enemigo).isEmpty()) {
                    continue;
                }
                // Solo se cubre al enemigo si de sus indicadores sale al menos UNA dependencia derivada: sin ninguna (un
                // héroe invisible o de ilusiones, sin escape) la tabla no tiene nada que decir y debe seguir
                // valiendo la detección por palabra clave de 5.1.
                List<Dependencia> derivadas = dependenciasDerivadas(enemigo, movimientoNoEscape);
                if (!derivadas.isEmpty()) {
                    cubiertos.add(enemigo.id());
                    porEnemigo.put(enemigo.id(), contrarrestosDe(enemigo, derivadas, true, validas));
                }
            }
            if (!problemas.isEmpty()) {
                log.warn("El archivo de dependencias de héroes no cuadra con las habilidades de OpenDota "
                        + "(se descartaron esas entradas): {}", problemas);
            }
            return new Tabla(true, Map.copyOf(porEnemigo), Set.copyOf(cubiertos), !dh.indicadoresPorHeroe().isEmpty(),
                    List.copyOf(problemas));
        }

        /**
         * Contrarrestos de todos los candidatos contra las dependencias de un enemigo. Si las dependencias son
         * {@code derivadas} (confianza baja) la confianza de cada camino es la MENOR entre la de la dependencia y la
         * de la herramienta, y por (candidato, mecanismo) solo cuenta la mejor (un héroe puede tener varios escapes).
         */
        private Map<Integer, List<Contrarresto>> contrarrestosDe(HeroeMeta enemigo, List<Dependencia> dependencias,
                                                                  boolean derivadas,
                                                                  Map<Integer, List<HerramientaValida>> validas) {
            Map<Integer, List<Contrarresto>> porCandidato = new HashMap<>();
            for (Dependencia dep : dependencias) {
                Optional<String> sujetoDep = sujeto(enemigo, dep);
                if (sujetoDep.isEmpty()) {
                    continue; // clave de habilidad inválida: ya reportada, la dependencia se descarta
                }
                String sujeto = sujetoDep.get();
                String confianzaDependencia = derivadas ? CONFIANZA_DEPENDENCIA_DERIVADA : null;
                validas.forEach((candidatoId, lista) -> {
                    if (candidatoId == enemigo.id()) {
                        return;
                    }
                    mejorHerramienta(enemigo, datos.heroes().get(candidatoId), dep, sujeto, lista, confianzaDependencia)
                            .ifPresent(c -> {
                                List<Contrarresto> actuales = porCandidato.computeIfAbsent(candidatoId, k -> new ArrayList<>());
                                if (derivadas) {
                                    agregarMejorPorMecanismo(actuales, c);
                                } else {
                                    actuales.add(c);
                                }
                            });
                });
                if (dep.mecanismo() == Mecanismo.ACOSO_RANGO) {
                    agregarRangoMayor(enemigo, sujeto, porCandidato);
                }
            }
            return congelar(porCandidato);
        }

        private void agregarMejorPorMecanismo(List<Contrarresto> actuales, Contrarresto nuevo) {
            for (int i = 0; i < actuales.size(); i++) {
                if (actuales.get(i).mecanismo() == nuevo.mecanismo()) {
                    if (nuevo.peso() > actuales.get(i).peso()) {
                        actuales.set(i, nuevo);
                    }
                    return;
                }
            }
            actuales.add(nuevo);
        }

        // --- Fuente derivada: indicadores de los archivos del juego ---

        private Map<String, IndicadoresHabilidad> indicadoresDe(HeroeMeta heroe) {
            if (heroe.nombreInterno() == null) {
                return Map.of();
            }
            return dh.indicadoresPorHeroe().getOrDefault(heroe.nombreInterno(), Map.of());
        }

        /** Habilidades del héroe según hero_abilities, en orden y sin repetir (vacío si no se puede saber). */
        private Set<String> habilidadesDelHeroe(HeroeMeta heroe) {
            if (heroe.nombreInterno() == null
                    || !(dh.habilidadesPorHeroe().get(heroe.nombreInterno()) instanceof Map<?, ?> mapa)
                    || !(mapa.get("abilities") instanceof List<?> claves)) {
                return Set.of();
            }
            Set<String> resultado = new LinkedHashSet<>();
            claves.forEach(c -> resultado.add(String.valueOf(c)));
            return resultado;
        }

        /** Claves de {@code movimientoNoEscape}; las desconocidas para el snapshot van a problemas (no rompen nada). */
        private Set<String> validarMovimientoNoEscape() {
            Set<String> validas = new HashSet<>();
            for (String clave : repositorio.movimientoNoEscape()) {
                if (habilidad(clave) == null) {
                    problemas.add("movimientoNoEscape: la habilidad '" + clave + "' es desconocida en el snapshot de OpenDota");
                } else {
                    validas.add(clave);
                }
            }
            return validas;
        }

        /**
         * ESCAPE: habilidades propias del héroe (según hero_abilities) que no se pueden usar enraizado y no están
         * ocultas, salvo las de {@code movimientoNoEscape}. PASIVAS: pasivas propias rompibles y no ocultas.
         */
        private List<Dependencia> dependenciasDerivadas(HeroeMeta enemigo, Set<String> movimientoNoEscape) {
            Map<String, IndicadoresHabilidad> indicadores = indicadoresDe(enemigo);
            List<Dependencia> derivadas = new ArrayList<>();
            for (String clave : habilidadesDelHeroe(enemigo)) {
                IndicadoresHabilidad ind = indicadores.get(clave);
                if (ind == null || ind.oculta() || habilidad(clave) == null) {
                    continue;
                }
                if (ind.desactivadaPorRoot() && !movimientoNoEscape.contains(clave)) {
                    derivadas.add(new Dependencia(Mecanismo.ESCAPE, clave,
                            "Derivada de los archivos del juego: la habilidad no se puede usar enraizado."));
                }
                if (juego.derivarPasivas() && ind.rompible() && ind.pasiva()) {
                    derivadas.add(new Dependencia(Mecanismo.PASIVAS, clave,
                            "Derivada de los archivos del juego: pasiva que el break anula."));
                }
            }
            return derivadas;
        }

        /**
         * STUN derivado (confianza media, sin clave de duración) para cada habilidad propia del candidato con
         * disable duro, no oculta y que no figure ya entre sus herramientas curadas (misma clave: gana la curada).
         */
        private List<HerramientaValida> herramientasDerivadas(HeroeMeta candidato, List<Herramienta> curadas) {
            Map<String, IndicadoresHabilidad> indicadores = indicadoresDe(candidato);
            if (indicadores.isEmpty()) {
                return List.of();
            }
            Set<String> clavesCuradas = new HashSet<>();
            curadas.forEach(h -> clavesCuradas.add(h.habilidad()));
            List<HerramientaValida> derivadas = new ArrayList<>();
            for (String clave : habilidadesDelHeroe(candidato)) {
                IndicadoresHabilidad ind = indicadores.get(clave);
                if (ind == null || !ind.disableDuro() || !ind.objetivoEnemigo() || ind.oculta() || clavesCuradas.contains(clave)
                        || habilidad(clave) == null) {
                    continue;
                }
                validar(candidato, new Herramienta(clave, Efecto.STUN, null, null, null, ind.ultimate(),
                        CONFIANZA_HERRAMIENTA_DERIVADA)).ifPresent(derivadas::add);
            }
            return derivadas;
        }

        private Map<Integer, List<Contrarresto>> congelar(Map<Integer, List<Contrarresto>> origen) {
            Map<Integer, List<Contrarresto>> copia = new HashMap<>();
            origen.forEach((k, v) -> copia.put(k, List.copyOf(v)));
            return Map.copyOf(copia);
        }

        /**
         * Arranque del motivo ("Storm Spirit depende de Ball Lightning (escape): "); vacío si la clave de
         * habilidad de la dependencia es inválida (se reporta y la dependencia se descarta).
         */
        private Optional<String> sujeto(HeroeMeta enemigo, Dependencia dep) {
            String nombreHabilidad = "su alcance de ataque";
            if (dep.habilidad() != null && !dep.habilidad().isBlank()) {
                Map<String, Object> habilidad = habilidad(dep.habilidad());
                if (habilidad == null) {
                    problemas.add(enemigo.nombre() + ": la habilidad '" + dep.habilidad() + "' de la dependencia "
                            + dep.mecanismo() + " es desconocida en el snapshot de OpenDota");
                    return Optional.empty();
                }
                if (!perteneceAlHeroe(enemigo, dep.habilidad())) {
                    problemas.add(enemigo.nombre() + ": la habilidad '" + dep.habilidad() + "' de la dependencia "
                            + dep.mecanismo() + " no pertenece a " + enemigo.nombre() + " según hero_abilities");
                    return Optional.empty();
                }
                nombreHabilidad = DetectorHabilidades.nombre(habilidad, dep.habilidad());
            }
            return Optional.of(enemigo.nombre() + " depende de " + nombreHabilidad + " (" + dep.mecanismo().texto() + "): ");
        }

        private Optional<HerramientaValida> validar(HeroeMeta heroe, Herramienta h) {
            String prefijo = heroe.nombre() + ": ";
            Map<String, Object> habilidad = habilidad(h.habilidad());
            if (habilidad == null) {
                problemas.add(prefijo + "la habilidad '" + h.habilidad() + "' (" + h.efecto()
                        + ") es desconocida en el snapshot de OpenDota");
                return Optional.empty();
            }
            if (!perteneceAlHeroe(heroe, h.habilidad())) {
                problemas.add(prefijo + "la habilidad '" + h.habilidad() + "' no pertenece a " + heroe.nombre()
                        + " según hero_abilities");
                return Optional.empty();
            }
            Double duracion = null;
            String evidencia = null;
            double retraso = 0.0;
            if (h.claveDuracion() != null && !h.claveDuracion().isBlank()) {
                String valor = nivel1(habilidad, h.claveDuracion());
                Double numero = numero(valor);
                if (numero == null || numero <= 0) {
                    problemas.add(prefijo + respaldoFallido(h, h.claveDuracion(), valor));
                    return Optional.empty();
                }
                duracion = numero;
            }
            if (h.claveEvidencia() != null && !h.claveEvidencia().isBlank()) {
                String valor = nivel1(habilidad, h.claveEvidencia());
                Double numero = numero(valor);
                if (numero == null || numero <= 0) {
                    problemas.add(prefijo + respaldoFallido(h, h.claveEvidencia(), valor));
                    return Optional.empty();
                }
                evidencia = valor;
            }
            if (h.claveRetraso() != null && !h.claveRetraso().isBlank()) {
                String valor = nivel1(habilidad, h.claveRetraso());
                Double numero = numero(valor);
                if (numero == null || numero < 0) {
                    problemas.add(prefijo + respaldoFallido(h, h.claveRetraso(), valor));
                    return Optional.empty();
                }
                retraso = numero;
            }
            Double puntoLanzamiento = numero(nivel1(habilidad, "abilitycastpoint"));
            return Optional.of(new HerramientaValida(h, DetectorHabilidades.nombre(habilidad, h.habilidad()), duracion,
                    evidencia, puntoLanzamiento == null ? 0.0 : puntoLanzamiento, retraso,
                    "Yes".equals(habilidad.get("bkbpierce")), esPasiva(habilidad)));
        }

        private String respaldoFallido(Herramienta h, String clave, String valor) {
            return "la habilidad '" + h.habilidad() + "' no respalda el efecto " + h.efecto() + " con la clave '" + clave
                    + "': " + (valor == null ? "no existe en sus atributos" : "vale " + valor + " en su primer nivel");
        }

        /**
         * La mejor herramienta del candidato contra la dependencia. Confianza del camino: la de la herramienta; si
         * la dependencia es derivada ({@code confianzaDependencia} no nula) es la MENOR de las dos, y es la que
         * pesa (peso = peso(confianza) x multiplicador efecto/mecanismo x fiabilidad).
         */
        private Optional<Contrarresto> mejorHerramienta(HeroeMeta enemigo, HeroeMeta candidato, Dependencia dep,
                                                         String sujeto, List<HerramientaValida> herramientas,
                                                         String confianzaDependencia) {
            Contrarresto mejor = null;
            for (HerramientaValida t : herramientas) {
                double multiplicador = TablaPesosEfectoMecanismo.multiplicador(dep.mecanismo(), t.def().efecto());
                if (multiplicador <= 0) {
                    continue;
                }
                double fiabilidad = FiabilidadHerramienta.calcular(t.duracion(), t.puntoLanzamiento(), t.retraso(),
                        t.atraviesaBkb(), t.def().esUlti());
                String confianza = confianzaDependencia == null
                        ? t.def().confianza() : menorConfianza(confianzaDependencia, t.def().confianza());
                double peso = props.peso(confianza) * multiplicador * fiabilidad;
                if (mejor == null || peso > mejor.peso()) {
                    mejor = new Contrarresto(enemigo.id(), candidato.id(), dep.mecanismo(), t.def().efecto(),
                            t.def().habilidad(), sujeto + frase(t, candidato) + (t.def().esUlti() ? " (desde nivel 6)" : "") + ".",
                            confianza, t.def().esUlti(), peso);
                }
            }
            return Optional.ofNullable(mejor);
        }

        private void agregarRangoMayor(HeroeMeta enemigo, String sujeto, Map<Integer, List<Contrarresto>> porCandidato) {
            Integer alcanceEnemigo = dh.rangoAtaquePorHeroe().get(enemigo.id());
            if (alcanceEnemigo == null) {
                return;
            }
            double multiplicador = TablaPesosEfectoMecanismo.multiplicador(Mecanismo.ACOSO_RANGO, Efecto.RANGO_MAYOR);
            for (HeroeMeta candidato : datos.heroes().values()) {
                Integer alcance = dh.rangoAtaquePorHeroe().get(candidato.id());
                if (candidato.id() == enemigo.id() || alcance == null
                        || alcance - alcanceEnemigo < props.dependencias().rangoMinimo()) {
                    continue;
                }
                double peso = props.peso(CONFIANZA_ALCANCE) * multiplicador
                        * FiabilidadHerramienta.deAlcance(alcance - alcanceEnemigo);
                String motivo = sujeto + candidato.nombre() + " tiene más alcance de ataque (" + alcance
                        + " frente a " + alcanceEnemigo + ").";
                porCandidato.computeIfAbsent(candidato.id(), k -> new ArrayList<>()).add(new Contrarresto(
                        enemigo.id(), candidato.id(), Mecanismo.ACOSO_RANGO, Efecto.RANGO_MAYOR, null, motivo,
                        CONFIANZA_ALCANCE, false, peso));
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> habilidad(String clave) {
            Object o = dh.abilidades().get(clave);
            return o instanceof Map ? (Map<String, Object>) o : null;
        }

        /** Si hero_abilities no trae al héroe (o el héroe no tiene nombre interno) no se puede comprobar: se acepta. */
        private boolean perteneceAlHeroe(HeroeMeta heroe, String clave) {
            if (heroe.nombreInterno() == null) {
                return true;
            }
            Object entrada = dh.habilidadesPorHeroe().get(heroe.nombreInterno());
            if (!(entrada instanceof Map<?, ?> mapa) || !(mapa.get("abilities") instanceof List<?> claves)) {
                return true;
            }
            return claves.stream().anyMatch(c -> clave.equals(String.valueOf(c)));
        }
    }

    /** Una herramienta ya respaldada por los atributos de OpenDota, con sus números de nivel 1. */
    private record HerramientaValida(Herramienta def, String nombre, Double duracion, String evidencia,
                                     double puntoLanzamiento, double retraso, boolean atraviesaBkb, boolean pasiva) {
    }

    // --- Texto ---

    /** La menor de dos confianzas ("alta" &gt; "media" &gt; "baja"); una desconocida cuenta como "baja". */
    static String menorConfianza(String a, String b) {
        return rango(a) <= rango(b) ? a : b;
    }

    private static int rango(String confianza) {
        return switch (confianza) {
            case "alta" -> 3;
            case "media" -> 2;
            default -> 1;
        };
    }

    private static String frase(HerramientaValida t, HeroeMeta candidato) {
        String duracion = t.duracion() == null ? null : formatear(t.duracion()) + " s en su primer nivel";
        String efecto = switch (t.def().efecto()) {
            case HEX -> "lo desactiva " + duracion;
            case SILENCIO -> "lo silencia " + duracion;
            // Un STUN derivado de los archivos del juego no trae duración curada: no se inventa ninguna cifra.
            case STUN -> duracion == null ? "lo desactiva (disable duro)" : "lo aturde " + duracion;
            case ROOT -> "lo inmoviliza " + duracion;
            case DESARME -> "lo desarma " + duracion;
            case BREAK -> duracion == null ? "le anula las pasivas" : "le anula las pasivas " + duracion;
            case REVELADO -> duracion == null ? "revela su invisibilidad" : "revela su invisibilidad " + duracion;
            case AREA -> "daña en área" + (t.evidencia() == null ? "" : " (radio " + t.evidencia() + " en su primer nivel)");
            case QUEMA_MANA -> "le quema maná" + (t.evidencia() == null ? "" : " (" + t.evidencia() + " en su primer nivel)");
            case RANGO_MAYOR -> "tiene más alcance de ataque";
        };
        List<String> detalles = new ArrayList<>();
        if (!t.pasiva()) {
            detalles.add(t.puntoLanzamiento() <= 0 ? "lanzamiento instantáneo"
                    : "lanzamiento de " + formatear(t.puntoLanzamiento()) + " s");
        }
        if (t.retraso() > 0) {
            detalles.add("efecto tras " + formatear(t.retraso()) + " s");
        }
        if (t.atraviesaBkb()) {
            detalles.add("atraviesa la inmunidad a la magia");
        }
        String base = t.nombre() + " de " + candidato.nombre() + " " + efecto;
        return detalles.isEmpty() ? base : base + ", " + String.join(", ", detalles);
    }

    /** 2 -> "2", 2.5 -> "2,5": coma decimal en español, sin ceros sobrantes. */
    static String formatear(double valor) {
        return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    // --- Lectura de los atributos de OpenDota (nivel 1) ---

    /** Valor en su primer nivel (primer elemento si es una lista) del atributo {@code clave}; null si no existe. */
    static String nivel1(Map<String, Object> habilidad, String clave) {
        if (!(habilidad.get("attrib") instanceof List<?> atributos)) {
            return null;
        }
        for (Object o : atributos) {
            if (o instanceof Map<?, ?> a && clave.equals(a.get("key"))) {
                Object valor = a.get("value");
                if (valor instanceof List<?> lista) {
                    return lista.isEmpty() ? null : String.valueOf(lista.get(0));
                }
                return valor == null ? null : String.valueOf(valor);
            }
        }
        return null;
    }

    /** "2.5" -> 2.5, "+2.5" -> 2.5, "9%" -> 9; null si no es un número. */
    static Double numero(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim().replace("%", "").replace("+", "");
        try {
            return Double.valueOf(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean esPasiva(Map<String, Object> habilidad) {
        Object behavior = habilidad.get("behavior");
        if (behavior instanceof List<?> lista) {
            return lista.stream().anyMatch(b -> "Passive".equals(String.valueOf(b)));
        }
        return "Passive".equals(String.valueOf(behavior));
    }
}
