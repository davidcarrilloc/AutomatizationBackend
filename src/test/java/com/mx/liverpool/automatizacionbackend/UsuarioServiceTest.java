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
import com.mx.liverpool.automatizacionbackend.repository.UsuarioRepository;
import com.mx.liverpool.automatizacionbackend.service.CopomexClient;
import com.mx.liverpool.automatizacionbackend.service.UsuarioService;
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
    UsuarioRepository repo;
    @Mock
    UsuarioMapper mapper;
    @Mock
    CopomexClient copomex;

    @InjectMocks
    UsuarioService servicio;

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
        when(repo.existsByCorreo(req.getEmail())).thenReturn(false);
        when(mapper.fromUsuarioRequestToUsuario(req)).thenReturn(entidad);
        when(copomex.completarDireccion(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(copomexResp));
        when(repo.save(entidad)).thenAnswer(i -> { entidad.setId(1L); return entidad; });
        when(mapper.fromUsuarioToResponse(entidad)).thenReturn(respEsperada);

        // 6) Ejecutar
        UsuarioResponse respuesta = servicio.crearUsuario(req);

        // 7) Verificar
        assertThat(respuesta).isEqualTo(respEsperada);
        verify(repo).save(entidad);
    }

    // ─── correo duplicado ───
    @Test
    void crearUsuario_lanzaExcepcionSiCorreoDuplicado() {
        Usuario dummy = new Usuario();
        dummy.setDireccion(new Direccion());

        UsuarioRequest req = UsuarioRequest.builder()
                .nombre("Juan")
                .apellidoPaterno("Pérez")
                .apellidoMaterno("Gómez")
                .email("test@mail.com")
                .direccionRequest(DireccionRequest.builder()
                        .codigoPostal("12345")
                        .colonia("Centro")
                        .calle("Principal")
                        .numeroExterior("100")
                        .build())
                .build();

        when(mapper.fromUsuarioRequestToUsuario(req)).thenReturn(dummy);
        when(repo.existsByCorreo(req.getEmail())).thenReturn(true);
        assertThatThrownBy(() -> servicio.crearUsuario(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correo ya está en uso");
    }

    // ─── Copomex no disponible (circuit breaker) ───
    @Test
    void crearUsuario_lanzaCopomexNoDisponibleSiFallback() {
        UsuarioRequest req = new UsuarioRequest();
        req.setEmail("ok@example.com");
        req.setDireccionRequest(new DireccionRequest());

        Usuario entidad = new Usuario();
        entidad.setDireccion(new Direccion());

        when(repo.existsByCorreo(anyString())).thenReturn(false);
        when(mapper.fromUsuarioRequestToUsuario(req)).thenReturn(entidad);

        when(copomex.completarDireccion(any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new CopomexNoDisponibleException("API caída")));

        assertThatThrownBy(() -> servicio.crearUsuario(req))
                .isInstanceOf(CopomexNoDisponibleException.class);
    }
}
