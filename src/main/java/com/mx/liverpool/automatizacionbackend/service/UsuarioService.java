package com.mx.liverpool.automatizacionbackend.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import com.mx.liverpool.automatizacionbackend.exception.UsuarioNoEncontrado;
import com.mx.liverpool.automatizacionbackend.mapper.UsuarioMapper;
import com.mx.liverpool.automatizacionbackend.model.Usuario;
import com.mx.liverpool.automatizacionbackend.payload.request.UsuarioRequest;
import com.mx.liverpool.automatizacionbackend.payload.response.UsuarioResponse;
import com.mx.liverpool.automatizacionbackend.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {
    private final UsuarioRepository repo;
    private final UsuarioMapper mapper;
    private final CopomexClient copomexClient;

    public Page<UsuarioResponse> listarUsuarios(Pageable pageable, String nombre) {
        Page<Usuario> pagina = (nombre == null)
                ? repo.findAll(pageable)
                : repo.findByNombreContainingIgnoreCase(nombre, pageable);
        return pagina.map(mapper::fromUsuarioToResponse);
    }

    public UsuarioResponse obtenerUsuarioPorId(String id) {
        Long usuarioId = Long.parseLong(id);
        return mapper.fromUsuarioToResponse(repo.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontrado("Usuario no encontrado con ID: " + id)));
    }

    public UsuarioResponse crearUsuario(UsuarioRequest usuarioRequest) {
        Usuario nuevoUsuario = mapper.fromUsuarioRequestToUsuario(usuarioRequest);
        nuevoUsuario.getDireccion().setUsuario(nuevoUsuario); // establecer la relación 1:1

        boolean foundEmail = repo.existsByCorreo(usuarioRequest.getEmail());
        if (foundEmail) throw new IllegalArgumentException("El correo ya está en uso: " + usuarioRequest.getEmail());

        // Llamar a copomex
        var responseFromCopomex = copomexClient.completarDireccion(
                nuevoUsuario.getDireccion().getCodigoPostal(),
                nuevoUsuario.getDireccion().getColonia(),
                nuevoUsuario.getDireccion().getCalle(),
                nuevoUsuario.getDireccion().getNumeroExterior(),
                nuevoUsuario.getDireccion().getNumeroInterior()
        ).block(); // Bloqueamos para esperar la respuesta


        if (responseFromCopomex == null) throw new RuntimeException("No se pudo completar la dirección con Copomex");
        nuevoUsuario.getDireccion().setColonia(responseFromCopomex.getColonia());
        nuevoUsuario.getDireccion().setMunicipio(responseFromCopomex.getMunicipio());
        nuevoUsuario.getDireccion().setEstado(responseFromCopomex.getEstado());

        nuevoUsuario = repo.save(nuevoUsuario);
        return mapper.fromUsuarioToResponse(nuevoUsuario);
    }

    public UsuarioResponse actualizarUsuario(String id, UsuarioRequest usuarioRequest) {
        Long usuarioId = Long.parseLong(id);
        Usuario usuarioExistente = repo.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontrado("Usuario no encontrado con ID: " + id));

        mapper.updateUsuarioFromRequest(usuarioRequest, usuarioExistente);
        usuarioExistente.getDireccion().setUsuario(usuarioExistente); // establecer la relación 1:1
        boolean foundEmail = repo.existsByCorreo(usuarioRequest.getEmail());
        if (foundEmail && !usuarioExistente.getCorreo().equals(usuarioRequest.getEmail()))
            throw new IllegalArgumentException("El correo ya está en uso: " + usuarioRequest.getEmail());

        var responseFromCopomex = copomexClient.completarDireccion(
                usuarioExistente.getDireccion().getCodigoPostal(),
                usuarioExistente.getDireccion().getColonia(),
                usuarioExistente.getDireccion().getCalle(),
                usuarioExistente.getDireccion().getNumeroExterior(),
                usuarioExistente.getDireccion().getNumeroInterior()
        ).block();

        if (responseFromCopomex == null) throw new RuntimeException("No se pudo completar la dirección con Copomex");
        usuarioExistente.getDireccion().setColonia(responseFromCopomex.getColonia());
        usuarioExistente.getDireccion().setMunicipio(responseFromCopomex.getMunicipio());
        usuarioExistente.getDireccion().setEstado(responseFromCopomex.getEstado());
        usuarioExistente = repo.save(usuarioExistente);
        return mapper.fromUsuarioToResponse(usuarioExistente);
    }

    public Map<String, String> eliminarUsuario(String id) {
        Long usuarioId = Long.parseLong(id);
        Usuario usuario = repo.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontrado("Usuario no encontrado con ID: " + id));
        repo.delete(usuario);
        return Map.of("mensaje", "Usuario eliminado correctamente");
    }
}
