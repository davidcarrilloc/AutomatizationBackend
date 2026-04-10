package com.mx.liverpool.automatizacionbackend.exception;

import java.io.Serial;

public class TxNotFound extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    public TxNotFound(String message) {
        super(message);
    }
}
