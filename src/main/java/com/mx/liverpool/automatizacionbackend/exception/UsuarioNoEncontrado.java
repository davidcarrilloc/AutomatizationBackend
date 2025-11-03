package com.mx.liverpool.automatizacionbackend.exception;

import java.io.Serial;

public class UsuarioNoEncontrado extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    public UsuarioNoEncontrado(String message) {
        super(message);
    }
}
