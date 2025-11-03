package com.mx.liverpool.automatizacionbackend.exception;

import java.io.Serial;

public class CorreoExisteException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CorreoExisteException(String correo) {
        super("El correo electrónico '" + correo + "' ya está en uso.");
    }
}
