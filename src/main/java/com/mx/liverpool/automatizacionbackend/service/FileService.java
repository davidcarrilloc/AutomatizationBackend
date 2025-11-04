package com.mx.liverpool.automatizacionbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.*;

@Service
public class FileService {
    private final String uploadPendienteApvDir;

    @Autowired
    public FileService(@Value("${project.remision.path.pendiente}") String uploadPendienteApvDir) {
        this.uploadPendienteApvDir = uploadPendienteApvDir;
    }

    public void moveFileToProcessingApvDirectory(MultipartFile file) {
        moveFileToDirectory(file, uploadPendienteApvDir);
    }

    public void moveFileToProcessingAtgDirectory(MultipartFile file) {
        moveFileToDirectory(file, uploadPendienteApvDir);
    }

    public void moveFileToDirectory(MultipartFile file, String targetDirectory) {
        try {
            Path targetPath = Paths.get(targetDirectory).resolve("input.csv");
            createDirectories(targetPath.getParent());
            file.transferTo(targetPath.toFile());
        } catch (Exception e) {
            throw new RuntimeException("Error al mover el archivo a la carpeta de procesamiento: " + e.getMessage(), e);
        }
    }
}
