package com.dotastats.analizadores;

import com.dotastats.draft.MotorSugerencias;
import com.dotastats.draft.ResultadoSugerencias;
import com.dotastats.draft.Sugerencia;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fila "Sugerencias por meta": envuelve al {@link MotorSugerencias} existente (meta de OpenDota, duelos,
 * línea e historial personal) sin cambiarle el puntaje ni el motivo. Las advertencias que genere ese motor
 * (perfil no disponible, matchups fallidos, sin datos de línea) se trasladan a {@link ContextoDraft#advertencias()}
 * para que lleguen igual que antes a la respuesta final.
 */
@Component
public class AnalizadorMeta implements AnalizadorDraft {

    private final MotorSugerencias motor;

    public AnalizadorMeta(MotorSugerencias motor) {
        this.motor = motor;
    }

    @Override
    public String id() {
        return "meta";
    }

    @Override
    public String titulo() {
        return "Sugerencias por meta";
    }

    @Override
    public int orden() {
        return 1;
    }

    @Override
    public List<Sugerencia> sugerir(ContextoDraft ctx) {
        ResultadoSugerencias resultado = ctx.accountId() != null
                ? motor.sugerir(ctx.miRol(), ctx.enemigosPorSlot(), ctx.aliados(), ctx.accountId())
                : motor.sugerir(ctx.miRol(), ctx.enemigosPorSlot(), ctx.aliados());
        ctx.advertencias().addAll(resultado.advertencias());
        return resultado.sugerencias();
    }
}
