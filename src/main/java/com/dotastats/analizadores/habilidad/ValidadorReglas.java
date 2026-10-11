package com.dotastats.analizadores.habilidad;

import com.dotastats.client.OpenDotaClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Valida las reglas de habilidad contra {@code /constants/abilities}: detecta habilidades que cambiaron o
 * desaparecieron tras un parche. Nunca bloquea el arranque (la validación es solo de este endpoint, a
 * pedido); si OpenDota no responde, la excepción se propaga como siempre (503 OPENDOTA_NO_DISPONIBLE).
 */
@Service
public class ValidadorReglas {

    private final OpenDotaClient client;

    public ValidadorReglas(OpenDotaClient client) {
        this.client = client;
    }

    public List<ResultadoValidacionRegla> validar(List<ReglaHabilidad> reglas) {
        Map<String, Object> abilidades = client.obtenerAbilidades();
        List<ResultadoValidacionRegla> resultado = new ArrayList<>();
        for (ReglaHabilidad r : reglas) {
            List<String> problemas = new ArrayList<>();
            if (!abilidades.containsKey(r.habilidad())) {
                problemas.add("la habilidad '" + r.habilidad() + "' ya no existe en /constants/abilities");
            }
            for (String anula : r.anula()) {
                if (!abilidades.containsKey(anula)) {
                    problemas.add("la habilidad anulada '" + anula + "' ya no existe en /constants/abilities");
                }
            }
            resultado.add(new ResultadoValidacionRegla(r.id(), problemas.isEmpty(), problemas));
        }
        return resultado;
    }
}
