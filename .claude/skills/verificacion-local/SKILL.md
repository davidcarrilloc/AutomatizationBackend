---
name: verificacion-local
description: Úsala cuando tengas que probar lógica de este backend sin arrancar la aplicación — extracción, validación, parseo, cualquier método con ramas o ciclos. Arrancar la app abre 4 datasources Oracle que no responden desde la máquina de desarrollo, así que la prueba por Swagger casi nunca es opción. Cubre el build con JDK 23 (el JAVA_HOME global apunta a JDK 11), cómo armar el classpath desde .m2 sin caer en jars de 2010, y el harness de asserts en el scratchpad.
---

# Verificar sin arrancar la app

El proyecto **no tiene tests automatizados** y arrancarlo abre cuatro datasources Oracle
(`172.17.212.224`, `172.16.212.22`, `172.17.212.7`) que no responden desde la máquina de desarrollo.
Eso deja la prueba por Swagger fuera del alcance casi siempre. Lo que sí funciona: compilar y
ejecutar el servicio directamente contra `target/classes`.

## 1. Compilar

```bash
JAVA_HOME="C:/Program Files/Java/jdk-23" ./mvnw -o -q clean package -DskipTests
```

- El **`JAVA_HOME` global apunta a JDK 11** y el proyecto necesita 21+. Sin el prefijo, el build
  truena con errores de versión que no tienen nada que ver con tu cambio.
- `-o` (offline) evita esperar a un repositorio remoto que puede no estar accesible.
- `-q` deja solo los errores: si no imprime nada, compiló.

## 2. Armar el classpath

`mvn dependency:build-classpath` **no funciona offline** aquí (`NoPluginFoundForPrefixException`).
Se resuelven los jars a mano desde el repositorio local:

```bash
M2="C:/Users/David Carrillo/.m2/repository"
ls "$M2/com/fasterxml/jackson/core/jackson-databind"/*/*.jar | grep -v -e sources -e javadoc | sort -V | tail -1
```

Dos trampas, ambas cuestan media hora:

1. **El path lleva un espacio** (`David Carrillo`). Cítalo siempre. `~/.m2` **no** funciona en Git
   Bash por eso mismo.
2. **Usa `sort -V | tail -1`, nunca `head -1`.** El repositorio guarda todas las versiones que
   alguna vez se descargaron: `head -1` trae `spring-core 2.0.8` de 2010 y revienta con
   `NoClassDefFoundError: org/springframework/util/MultiValueMap`, un error que parece de tu código
   y no lo es.

### Jars mínimos

Para tocar un `@Service` cualquiera:

| Jar | Por qué |
|---|---|
| `jackson-databind`, `jackson-core`, `jackson-annotations` | `ObjectMapper`, `JsonNode` |
| `spring-core` | `Resource`, `FileSystemResource` (parámetros de constructor) |
| `spring-jcl` | lo pide `spring-core` |
| `log4j-api` | el `@Log4j2` de Lombok genera un `Logger` estático |

Si el servicio construye un `WebClient`, agrega además `spring-web`, `spring-webflux`,
`reactor-core`, `micrometer-observation` y `micrometer-commons` — el último es el que falta más
seguido (`NoClassDefFoundError: io/micrometer/observation/ObservationRegistry`).

**No** necesitas `lombok`: los getters ya están compilados dentro de `target/classes`.
El `ERROR Log4j API could not find a logging provider` en la salida es inofensivo (falta
`log4j-core`) y no afecta las aserciones.

## 3. El harness

Va en el **scratchpad**, nunca en el repo, y **en el mismo paquete que el servicio** — así alcanza
también los métodos package-private (por eso `AvailabilityService.extraerStock` y otros se dejan sin
`private`: para poder verificarlos).

```java
package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class VerificacionLoQueSea {

    static MiServicio servicio = new MiServicio(new ObjectMapper(), /* ...lo que pida el constructor */);

    static void ok(String caso, boolean condicion) {
        if (!condicion) throw new AssertionError("FALLO: " + caso);
        System.out.println("OK  " + caso);
    }

    public static void main(String[] args) {
        var resultado = servicio.metodoAProbar("entrada");
        ok("el caso feliz devuelve 1 registro", resultado.size() == 1);
        ok("entrada vacía no truena", servicio.metodoAProbar("").isEmpty());

        System.out.println("\nTodos los casos pasaron.");
    }
}
```

`ok(...)` en vez de `assert` porque `assert` está desactivado salvo que corras con `-ea`, y es fácil
creer que pasó una prueba que ni se ejecutó.

Compilar y correr:

```bash
SP="<ruta del scratchpad>"
CP="<los jars separados por ; en Windows>"
cd "C:/Users/David Carrillo/Documents/Kv/automatizacion-backend"
"C:/Program Files/Java/jdk-23/bin/javac.exe" -nowarn -d "$SP/out" -cp "target/classes;$CP" "$SP/VerificacionLoQueSea.java"
"C:/Program Files/Java/jdk-23/bin/java.exe" -cp "$SP/out;target/classes;$CP" com.mx.liverpool.automatizacionbackend.service.VerificacionLoQueSea
```

En Windows el separador de classpath es **`;`**, no `:`.

## 4. Qué verificar

Un caso por rama, no uno por método. Lo que sí vale la pena:

- el caso feliz del ejemplo que traiga el SPEC, con los valores exactos que el spec espera;
- que los datos **no se crucen** cuando hay varios (3 elementos → cada uno con su par correcto: es
  el error que un solo caso feliz nunca detecta);
- cada rama de error que escribas (entrada inválida, campo faltante, lista vacía);
- lo que el SPEC afirme como resultado esperado, tal cual, con ese valor.

## 5. Qué reportar

Lo que se probó así **es** verificación y va en el Cierre del SPEC. Lo que quedó pendiente también:
la prueba de extremo a extremo por Swagger se reporta como pendiente con su razón (los datasources),
igual que en `SPEC_VALIDATOR.md`, `SPEC_AVAILABILITY.md` y `SPEC_VALIDADOR_MARKETPLACE.md`. Nunca la
des por hecha.
