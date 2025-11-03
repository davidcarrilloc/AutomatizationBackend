package com.mx.liverpool.automatizacionbackend.exception;

import java.io.Serial;

public class ColoniaNoEncontradaException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    public ColoniaNoEncontradaException(String message) {
        super(message);
    }
}
