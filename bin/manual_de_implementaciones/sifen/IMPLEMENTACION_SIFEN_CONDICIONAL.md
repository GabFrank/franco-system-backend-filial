# Guía para la Creación Condicional de Beans de Configuración en Spring Boot

Este documento detalla el mecanismo utilizado para habilitar o deshabilitar la inicialización de un bean de configuración de Spring Boot basándose en una propiedad en el archivo `application.properties`. Usaremos el caso de la integración con SIFEN como ejemplo práctico.

El objetivo principal es evitar que la aplicación falle al arrancar cuando una funcionalidad está desactivada y, por lo tanto, sus configuraciones requeridas (como rutas a certificados, contraseñas, etc.) no están presentes.

## Paso 1: Centralizar las Propiedades de Configuración

El primer paso es crear una clase dedicada a contener todas las propiedades relacionadas con la funcionalidad que deseamos controlar. Esto mejora la organización y el tipado seguro.

Cree una clase y anótela con `@ConfigurationProperties`, especificando un prefijo. Esto le indica a Spring que debe mapear todas las propiedades que comiencen con ese prefijo a los campos de esta clase.

**Ejemplo: `SifenProperties.java`**

```java
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Validated
@ConfigurationProperties(prefix = "sifen")
public class SifenProperties {

    // Esta es la propiedad clave que controlará la creación del bean.
    // Le damos un valor por defecto (ej. true).
    private boolean enabled = true;

    @NotBlank
    private String ambiente = "DEV";

    @NotNull
    private Certificado certificado = new Certificado();

    @NotBlank
    private String csc;

    @NotBlank
    private String cscId;

    // ... otras propiedades ...

    @Data
    public static class Certificado {
        @NotBlank
        private String archivo;

        @NotBlank
        private String contrasena;
        
        // ... otras sub-propiedades ...
    }
}
```

## Paso 2: Crear la Clase de Configuración del Bean

A continuación, cree la clase de configuración de Spring que será responsable de construir el bean.

1.  Anote la clase con `@Configuration`.
2.  Anote la clase con `@EnableConfigurationProperties` y apunte a la clase de propiedades que creó en el paso anterior. Esto activa el mapeo de propiedades.

**Ejemplo: `SifenConfiguration.java`**

```java
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable; // Importante

import java.io.FileNotFoundException;

@Slf4j
@Configuration
@EnableConfigurationProperties(SifenProperties.class)
public class SifenConfiguration {
    
    // ... método del bean ...

}
```

## Paso 3: Implementar la Lógica Condicional en el Método del Bean

Este es el núcleo del mecanismo. Dentro del método que crea el bean (anotado con `@Bean`), debe verificar el valor de la propiedad `enabled`.

1.  Declare el método del bean para que devuelva el tipo de objeto que necesita configurar (ej. `SifenConfig`).
2.  **Importante:** Anote el método con `@Nullable` de `org.springframework.lang`. Esto le informa al contenedor de Spring que es legal que este método devuelva `null`, evitando un `BeanCreationException`.
3.  Inyecte su clase de propiedades (`SifenProperties`) como un parámetro del método. Spring la proporcionará automáticamente.
4.  Añada una condición `if` al principio del método para comprobar la propiedad `enabled`.
5.  Si `enabled` es `false`, registre un mensaje informativo y **retorne `null`**. Esto efectivamente cancela la creación y registro del bean en el contexto de Spring.
6.  Si `enabled` es `true`, proceda con la lógica normal de creación e inicialización del bean.

**Ejemplo: Método `sifenConfig`**

```java
// ... dentro de SifenConfiguration.java ...

@Bean
@Nullable // Permite que el bean sea nulo sin lanzar un error.
public SifenConfig sifenConfig(SifenProperties properties) throws SifenException, FileNotFoundException {
    // 1. Verificación de la propiedad
    if (!properties.isEnabled()) {
        log.warn("La funcionalidad SIFEN está deshabilitada (sifen.enabled=false). El bean SifenConfig no se creará.");
        return null; // 2. Se retorna null para evitar la creación del bean
    }

    // 3. Si está habilitado, se procede con la lógica de creación normal
    log.info("SIFEN está habilitado. Configurando SifenConfig...");
    
    // Lógica para validar la existencia de archivos, etc.
    if (properties.getCertificado().getArchivo() == null) {
        throw new FileNotFoundException("La ruta del certificado no puede ser nula si SIFEN está habilitado.");
    }

    try {
        SifenConfig config = new SifenConfig(...); // Crear y configurar el objeto
        // ... más configuración ...
        log.info("SifenConfig inicializado correctamente.");
        return config;
    } catch (Exception e) {
        log.error("Error fatal al inicializar la configuración de SIFEN: {}", e.getMessage(), e);
        throw new RuntimeException("Error al inicializar SifenConfig: " + e.getMessage());
    }
}
```

## Paso 4: Configurar en `application.properties`

Ahora, en su archivo `application.properties`, puede controlar la funcionalidad simplemente cambiando el valor de la propiedad `enabled`.

**Para deshabilitar:**
(No se requiere ninguna otra propiedad de `sifen` si está deshabilitado)

```properties
sifen.enabled=false
```

**Para habilitar:**
(Se deben proporcionar todas las propiedades requeridas por `SifenProperties`)

```properties
sifen.enabled=true
sifen.ambiente=PROD
sifen.cscId=001
sifen.csc=ABCD...
sifen.certificado.archivo=/ruta/al/certificado.p12
sifen.certificado.contrasena=micontrasena
```

## Conclusión

Siguiendo estos pasos, puede crear un sistema robusto donde las funcionalidades que dependen de configuraciones externas complejas pueden ser habilitadas o deshabilitadas de forma segura con una sola propiedad, sin causar que la aplicación falle al iniciarse.

