FROM openjdk:21-jdk-slim

# Instalar dependencias
RUN apt-get update && apt-get install -y maven

# Copiar el archivo pom.xml y el código fuente
WORKDIR /app
COPY pom.xml /app/
COPY src /app/src

# Construir el proyecto
RUN mvn clean package -DskipTests
EXPOSE 8080

# Ejecutar
CMD ["java", "-jar", "target/LaComerExamenTecnico-0.0.1-SNAPSHOT.jar"]
