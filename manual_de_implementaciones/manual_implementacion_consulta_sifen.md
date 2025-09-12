# Manual de Implementación: Integración SIFEN con rshk-jsifenlib

Este documento es una guía detallada para replicar la integración del servicio de consulta RUC de SIFEN utilizando la librería `rshk-jsifenlib`.

## ✅ Checklist de Implementación

- [ ] **FASE 1: Limpieza**: Código y configuración antiguos eliminados.
- [ ] **FASE 2: Dependencias y Configuración**: `pom.xml` y archivos de propiedades actualizados.
- [ ] **FASE 3: Configuración Spring**: Clases `SifenProperties` y `SifenConfiguration` creadas para inicializar la librería.
- [ ] **FASE 4: DTOs y Modelos**: DTOs de respuesta creados y el modelo de dominio actualizado.
- [ ] **FASE 5: Capa de Servicio**: `SifenService` implementado como wrapper.
- [ ] **FASE 6: Pruebas e Integración**: Método `main` para pruebas locales añadido e integración con `ClienteService` y `ClienteGraphQL` completada.

---

## 📖 Guía Detallada de Replicación

### **FASE 1: LIMPIEZA**
Antes de comenzar, asegúrate de eliminar cualquier implementación manual existente para la consulta de RUC.
- **Acción**: Elimina la clase de servicio anterior (ej: `SifenConsultaRucService.java`).
- **Acción**: Elimina las propiedades de configuración relacionadas del `application.properties`.
- **Acción**: Comenta o elimina la inyección del servicio antiguo en las clases consumidoras (ej: `ClienteService`).

### **FASE 2: DEPENDENCIAS Y CONFIGURACIÓN**

#### **2.1 Dependencia Maven (`pom.xml`)**
Añade la librería `rshk-jsifenlib` a las dependencias de tu proyecto.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.roshka.sifen</groupId>
    <artifactId>rshk-jsifenlib</artifactId>
    <version>0.2.4</version>
</dependency>
```

#### **2.2 Estructura de Paquetes**
Crea una estructura de paquetes dedicada para mantener el código de SIFEN organizado.

```
/src/main/java/com/franco/dev/service/
└── sifen/
    ├── config/      <- Clases de configuración de Spring
    ├── dto/         <- Data Transfer Objects
    │   └── response/
    └── service/     <- La clase de servicio wrapper
```

#### **2.3 Archivos de Propiedades**

**`application.properties`**: Define la configuración base y utiliza variables de entorno para los secretos.

```properties
# SIFEN Configuration - Using rshk-jsifenlib
sifen.enabled=true
sifen.ambiente=DEV
sifen.certificado.usar=true
sifen.certificado.tipo=PFX
sifen.certificado.archivo=${SIFEN_CERT_PATH:/ruta/por/defecto/certificado.pfx}
sifen.certificado.contrasena=${SIFEN_CERT_PASSWORD:changeit}
sifen.csc=${SIFEN_CSC:D37561586c1CAd69A2e7747E73f9F03B}
sifen.csc-id=${SIFEN_CSC_ID:0001}
sifen.habilitar.nota.tecnica.13=false
```

**`application-dev.properties`**: Sobrescribe las propiedades para el entorno de desarrollo local.

```properties
# Development Overrides for SIFEN
# Reemplaza con la ruta y contraseña de tu certificado PFX local.
sifen.certificado.archivo=/Users/gabfranck/workspace/frc-sistemas-informaticos/backend/filial/frc-filial-server/certificados/certificado.pfx
sifen.certificado.contrasena=fasa1701
```

### **FASE 3: CONFIGURACIÓN SPRING**

#### **3.1 `SifenProperties.java`**
Esta clase mapea las propiedades del archivo `.properties` a un objeto Java fuertemente tipado.

```java
// /service/sifen/config/SifenProperties.java
package com.franco.dev.service.sifen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "sifen")
public class SifenProperties {
    private boolean enabled = true;
    @NotBlank
    private String ambiente = "DEV";
    @NotNull
    private Certificado certificado = new Certificado();
    @NotBlank
    private String csc;
    @NotBlank
    private String cscId;
    private boolean habilitarNotaTecnica13 = false;

    @Data
    public static class Certificado {
        private boolean usar = true;
        @NotBlank
        private String tipo = "PFX";
        @NotBlank
        private String archivo;
        @NotBlank
        private String contrasena;
    }
}
```

#### **3.2 `SifenConfiguration.java`**
Esta clase utiliza `SifenProperties` para configurar e inicializar la librería SIFEN al arrancar la aplicación. Incluye una validación crítica para asegurar que el archivo del certificado exista.

```java
// /service/sifen/config/SifenConfiguration.java
package com.franco.dev.service.sifen.config;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.config.SifenConfig;
import com.roshka.sifen.core.types.TipoAmbiente;
import com.roshka.sifen.core.types.TipoCertificadoCliente;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Configuration
@EnableConfigurationProperties(SifenProperties.class)
@ConditionalOnProperty(name = "sifen.enabled", havingValue = "true", matchIfMissing = true)
public class SifenConfiguration {

    @Bean
    public SifenConfig sifenConfig(SifenProperties properties) throws FileNotFoundException {
        String certPath = properties.getCertificado().getArchivo();
        if (Files.notExists(Paths.get(certPath))) {
            log.error("****************************************************************************");
            log.error("ARCHIVO DE CERTIFICADO SIFEN NO ENCONTRADO EN: {}", certPath);
            log.error("****************************************************************************");
            throw new FileNotFoundException("No se encontró el archivo de certificado SIFEN: " + certPath);
        }

        log.info("Inicializando configuración de SIFEN para el ambiente: {}", properties.getAmbiente());
        SifenConfig config = new SifenConfig(
            TipoAmbiente.valueOf(properties.getAmbiente().toUpperCase()),
            properties.getCscId(),
            properties.getCsc(),
            TipoCertificadoCliente.valueOf(properties.getCertificado().getTipo().toUpperCase()),
            certPath,
            properties.getCertificado().getContrasena()
        );
        config.setHabilitarNotaTecnica13(properties.isHabilitarNotaTecnica13());
        Sifen.setSifenConfig(config);
        log.info("Configuración de SIFEN inicializada correctamente.");
        return config;
    }
}
```

### **FASE 4: DTOS Y MODELOS**

#### **4.1 `SifenResponseBase.java`**
Clase base abstracta para estandarizar todas las respuestas de SIFEN.

```java
// /service/sifen/dto/response/SifenResponseBase.java
package com.franco.dev.service.sifen.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public abstract class SifenResponseBase {
    private boolean procesamientoCorrecto;
    private String codigoRespuesta;
    private String mensajeRespuesta;
    private LocalDateTime timestamp = LocalDateTime.now();
}
```

#### **4.2 DTO del Servicio: `ConsultaRucResponse.java`**
Este DTO representa la respuesta de la consulta RUC dentro de nuestra capa de servicio.

```java
// /service/sifen/dto/response/ConsultaRucResponse.java
package com.franco.dev.service.sifen.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConsultaRucResponse extends SifenResponseBase {
    private String ruc;
    private String razonSocial;
    private String estadoContribuyente;
    private String codigoEstadoContribuyente;
    private String esFacturadorElectronico;
}
```

### **FASE 5: CAPA DE SERVICIO**

#### **5.1 `SifenService.java`**
El corazón de la implementación. Encapsula las llamadas a la librería, maneja la lógica de negocio (limpieza de RUC), mapea las respuestas y gestiona los errores.

```java
// /service/sifen/service/SifenService.java
package com.franco.dev.service.sifen.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.service.sifen.dto.response.ConsultaRucResponse;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.config.SifenConfig;
import com.roshka.sifen.core.beans.response.RespuestaConsultaRUC;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SifenService {

    public ConsultaRucResponse consultaRuc(String ruc) {
        try {
            String rucSinDv = cleanRuc(ruc);
            log.info("Consultando RUC: {} (enviado como: {})", ruc, rucSinDv);
            RespuestaConsultaRUC respuestaSifen = Sifen.consultaRUC(rucSinDv);
            return mapToConsultaRucResponse(respuestaSifen, ruc);
        } catch (SifenException e) {
            log.error("Error al consultar RUC en SIFEN: {}", e.getMessage(), e);
            return createErrorResponse(e);
        }
    }

    private String cleanRuc(String ruc) {
        if (ruc == null) return "";
        String cleanRuc = ruc.replaceAll("[^0-9]", "");
        if (cleanRuc.length() > 1) {
            return cleanRuc.substring(0, cleanRuc.length() - 1);
        }
        return cleanRuc;
    }

    private ConsultaRucResponse mapToConsultaRucResponse(RespuestaConsultaRUC r, String rucOriginal) {
        ConsultaRucResponse dto = new ConsultaRucResponse();
        dto.setProcesamientoCorrecto("0502".equals(r.getdCodRes()));
        dto.setCodigoRespuesta(r.getdCodRes());
        dto.setMensajeRespuesta(r.getdMsgRes());
        dto.setRuc(rucOriginal);
        if (r.getxContRUC() != null) {
            dto.setRazonSocial(r.getxContRUC().getdRazCons());
            dto.setEstadoContribuyente(r.getxContRUC().getdDesEstCons());
            dto.setCodigoEstadoContribuyente(r.getxContRUC().getdCodEstCons());
            dto.setEsFacturadorElectronico(r.getxContRUC().getdRUCFactElec());
        }
        return dto;
    }

    private ConsultaRucResponse createErrorResponse(SifenException e) {
        ConsultaRucResponse dto = new ConsultaRucResponse();
        dto.setProcesamientoCorrecto(false);
        dto.setCodigoRespuesta("999");
        dto.setMensajeRespuesta("Error de comunicación con SIFEN: " + e.getMessage());
        return dto;
    }
    
    // ... (método main de prueba) ...
}
```

### **FASE 6: PRUEBAS E INTEGRACIÓN**

#### **6.1 `ClienteService.java`**
Se inyecta `SifenService` y se delega la llamada.

```java
// /service/personas/ClienteService.java
// ...
import com.franco.dev.service.sifen.service.SifenService;
// ...

@Service
@AllArgsConstructor
public class ClienteService extends CrudService<Cliente, ClienteRepository> {
    // ...
    private final SifenService sifenService;

    public com.franco.dev.service.sifen.dto.response.ConsultaRucResponse consultaRuc(String ruc) {
        return sifenService.consultaRuc(ruc);
    }
    // ...
}
```

#### **6.2 `ClienteGraphQL.java`**
El resolver de GraphQL se adapta para consumir el nuevo servicio. Utiliza `ModelMapper` para convertir el DTO de servicio al DTO de dominio que espera el esquema GraphQL, manteniendo la compatibilidad.

```java
// /graphql/personas/ClienteGraphQL.java
// ...
import com.franco.dev.domain.personas.ConsultaRucResponse;
import org.modelmapper.ModelMapper;
// ...

@Component
public class ClienteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {
    // ...
    @Autowired
    private ClienteService service;

    public ConsultaRucResponse consultaRuc(String ruc) {
        com.franco.dev.service.sifen.dto.response.ConsultaRucResponse sifenResponse = service.consultaRuc(ruc);
        ModelMapper m = new ModelMapper();
        // Mapea del DTO de servicio al DTO de dominio
        return m.map(sifenResponse, ConsultaRucResponse.class);
    }
    // ...
}
```

#### **6.3 Prueba con Método `main`**
El método `main` en `SifenService` permite una validación rápida y aislada.

```java
// En SifenService.java
public static void main(String[] args) {
    try {
        System.out.println("Iniciando prueba de consulta RUC SIFEN...");
        SifenConfig config = SifenConfig.cargarConfiguracion("src/main/resources/application-dev.properties");
        Sifen.setSifenConfig(config);
        System.out.println("Configuración cargada correctamente.");

        SifenService service = new SifenService();
        String rucDePrueba = "4043581-4";
        ConsultaRucResponse respuesta = service.consultaRuc(rucDePrueba);

        ObjectMapper objectMapper = new ObjectMapper();
        System.out.println("Respuesta mapeada (JSON): " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(respuesta));
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```
