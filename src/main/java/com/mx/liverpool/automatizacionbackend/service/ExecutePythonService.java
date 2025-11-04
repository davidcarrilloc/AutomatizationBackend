package com.mx.liverpool.automatizacionbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
        String scriptPath = projectDir + "/remisiones_kognivera.py";
        return executePythonScript(scriptPath);
    }

    public String executeAtgScript() {
        String scriptPath = projectDir + "/remisiones_kognivera.py";
        return executePythonScript(scriptPath);
    }

    private String executePythonScript(String scriptPath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(pythonPath, scriptPath);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Error al ejecutar el script de Python. Código de salida: " + exitCode);
            }

            StringBuilder output = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                return output.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al ejecutar el script de Python: " + e.getMessage(), e);
        }
    }
}
