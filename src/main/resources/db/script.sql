/* ---------- schema.sql ---------- */
CREATE USER IF NOT EXISTS first_user@'%'
  IDENTIFIED BY 'pass';

DROP DATABASE IF EXISTS usuarios;
CREATE DATABASE IF NOT EXISTS usuarios
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE usuarios;

-- Tabla principal
CREATE TABLE usuarios (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre            VARCHAR(60)  NOT NULL,
    apellido_paterno  VARCHAR(60)  NOT NULL,
    apellido_materno  VARCHAR(60)  NULL,
    correo            VARCHAR(120) NOT NULL UNIQUE,
    fecha_creacion    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Dirección física (una por usuario)
CREATE TABLE direcciones (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL,
    calle           VARCHAR(100) NOT NULL,
    numero_exterior VARCHAR(10)  NOT NULL,
    numero_interior VARCHAR(10)  NULL,
    codigo_postal   CHAR(5)      NOT NULL,
    colonia         VARCHAR(100) NOT NULL,
    municipio       VARCHAR(100) NOT NULL,
    estado          VARCHAR(100) NOT NULL,
    pais            VARCHAR(60)  NOT NULL DEFAULT 'México',
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_direccion_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
            ON DELETE CASCADE
);

-- Índice para acelerar búsquedas por CP
CREATE INDEX idx_direcciones_cp ON direcciones (codigo_postal);

/* ---------- data.sql (ejemplo opcional) ---------- */
INSERT INTO usuarios (nombre, apellido_paterno, apellido_materno, correo)
VALUES ('Juan', 'Pérez', 'López', 'juan.perez@ejemplo.com');

INSERT INTO direcciones (usuario_id, calle, numero_exterior, codigo_postal, colonia, municipio, estado)
VALUES (1, 'Av. Paseo de la Reforma', '123', '06600', 'Juárez', 'Cuauhtémoc', 'Ciudad de México');

GRANT ALL PRIVILEGES ON usuarios.* TO 'lacomer' WITH GRANT OPTION;
FLUSH PRIVILEGES;