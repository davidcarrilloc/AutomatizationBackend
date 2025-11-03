package com.mx.liverpool.automatizacionbackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "direcciones")
public class Direccion {
    /**
     * Entidad fuerte para tener historico de direcciones pasadas o si el sistema necesita multiples
     * direcciones p.e. facturacion, envio, casa, trabajo, etc.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String calle;

    @Column(name = "numero_exterior", nullable = false)
    private String numeroExterior;

    @Column(name = "numero_interior")
    private String numeroInterior;

    @Column(name = "codigo_postal", nullable = false, length = 5)
    private String codigoPostal;

    private String colonia;

    private String municipio;

    private String estado;

    @Column(name = "pais", nullable = false, length = 50)
    private String pais = "México";

    @OneToOne
    @JoinColumn(name = "usuario_id", foreignKey = @ForeignKey(name = "fk_direccion_usuario"))
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime fechaActualizacion;
}
