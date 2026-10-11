package com.dotastats.analizadores.habilidad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reglas")
public class ReglasController {

    private final RepositorioReglas repositorio;
    private final ValidadorReglas validador;

    public ReglasController(RepositorioReglas repositorio, ValidadorReglas validador) {
        this.repositorio = repositorio;
        this.validador = validador;
    }

    @GetMapping
    public List<ReglaHabilidad> listar() {
        return repositorio.todas();
    }

    @GetMapping("/validacion")
    public List<ResultadoValidacionRegla> validar() {
        return validador.validar(repositorio.todas());
    }
}
