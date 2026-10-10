package com.dotastats.draft;

public class HeroeDuplicadoException extends RuntimeException {

    public HeroeDuplicadoException(String nombreHeroe) {
        super("El héroe " + nombreHeroe + " aparece más de una vez en el draft");
    }
}
