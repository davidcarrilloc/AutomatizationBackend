package com.mx.liverpool.automatizacionbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ExecutePythonService {
    private final String pythonPath;
    private final String projectDir;

    @Autowired
    public ExecutePythonService(@Value("${python.path}") String pythonPath,
                                @Value("${project.remision.path}") String projectDir) {
        this.pythonPath = pythonPath;
        this.projectDir = projectDir;
    }

    public String executeApvScript() {
        String module = "remisionesAPV.remisiones_kognivera";
        return executePythonScript(module);
    }

    public String executeAtgScript() {
        String module = "remisionesAPV.remisiones_kognivera";
        return executePythonScript(module);
    }

    private String executePythonScript(String module) {
        try {
            System.setProperty("user.dir", projectDir);
            ProcessBuilder processBuilder = new ProcessBuilder(pythonPath, "-m", module);
            processBuilder.directory(new File(projectDir));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            StringBuilder output = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                String outputStr = output.toString();
                if (exitCode != 0) {
                    throw new RuntimeException("Error al ejecutar el script de Python. Código de salida: " + exitCode + ". Salida: " + outputStr);
                }

                return outputStr;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al ejecutar el script de Python: " + e.getMessage(), e);
        }
    }
}
