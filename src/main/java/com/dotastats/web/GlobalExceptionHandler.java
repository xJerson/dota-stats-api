package com.dotastats.web;

import com.dotastats.draft.HeroeDuplicadoException;
import com.dotastats.exception.DatosNoCargadosException;
import com.dotastats.exception.JugadorNoEncontradoException;
import com.dotastats.exception.OpenDotaNoDisponibleException;
import com.dotastats.exception.PerfilPrivadoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> manejarSteamIdInvalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("STEAM_ID_INVALIDO", e.getMessage()));
    }

    @ExceptionHandler(JugadorNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarJugadorNoEncontrado(JugadorNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("JUGADOR_NO_ENCONTRADO", e.getMessage()));
    }

    /**
     * 422 y no 403/404: la petición es válida y el recurso existe, pero no se puede procesar porque los datos del
     * jugador no son públicos. 403 sugeriría un problema de autenticación con esta API; 404 se reserva para jugador inexistente.
     */
    @ExceptionHandler(PerfilPrivadoException.class)
    public ResponseEntity<ErrorResponse> manejarPerfilPrivado(PerfilPrivadoException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("PERFIL_PRIVADO", e.getMessage()));
    }

    @ExceptionHandler(OpenDotaNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> manejarOpenDotaNoDisponible(OpenDotaNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("OPENDOTA_NO_DISPONIBLE", e.getMessage()));
    }

    @ExceptionHandler(DatosNoCargadosException.class)
    public ResponseEntity<ErrorResponse> manejarDatosNoCargados(DatosNoCargadosException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("DATOS_NO_CARGADOS", e.getMessage()));
    }

    @ExceptionHandler(HeroeDuplicadoException.class)
    public ResponseEntity<ErrorResponse> manejarHeroeDuplicado(HeroeDuplicadoException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("HEROE_DUPLICADO", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarSolicitudInvalida(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("SOLICITUD_INVALIDA", detalle));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarCuerpoIlegible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("SOLICITUD_INVALIDA", "El cuerpo de la petición no es un JSON válido"));
    }
}
