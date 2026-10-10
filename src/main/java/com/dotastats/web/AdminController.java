package com.dotastats.web;

import com.dotastats.meta.DatosMetaService;
import com.dotastats.meta.EstadoMeta;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DatosMetaService servicio;

    public AdminController(DatosMetaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refrescar() {
        boolean iniciada = servicio.refrescar();
        String mensaje = iniciada
                ? "Caché vaciada; recarga de /heroStats y parche iniciada en segundo plano, sigue el avance en GET /api/admin/estado"
                : "Ya hay una carga en curso, no se inicia otra";
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RefreshResponse(iniciada, mensaje));
    }

    @GetMapping("/estado")
    public EstadoMeta estado() {
        return servicio.estado();
    }

    public record RefreshResponse(boolean iniciada, String mensaje) {
    }
}
