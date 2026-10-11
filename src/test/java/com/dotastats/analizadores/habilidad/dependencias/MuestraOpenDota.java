package com.dotastats.analizadores.habilidad.dependencias;

import com.dotastats.client.Parche;
import com.dotastats.meta.DatosHabilidad;
import com.dotastats.meta.DatosMeta;
import com.dotastats.meta.HeroeMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Subconjunto REAL de las constantes de OpenDota (copiado de {@code /constants/abilities},
 * {@code /constants/hero_abilities} y {@code /constants/heroes} para los héroes del archivo curado), guardado en
 * {@code src/test/resources/habilidades/muestra-opendota.json} para que los tests no toquen la red y aun así
 * prueben contra números y claves reales (p. ej. que {@code huskar_inner_fire} no trae {@code silence_duration}).
 */
public final class MuestraOpenDota {

    public static final int STORM_SPIRIT = 17;
    public static final int RIKI = 32;
    public static final int PHANTOM_LANCER = 12;
    public static final int HUSKAR = 59;
    public static final int VIPER = 47;
    public static final int LION = 26;
    public static final int DROW = 6;
    public static final int ZEUS = 22;
    public static final int SLARDAR = 28;
    public static final int DEATH_PROPHET = 43;
    public static final int ANTI_MAGE = 1;
    public static final int LESHRAC = 52;
    public static final int CRYSTAL_MAIDEN = 5;

    private final DatosMeta datosMeta;
    private final DatosHabilidad datosHabilidad;

    private MuestraOpenDota(DatosMeta datosMeta, DatosHabilidad datosHabilidad) {
        this.datosMeta = datosMeta;
        this.datosHabilidad = datosHabilidad;
    }

    public static MuestraOpenDota cargar() {
        try (InputStream in = MuestraOpenDota.class.getResourceAsStream("/habilidades/muestra-opendota.json")) {
            Map<String, Object> raiz = new ObjectMapper().readValue(in, new TypeReference<>() {
            });
            Map<Integer, HeroeMeta> heroes = new HashMap<>();
            for (Object o : (List<?>) raiz.get("heroes")) {
                Map<?, ?> h = (Map<?, ?>) o;
                int id = ((Number) h.get("id")).intValue();
                heroes.put(id, new HeroeMeta(id, (String) h.get("nombre"), Set.of(), 1000, 500,
                        (String) h.get("nombreInterno")));
            }
            Map<Integer, Integer> rango = new HashMap<>();
            ((Map<?, ?>) raiz.get("rangoAtaque")).forEach((k, v) ->
                    rango.put(Integer.parseInt(String.valueOf(k)), ((Number) v).intValue()));
            @SuppressWarnings("unchecked")
            Map<String, Object> abilidades = (Map<String, Object>) raiz.get("abilidades");
            @SuppressWarnings("unchecked")
            Map<String, Object> porHeroe = (Map<String, Object>) raiz.get("habilidadesPorHeroe");
            return new MuestraOpenDota(
                    new DatosMeta(heroes, new Parche(60, "7.41"), Instant.parse("2026-10-10T12:00:00Z")),
                    new DatosHabilidad(abilidades, porHeroe, rango));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public DatosMeta datosMeta() {
        return datosMeta;
    }

    public DatosHabilidad datosHabilidad() {
        return datosHabilidad;
    }

    /** Un snapshot distinto (otra referencia, mismo contenido), como el que aparece tras un parche nuevo. */
    public DatosMeta otroSnapshot() {
        return new DatosMeta(datosMeta.heroes(), new Parche(61, "7.42"), datosMeta.cargadoEn());
    }
}
