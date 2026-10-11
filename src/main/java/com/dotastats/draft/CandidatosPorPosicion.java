package com.dotastats.draft;

import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Candidatos por posición (1-5): la misma lógica que usaba {@link MotorSugerenciasPuntaje} para elegir
 * quién puede sugerirse en cada posición (listas curadas, participación en la línea, etiquetas de rol y
 * exclusión manual), extraída para que otros analizadores de draft (por ejemplo {@code AnalizadorHabilidad})
 * la reutilicen sin duplicarla. El resultado NO excluye todavía a los héroes ya elegidos en este draft: eso
 * depende de la petición concreta, no de la posición, así que lo aplica quien use este resultado.
 * <p>
 * La advertencia de "sin datos de línea, se usaron las etiquetas de rol" la sigue emitiendo únicamente
 * {@link MotorSugerenciasPuntaje} (vía {@link Resultado#sinDatosDeLinea()}) para no duplicarla en la respuesta
 * cuando varias filas reutilizan este cálculo.
 */
public final class CandidatosPorPosicion {

    private CandidatosPorPosicion() {
    }

    public record Resultado(List<HeroeMeta> candidatos, boolean sinDatosDeLinea) {
    }

    public static Resultado calcular(int miRol, DatosMeta datos, SugerenciasProperties props) {
        List<String> rolesValidos = props.roles().getOrDefault(miRol, List.of());
        Set<String> lista = listaCurada(miRol, props);
        Integer linea = MotorSugerenciasPuntaje.lineaDe(miRol);
        boolean porLinea = lista.isEmpty() && linea != null
                && datos.partidasPorLinea().getOrDefault(linea, Map.of()).size() > 0;
        boolean sinDatosDeLinea = lista.isEmpty() && linea != null && !porLinea;
        Set<String> excluidos = props.excluirPosicion().getOrDefault(miRol, List.of()).stream()
                .map(n -> n.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<HeroeMeta> candidatos = datos.heroes().values().stream()
                .filter(h -> !excluidos.contains(h.nombre().toLowerCase(Locale.ROOT)))
                .filter(h -> !lista.isEmpty()
                        ? lista.contains(HeroeAliasResolver.normalizar(h.nombre()))
                        : porLinea
                        ? juegaLaLinea(h.id(), linea, datos, props) && cumpleEtiquetaDeNucleo(h, miRol, rolesValidos)
                        : rolesValidos.isEmpty() || h.roles().stream().anyMatch(rolesValidos::contains))
                .toList();
        return new Resultado(candidatos, sinDatosDeLinea);
    }

    /** Nombres normalizados (sin acentos ni mayúsculas) de la lista curada de la posición; vacío si no tiene. */
    private static Set<String> listaCurada(int posicion, SugerenciasProperties props) {
        return props.heroesPosicion().getOrDefault(posicion, List.of()).stream()
                .map(HeroeAliasResolver::normalizar)
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * La línea segura la juegan también los soportes (la línea es de la lane, no de la posición), así que la
     * posición 1 exige además una etiqueta de núcleo (por defecto {@code Carry}). En la central y la ofensiva
     * solo cuenta la línea.
     */
    private static boolean cumpleEtiquetaDeNucleo(HeroeMeta h, int posicion, List<String> rolesValidos) {
        return posicion != 1 || rolesValidos.isEmpty() || h.roles().stream().anyMatch(rolesValidos::contains);
    }

    private static boolean juegaLaLinea(int heroId, int linea, DatosMeta datos, SugerenciasProperties props) {
        long total = 0;
        for (int l = 1; l <= 3; l++) {
            total += datos.partidasPorLinea().getOrDefault(l, Map.of()).getOrDefault(heroId, 0L);
        }
        long enLinea = datos.partidasPorLinea().getOrDefault(linea, Map.of()).getOrDefault(heroId, 0L);
        return total > 0 && enLinea >= props.minPartidasLinea() && (double) enLinea / total >= props.umbralLinea();
    }
}
