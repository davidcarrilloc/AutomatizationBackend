package com.mx.liverpool.automatizacionbackend.mapper;

import com.mx.liverpool.automatizacionbackend.model.Direccion;
import com.mx.liverpool.automatizacionbackend.model.Usuario;
import com.mx.liverpool.automatizacionbackend.payload.request.UsuarioRequest;
import com.mx.liverpool.automatizacionbackend.payload.response.DireccionResponse;
import com.mx.liverpool.automatizacionbackend.payload.response.UsuarioResponse;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {
    public UsuarioResponse fromUsuarioToResponse(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellidoPaterno(),
                usuario.getApellidoMaterno(),
                usuario.getCorreo(),
                new DireccionResponse(
                        usuario.getDireccion().getCalle(),
                        usuario.getDireccion().getNumeroExterior(),
                        usuario.getDireccion().getNumeroInterior(),
                        usuario.getDireccion().getCodigoPostal(),
                        usuario.getDireccion().getColonia(),
                        usuario.getDireccion().getMunicipio(),
                        usuario.getDireccion().getEstado(),
                        usuario.getDireccion().getPais(),
                        usuario.getDireccion().getFechaCreacion(),
                        usuario.getDireccion().getFechaActualizacion()
                ),
                usuario.getFechaCreacion(),
                usuario.getFechaActualizacion()
        );
    }

    public Usuario fromUsuarioRequestToUsuario(UsuarioRequest usuarioRequest) {
        if (usuarioRequest == null) {
            return null;
        }
        return new Usuario(
                null,
                usuarioRequest.getNombre(),
                usuarioRequest.getApellidoPaterno(),
                usuarioRequest.getApellidoMaterno(),
                usuarioRequest.getEmail(),
                new Direccion(
                        null,
                        usuarioRequest.getDireccionRequest().getCalle(),
                        usuarioRequest.getDireccionRequest().getNumeroExterior(),
                        usuarioRequest.getDireccionRequest().getNumeroInterior(),
                        usuarioRequest.getDireccionRequest().getCodigoPostal(),
                        usuarioRequest.getDireccionRequest().getColonia(),
                        null, // municipio se rellena por Copomex
                        null, // estado se rellena por Copomex
                        "México",
                        null, // el usuario es 1:1 con la direccion
                        null, // fechaCreacion se rellena por Copomex
                        null  // fechaActualizacion se rellena por Copomex
                ),
                null,
                null
        );
    }

    public void updateUsuarioFromRequest(UsuarioRequest usuarioRequest, Usuario usuarioExistente) {
        if (usuarioRequest == null || usuarioExistente == null) {
            return;
        }
        usuarioExistente.setNombre(usuarioRequest.getNombre());
        usuarioExistente.setApellidoPaterno(usuarioRequest.getApellidoPaterno());
        usuarioExistente.setApellidoMaterno(usuarioRequest.getApellidoMaterno());
        usuarioExistente.setCorreo(usuarioRequest.getEmail());

        Direccion direccion = usuarioExistente.getDireccion();
        if (direccion != null) {
            direccion.setCalle(usuarioRequest.getDireccionRequest().getCalle());
            direccion.setNumeroExterior(usuarioRequest.getDireccionRequest().getNumeroExterior());
            direccion.setNumeroInterior(usuarioRequest.getDireccionRequest().getNumeroInterior());
            direccion.setCodigoPostal(usuarioRequest.getDireccionRequest().getCodigoPostal());
            direccion.setColonia(usuarioRequest.getDireccionRequest().getColonia());
            // municipio y estado se actualizan por Copomex
        }
    }
}
