package com.mx.liverpool.automatizacionbackend.exception;

import java.io.Serial;

public class CopomexNoDisponibleException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    public CopomexNoDisponibleException(String message) {
        super(message);
    }
}
