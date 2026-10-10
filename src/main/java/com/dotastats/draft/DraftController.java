package com.dotastats.draft;

import com.dotastats.util.SteamIdConverter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/draft")
public class DraftController {

    private final HeroeAliasResolver resolver;
    private final MotorSugerencias motorSugerencias;

    public DraftController(HeroeAliasResolver resolver, MotorSugerencias motorSugerencias) {
        this.resolver = resolver;
        this.motorSugerencias = motorSugerencias;
    }

    @GetMapping("/roles")
    public List<Rol> roles() {
        return Rol.TODOS;
    }

    @PostMapping("/suggest")
    public DraftResponse sugerir(@Valid @RequestBody DraftRequest request) {
        Set<Integer> yaElegidos = new HashSet<>();
        Map<Integer, Integer> idsEnemigos = new TreeMap<>();
        Map<Integer, Integer> idsAliados = new TreeMap<>();

        Clasificacion enemigos = clasificar(request.enemigos(), idsEnemigos, yaElegidos);
        Clasificacion aliados = clasificar(request.aliados(), idsAliados, yaElegidos);

        List<Integer> enemigosElegidos = new ArrayList<>(idsEnemigos.values());
        List<Integer> aliadosElegidos = new ArrayList<>(idsAliados.values());
        ResultadoSugerencias resultado = request.personaliza()
                // un steamId no numérico lanza IllegalArgumentException -> 400 STEAM_ID_INVALIDO
                ? motorSugerencias.sugerir(request.miRol(), enemigosElegidos, aliadosElegidos,
                        SteamIdConverter.toAccountId(request.steamId().trim()))
                : motorSugerencias.sugerir(request.miRol(), enemigosElegidos, aliadosElegidos);

        return new DraftResponse(
                request.miRol(),
                new Equipos<>(enemigos.resueltos, aliados.resueltos),
                new Equipos<>(enemigos.ambiguos, aliados.ambiguos),
                new Equipos<>(enemigos.noEncontrados, aliados.noEncontrados),
                resultado.sugerencias(),
                resultado.advertencias());
    }

    private Clasificacion clasificar(Map<Integer, String> slots, Map<Integer, Integer> ids, Set<Integer> yaElegidos) {
        Clasificacion resultado = new Clasificacion();
        // TreeMap para que la respuesta salga siempre ordenada por slot
        new TreeMap<>(slots).forEach((slot, entrada) -> {
            if (entrada == null) {
                return;
            }
            ResultadoAlias r = resolver.resolver(entrada);
            switch (r.tipo()) {
                case RESUELTO -> {
                    if (!yaElegidos.add(r.heroId())) {
                        throw new HeroeDuplicadoException(r.nombre());
                    }
                    ids.put(slot, r.heroId());
                    resultado.resueltos.put(slot, r.nombre());
                }
                case AMBIGUO -> resultado.ambiguos.put(slot, r.candidatos());
                case NO_ENCONTRADO -> resultado.noEncontrados.put(slot, entrada);
            }
        });
        return resultado;
    }

    private static class Clasificacion {
        final Map<Integer, String> resueltos = new TreeMap<>();
        final Map<Integer, List<String>> ambiguos = new TreeMap<>();
        final Map<Integer, String> noEncontrados = new TreeMap<>();
    }
}
