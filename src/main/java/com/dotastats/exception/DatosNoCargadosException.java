package com.dotastats.exception;

public class DatosNoCargadosException extends RuntimeException {
    public DatosNoCargadosException() {
        super("Los datos de meta aún no están cargados; consulta GET /api/admin/estado e intenta de nuevo en un momento");
    }
}
