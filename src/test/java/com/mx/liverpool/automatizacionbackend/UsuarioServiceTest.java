package com.mx.liverpool.automatizacionbackend;

import com.mx.liverpool.automatizacionbackend.exception.CopomexNoDisponibleException;
import com.mx.liverpool.automatizacionbackend.mapper.UsuarioMapper;
import com.mx.liverpool.automatizacionbackend.model.Direccion;
import com.mx.liverpool.automatizacionbackend.model.Usuario;
import com.mx.liverpool.automatizacionbackend.payload.request.DireccionRequest;
import com.mx.liverpool.automatizacionbackend.payload.request.UsuarioRequest;
import com.mx.liverpool.automatizacionbackend.payload.response.DireccionCopomexResponse;
import com.mx.liverpool.automatizacionbackend.payload.response.DireccionResponse;
import com.mx.liverpool.automatizacionbackend.payload.response.UsuarioResponse;
import com.mx.liverpool.automatizacionbackend.service.CopomexClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock
    UsuarioMapper mapper;
    @Mock
    CopomexClient copomex;
    // ───────────────────────── test: crear usuario OK ──────────────────────────
    @Test
    void crearUsuario_devuelveResponseCuandoTodoEsCorrecto() {
        // 1) Construir el request de forma explícita
        UsuarioRequest req = new UsuarioRequest();
        req.setNombre("María Fernanda");
        req.setApellidoPaterno("López");
        req.setApellidoMaterno("Cruz");
        req.setEmail("m.fernanda@example.com");

        DireccionRequest dirReq = new DireccionRequest();
        dirReq.setCodigoPostal("06600");
        dirReq.setColonia("Juárez");
        dirReq.setCalle("Reforma");
        dirReq.setNumeroExterior("135");
        dirReq.setNumeroInterior("3B");
        req.setDireccionRequest(dirReq);

        // 2) Entidad que se supone guardará el repositorio
        Usuario entidad = new Usuario();
        Direccion dirEntidad = new Direccion();
        entidad.setDireccion(dirEntidad);
        entidad.setCorreo(req.getEmail());

        // 3) Respuesta de Copomex stub
        DireccionCopomexResponse copomexResp = DireccionCopomexResponse.builder()
                .codigoPostal("06600")
                .colonia("Juárez")
                .municipio("Cuauhtémoc")
                .estado("Ciudad de México")
                .ciudad("Ciudad de México")
                .calle("Reforma")
                .numeroExterior("135")
                .numeroInterior("3B")
                .build();

        // 4) DTO de salida esperado
        UsuarioResponse respEsperada = UsuarioResponse.builder()
                .id(1L)
                .nombre(req.getNombre())
                .correo(req.getEmail())
                .direccion(DireccionResponse.builder()
                        .codigoPostal(copomexResp.getCodigoPostal())
                        .colonia(copomexResp.getColonia())
                        .municipio(copomexResp.getMunicipio())
                        .estado(copomexResp.getEstado())
                        .pais("México")
                        .calle(copomexResp.getCalle())
                        .numeroExterior(copomexResp.getNumeroExterior())
                        .numeroInterior(copomexResp.getNumeroInterior())
                        .build())
                .build();

        // 5) Stubbing de dependencias
        when(mapper.fromUsuarioRequestToUsuario(req)).thenReturn(entidad);
        when(copomex.completarDireccion(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(copomexResp));
        when(mapper.fromUsuarioToResponse(entidad)).thenReturn(respEsperada);
   }
}
