package com.dotastats.web;

import com.dotastats.client.OpenDotaClient;
import com.dotastats.service.RecomendacionService;
import com.dotastats.util.SteamIdConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Proxy directo a los endpoints de "players" de OpenDota (ver
 * https://docs.opendota.com/#tag/players). No usa API key: se llama a OpenDota
 * de forma anónima, así que aplica el límite de tasa gratuito compartido por IP.
 */
@RestController
@RequestMapping("/api/jugadores/{steamId}")
public class JugadorController {

    private final OpenDotaClient openDotaClient;
    private final RecomendacionService recomendacionService;

    public JugadorController(OpenDotaClient openDotaClient, RecomendacionService recomendacionService) {
        this.openDotaClient = openDotaClient;
        this.recomendacionService = recomendacionService;
    }

    @GetMapping
    public Object perfil(@PathVariable String steamId) {
        return recurso(steamId, "", Map.of());
    }

    @GetMapping("/wl")
    public Object winLoss(@PathVariable String steamId, @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/wl", parametros);
    }

    @GetMapping("/recentMatches")
    public Object partidasRecientes(@PathVariable String steamId) {
        return recurso(steamId, "/recentMatches", Map.of());
    }

    @GetMapping("/matches")
    public Object partidas(@PathVariable String steamId, @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/matches", parametros);
    }

    @GetMapping("/heroes")
    public Object heroes(@PathVariable String steamId, @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/heroes", parametros);
    }

    @GetMapping("/peers")
    public Object peers(@PathVariable String steamId, @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/peers", parametros);
    }

    @GetMapping("/pros")
    public Object pros(@PathVariable String steamId, @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/pros", parametros);
    }

    @GetMapping("/totals")
    public Object totales(@PathVariable String steamId, @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/totals", parametros);
    }

    @GetMapping("/counts")
    public Object counts(@PathVariable String steamId, @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/counts", parametros);
    }

    @GetMapping("/histograms/{field}")
    public Object histograma(
            @PathVariable String steamId,
            @PathVariable String field,
            @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/histograms/" + field, parametros);
    }

    @GetMapping("/wardmap")
    public Object wardmap(@PathVariable String steamId, @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/wardmap", parametros);
    }

    @GetMapping("/wordcloud")
    public Object wordcloud(@PathVariable String steamId, @RequestParam Map<String, String> parametros) {
        return recurso(steamId, "/wordcloud", parametros);
    }

    @GetMapping("/ratings")
    public Object ratings(@PathVariable String steamId) {
        return recurso(steamId, "/ratings", Map.of());
    }

    @GetMapping("/rankings")
    public Object rankings(@PathVariable String steamId) {
        return recurso(steamId, "/rankings", Map.of());
    }

    @GetMapping("/recomendaciones")
    public Object recomendaciones(@PathVariable String steamId) {
        long accountId = SteamIdConverter.toAccountId(steamId);
        return recomendacionService.generar(accountId);
    }

    @PostMapping("/refresh")
    public Object refrescar(@PathVariable String steamId) {
        long accountId = SteamIdConverter.toAccountId(steamId);
        return openDotaClient.refrescar(accountId);
    }

    private Object recurso(String steamId, String subruta, Map<String, String> parametros) {
        long accountId = SteamIdConverter.toAccountId(steamId);
        return openDotaClient.obtenerRecurso(accountId, subruta, parametros);
    }
}
