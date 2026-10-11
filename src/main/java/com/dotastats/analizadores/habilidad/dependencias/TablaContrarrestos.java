package com.dotastats.analizadores.habilidad.dependencias;

import com.dotastats.analizadores.habilidad.DetectorHabilidades;
import com.dotastats.analizadores.habilidad.HabilidadProperties;
import com.dotastats.meta.DatosHabilidad;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.HeroeMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private final DatosMetaService metaService;
    private final RepositorioDependencias repositorio;
    private final HabilidadProperties props;

    private volatile Construida construida;

    public TablaContrarrestos(DatosMetaService metaService, RepositorioDependencias repositorio, HabilidadProperties props) {
        this.metaService = metaService;
        this.repositorio = repositorio;
        this.props = props;
    }

    private record Construida(DatosMeta datos, Tabla tabla) {
    }

    private record Tabla(boolean disponible, Map<Integer, Map<Integer, List<Contrarresto>>> porEnemigo,
                         List<String> problemas) {
    }

    /** true si la tabla se pudo construir con las habilidades de OpenDota para este snapshot. */
    public boolean disponible(DatosMeta datos) {
        return tabla(datos).disponible();
    }

    /** Contrarrestos del candidato contra las dependencias del enemigo (una por mecanismo); vacía si no hay. */
    public List<Contrarresto> contrarrestos(int enemigoId, int candidatoId, DatosMeta datos) {
        return tabla(datos).porEnemigo().getOrDefault(enemigoId, Map.of()).getOrDefault(candidatoId, List.of());
    }

    /** Problemas de validación del archivo curado contra el snapshot de habilidades (vacía si todo cuadra). */
    public List<String> problemas(DatosMeta datos) {
        return tabla(datos).problemas();
    }

    private Tabla tabla(DatosMeta datos) {
        Construida actual = construida;
        if (actual != null && actual.datos() == datos) {
            return actual.tabla();
        }
        return construir(datos);
    }

    private synchronized Tabla construir(DatosMeta datos) {
        Construida actual = construida;
        if (actual != null && actual.datos() == datos) {
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
        Tabla nueva = habilidades == null ? new Tabla(false, Map.of(), resueltas.problemas()) : new Constructor(datos, resueltas, habilidades).construir();
        construida = new Construida(datos, nueva);
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
            Map<Integer, List<Herramienta>> herramientas = resueltas.herramientasPorHeroe();
            Map<Integer, List<HerramientaValida>> validas = new HashMap<>();
            herramientas.forEach((heroeId, lista) -> {
                List<HerramientaValida> ok = new ArrayList<>();
                for (Herramienta h : lista) {
                    validar(datos.heroes().get(heroeId), h).ifPresent(ok::add);
                }
                validas.put(heroeId, ok);
            });

            Map<Integer, Map<Integer, List<Contrarresto>>> porEnemigo = new HashMap<>();
            resueltas.dependenciasPorHeroe().forEach((enemigoId, dependencias) -> {
                HeroeMeta enemigo = datos.heroes().get(enemigoId);
                Map<Integer, List<Contrarresto>> porCandidato = new HashMap<>();
                for (Dependencia dep : dependencias) {
                    Optional<String> sujetoDep = sujeto(enemigo, dep);
                    if (sujetoDep.isEmpty()) {
                        continue; // clave de habilidad inválida: ya reportada, la dependencia se descarta
                    }
                    String sujeto = sujetoDep.get();
                    validas.forEach((candidatoId, lista) -> {
                        if (candidatoId == enemigoId.intValue()) {
                            return;
                        }
                        mejorHerramienta(enemigo, datos.heroes().get(candidatoId), dep, sujeto, lista)
                                .ifPresent(c -> porCandidato.computeIfAbsent(candidatoId, k -> new ArrayList<>()).add(c));
                    });
                    if (dep.mecanismo() == Mecanismo.ACOSO_RANGO) {
                        agregarRangoMayor(enemigo, sujeto, porCandidato);
                    }
                }
                porEnemigo.put(enemigoId, congelar(porCandidato));
            });
            if (!problemas.isEmpty()) {
                log.warn("El archivo de dependencias de héroes no cuadra con las habilidades de OpenDota "
                        + "(se descartaron esas entradas): {}", problemas);
            }
            return new Tabla(true, Map.copyOf(porEnemigo), List.copyOf(problemas));
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

        private Optional<Contrarresto> mejorHerramienta(HeroeMeta enemigo, HeroeMeta candidato, Dependencia dep,
                                                         String sujeto, List<HerramientaValida> herramientas) {
            Contrarresto mejor = null;
            for (HerramientaValida t : herramientas) {
                double multiplicador = TablaPesosEfectoMecanismo.multiplicador(dep.mecanismo(), t.def().efecto());
                if (multiplicador <= 0) {
                    continue;
                }
                double fiabilidad = FiabilidadHerramienta.calcular(t.duracion(), t.puntoLanzamiento(), t.retraso(),
                        t.atraviesaBkb(), t.def().esUlti());
                double peso = props.peso(t.def().confianza()) * multiplicador * fiabilidad;
                if (mejor == null || peso > mejor.peso()) {
                    mejor = new Contrarresto(enemigo.id(), candidato.id(), dep.mecanismo(), t.def().efecto(),
                            t.def().habilidad(), sujeto + frase(t, candidato) + (t.def().esUlti() ? " (desde nivel 6)" : "") + ".", t.def().confianza(), t.def().esUlti(), peso);
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

    private static String frase(HerramientaValida t, HeroeMeta candidato) {
        String duracion = t.duracion() == null ? null : formatear(t.duracion()) + " s en su primer nivel";
        String efecto = switch (t.def().efecto()) {
            case HEX -> "lo desactiva " + duracion;
            case SILENCIO -> "lo silencia " + duracion;
            case STUN -> "lo aturde " + duracion;
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
