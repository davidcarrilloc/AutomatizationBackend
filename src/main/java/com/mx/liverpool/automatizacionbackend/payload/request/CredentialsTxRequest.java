package com.mx.liverpool.automatizacionbackend.payload.request;

import lombok.Data;

@Data
public class CredentialsTxRequest {
    private String user;
    private String password;
}
