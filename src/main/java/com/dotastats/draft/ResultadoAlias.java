package com.dotastats.draft;

import java.util.List;

/**
 * Resultado de intentar resolver el texto que escribió el usuario a un héroe del catálogo.
 * Según el tipo, solo algunos campos tienen sentido: heroId/nombre para RESUELTO,
 * candidatos para AMBIGUO.
 */
public record ResultadoAlias(Tipo tipo, Integer heroId, String nombre, List<String> candidatos) {

    public enum Tipo { RESUELTO, AMBIGUO, NO_ENCONTRADO }

    public static ResultadoAlias resuelto(int heroId, String nombre) {
        return new ResultadoAlias(Tipo.RESUELTO, heroId, nombre, List.of());
    }

    public static ResultadoAlias ambiguo(List<String> candidatos) {
        return new ResultadoAlias(Tipo.AMBIGUO, null, null, List.copyOf(candidatos));
    }

    public static ResultadoAlias noEncontrado() {
        return new ResultadoAlias(Tipo.NO_ENCONTRADO, null, null, List.of());
    }
}
