package com.mx.liverpool.automatizacionbackend.exception;

import java.io.Serial;

public class TxDiffException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public TxDiffException(String message) {
        super(message);
    }
}
