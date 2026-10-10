package com.dotastats.service;

import com.dotastats.client.OpenDotaClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecomendacionService {

    private static final int PARTIDAS_MIN_PARA_SEÑALAR_HEROE_DEBIL = 50;
    private static final int PARTIDAS_MIN_PARA_SEÑALAR_HEROE_FUERTE = 30;
    private static final double DIFERENCIA_WINRATE_DEBIL = 3.0;
    private static final double DIFERENCIA_WINRATE_FUERTE = 5.0;

    private final OpenDotaClient openDotaClient;

    public RecomendacionService(OpenDotaClient openDotaClient) {
        this.openDotaClient = openDotaClient;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generar(long accountId) {
        Map<String, Object> wl = (Map<String, Object>) openDotaClient.obtenerRecurso(accountId, "/wl", Map.of());
        List<Map<String, Object>> heroes =
                (List<Map<String, Object>>) openDotaClient.obtenerRecurso(accountId, "/heroes", Map.of());
        List<Map<String, Object>> totales =
                (List<Map<String, Object>>) openDotaClient.obtenerRecurso(accountId, "/totals", Map.of());

        int ganadas = numero(wl.get("win")).intValue();
        int perdidas = numero(wl.get("lose")).intValue();
        int totalPartidas = ganadas + perdidas;
        double winRateGeneral = totalPartidas > 0 ? (ganadas * 100.0) / totalPartidas : 0.0;

        List<String> recomendaciones = new ArrayList<>();
        recomendaciones.addAll(recomendacionesPorHeroe(heroes, winRateGeneral));
        recomendaciones.addAll(recomendacionesPorStats(promediosPorPartida(totales)));

        if (recomendaciones.isEmpty()) {
            recomendaciones.add("Tus estadísticas están parejas, no hay ninguna señal fuerte de mejora puntual.");
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("account_id", accountId);
        resultado.put("partidas_totales", totalPartidas);
        resultado.put("winrate_general", redondear(winRateGeneral));
        resultado.put("recomendaciones", recomendaciones);
        return resultado;
    }

    private List<String> recomendacionesPorHeroe(List<Map<String, Object>> heroes, double winRateGeneral) {
        Map<Integer, String> nombresHeroes = nombresHeroes();
        List<String> recomendaciones = new ArrayList<>();

        List<Map<String, Object>> jugados = heroes.stream()
                .filter(h -> numero(h.get("games")).intValue() > 0)
                .sorted(Comparator.comparingInt((Map<String, Object> h) -> numero(h.get("games")).intValue()).reversed())
                .toList();

        jugados.stream()
                .filter(h -> numero(h.get("games")).intValue() >= PARTIDAS_MIN_PARA_SEÑALAR_HEROE_DEBIL)
                .filter(h -> winRateDe(h) < winRateGeneral - DIFERENCIA_WINRATE_DEBIL)
                .sorted(Comparator.comparingDouble(
                        (Map<String, Object> h) -> numero(h.get("games")).intValue() * (winRateGeneral - winRateDe(h)))
                        .reversed())
                .limit(3)
                .forEach(h -> recomendaciones.add(String.format(
                        "%s es uno de tus héroes más jugados (%d partidas) pero tu winrate ahí es %.1f%%, "
                                + "por debajo de tu promedio general (%.1f%%). Considera bajarle prioridad o revisar tu build/timing.",
                        nombreDe(nombresHeroes, h), numero(h.get("games")).intValue(), winRateDe(h), winRateGeneral)));

        jugados.stream()
                .filter(h -> numero(h.get("games")).intValue() >= PARTIDAS_MIN_PARA_SEÑALAR_HEROE_FUERTE)
                .filter(h -> winRateDe(h) >= winRateGeneral + DIFERENCIA_WINRATE_FUERTE)
                .sorted(Comparator.comparingDouble(this::winRateDe).reversed())
                .limit(2)
                .forEach(h -> recomendaciones.add(String.format(
                        "%s tiene un winrate de %.1f%% en %d partidas, muy por encima de tu promedio. Priorízalo más en el pick.",
                        nombreDe(nombresHeroes, h), winRateDe(h), numero(h.get("games")).intValue())));

        return recomendaciones;
    }

    private List<String> recomendacionesPorStats(Map<String, Double> stats) {
        List<String> recomendaciones = new ArrayList<>();

        Double kills = stats.get("kills");
        Double deaths = stats.get("deaths");
        if (kills != null && deaths != null && deaths > kills * 0.7) {
            recomendaciones.add(String.format(
                    "Mueres en promedio %.1f veces por partida contra %.1f kills. Cuida el posicionamiento en peleas, sobre todo en early/mid game.",
                    deaths, kills));
        }

        Double laneEff = stats.get("lane_efficiency_pct");
        if (laneEff != null && laneEff < 65) {
            recomendaciones.add(String.format(
                    "Tu eficiencia de línea promedio es %.1f%%. Apunta a 70%%+ maximizando última hits y evitando gastar tiempo fuera de la línea sin motivo.",
                    laneEff));
        }

        Double wardsObs = stats.get("purchase_ward_observer");
        if (wardsObs != null && wardsObs < 3) {
            recomendaciones.add(String.format(
                    "Compras en promedio %.1f wards de observador por partida. Aumentar la visión reduce mucho los ganks, sobre todo jugando roles de mid/carry.",
                    wardsObs));
        }

        Double throwVal = stats.get("throw");
        Double comebackVal = stats.get("comeback");
        if (throwVal != null && comebackVal != null && throwVal > comebackVal) {
            recomendaciones.add(
                    "Regalas más ventaja de la que recuperas (throw > comeback). Cuando vayas ganando claramente, evita jugadas de alto riesgo innecesarias.");
        }

        return recomendaciones;
    }

    private Map<String, Double> promediosPorPartida(List<Map<String, Object>> totales) {
        Map<String, Double> resultado = new HashMap<>();
        for (Map<String, Object> t : totales) {
            String campo = (String) t.get("field");
            double n = numero(t.get("n")).doubleValue();
            double suma = numero(t.get("sum")).doubleValue();
            if (n > 0) {
                resultado.put(campo, suma / n);
            }
        }
        return resultado;
    }

    private Map<Integer, String> nombresHeroes() {
        Map<Integer, String> nombres = new HashMap<>();
        for (Map<String, Object> h : openDotaClient.obtenerCatalogoHeroes()) {
            nombres.put(numero(h.get("id")).intValue(), (String) h.get("localized_name"));
        }
        return nombres;
    }

    private String nombreDe(Map<Integer, String> nombresHeroes, Map<String, Object> heroe) {
        int heroId = numero(heroe.get("hero_id")).intValue();
        return nombresHeroes.getOrDefault(heroId, "héroe #" + heroId);
    }

    private double winRateDe(Map<String, Object> heroe) {
        int juegos = numero(heroe.get("games")).intValue();
        int victorias = numero(heroe.get("win")).intValue();
        return juegos > 0 ? (victorias * 100.0) / juegos : 0.0;
    }

    private double redondear(double valor) {
        return Math.round(valor * 10) / 10.0;
    }

    private Number numero(Object valor) {
        return valor instanceof Number n ? n : 0;
    }
}
