# Manual de Implementación de Facturación Electrónica SIFEN

Este documento detalla la implementación del sistema de facturación electrónica integrado con SIFEN (Sistema Integrado de Facturación Electrónica Nacional) de Paraguay. Está diseñado para ser interpretado por un agente de IA para replicar la funcionalidad en un nuevo proyecto.

## 1. Estructura del Proyecto y Dependencias

La implementación se organiza en los siguientes paquetes principales:

-   `com.franco.dev.domain.financiero`: Contiene las entidades JPA que mapean las tablas de la base de datos y los enums relacionados.
-   `com.franco.dev.service.financiero`: Contiene los servicios de lógica de negocio para gestionar las entidades (actúan como repositorios).
-   `com.franco.dev.service.sifen`: Contiene la lógica de negocio principal para la interacción con SIFEN.
-   `com.franco.dev.service.sifen.config`: Clases de configuración para inicializar la librería SIFEN.
-   `com.franco.dev.service.sifen.util`: Clases de utilidad para parsing de XML, manejo de datos del receptor, etc.
-   `com.franco.dev.utilitarios`: Clases de utilidad generales, como el `PostgreSQLEnumType` personalizado.

### 1.1. Dependencia de la Librería SIFEN

El proyecto utiliza un fork personalizado de la librería `rshk-jsifenlib`. Es crucial incluir la siguiente dependencia y repositorio en el archivo `pom.xml`.

Para que Maven pueda descargar tu lib desde GitHub Packages, tenés que agregar las credenciales en el registry de Maven (el ~/.m2/settings.xml) y usar el mismo id que pusiste en el <repository>. Acá va, paso a paso:

1) Crear el token en GitHub

En GitHub: Settings → Developer settings → Personal access tokens.

Creá un Personal Access Token (classic o fine-grained) con scope read:packages (solo lectura).

Copiá el token (se muestra una sola vez).

Nota: GitHub Packages para Maven siempre requiere autenticación, incluso para repos públicos.

2) Configurar ~/.m2/settings.xml

En tu máquina de desarrollo (Linux/Mac: ~/.m2/settings.xml; Windows: %USERPROFILE%\.m2\settings.xml) agregá un <server> cuyo id sea exactamente github (igual al del <repository> que ya tenés en el pom.xml):

<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <server>
      <id>github</id>
      <!-- Tu usuario de GitHub (owner de la cuenta) -->
      <username>GabFrank</username>
      <!-- El PAT que creaste con read:packages -->
      <password>GH_PAT_XXXXXXXXXXXX</password>
    </server>
  </servers>

  <!-- Opcional pero recomendado: usar variables de entorno -->
  <!--
  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
  -->
</settings>

**Dependencia:**
```xml
<dependency>
    <groupId>io.github.gabfrank</groupId>
    <artifactId>jsifenlib</artifactId>
    <version>0.2.4-frc.13</version>
</dependency>
```

**Repositorio:**
Se necesita configurar un repositorio de Maven que apunte a GitHub Packages.
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/GabFrank/rshk-jsifenlib</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

**Nota:** Para que Maven pueda descargar desde este repositorio de GitHub, se requieren credenciales de autenticación. Estas deben configurarse en el archivo `settings.xml` de Maven del entorno de desarrollo/despliegue.

## 2. Configuración de la Aplicación

La configuración de la conexión con SIFEN se gestiona a través de la clase `SifenConfiguration` y se alimenta de propiedades definidas en `application.properties` (o variables de entorno).

### 2.1. Propiedades de Configuración (`SifenProperties.java`)

Se debe crear una clase para mapear las propiedades de SIFEN.

```java
package com.franco.dev.service.sifen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Validated
@ConfigurationProperties(prefix = "sifen")
public class SifenProperties {

    private boolean enabled = true;

    @NotBlank
    private String ambiente = "DEV"; // Puede ser DEV o PROD

    @NotNull
    private Certificado certificado = new Certificado();

    @NotBlank
    private String csc; // Código de Seguridad del Contribuyente

    @NotBlank
    private String cscId; // ID del CSC

    private boolean habilitarNotaTecnica13 = false;

    @Data
    public static class Certificado {
        private boolean usar = true;

        @NotBlank
        private String tipo = "PFX"; // Tipo de certificado, ej: PFX

        @NotBlank
        private String archivo; // Ruta al archivo del certificado digital

        @NotBlank
        private String contrasena; // Contraseña del certificado
    }
}
```

### 2.2. Archivo `application.properties`

Un ejemplo de configuración en `application.properties`:
```properties
sifen.enabled=true
sifen.ambiente=DEV
sifen.cscId=001
sifen.csc=A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4
sifen.certificado.tipo=PFX
sifen.certificado.archivo=/ruta/a/certificado.p12
sifen.certificado.contrasena=micontrasena
sifen.habilitarNotaTecnica13=true
```

### 2.3. Clase de Configuración (`SifenConfiguration.java`)

Esta clase `Bean` inicializa la configuración global de la librería SIFEN al arrancar la aplicación.

```java
package com.franco.dev.service.sifen.config;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.SifenConfig.TipoAmbiente;
import com.roshka.sifen.core.SifenConfig.TipoCertificadoCliente;
import com.roshka.sifen.core.exceptions.SifenException;

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
        String certPass = properties.getCertificado().getContrasena();


        if (Files.notExists(Paths.get(certPath))) {
            log.error("ARCHIVO DE CERTIFICADO SIFEN NO ENCONTRADO EN: {}", certPath);
            log.error("La aplicación no se iniciará. Verifique la ruta en `application.properties` o la variable de entorno `SIFEN_CERT_PATH`.");
            throw new FileNotFoundException("No se encontró el archivo de certificado SIFEN: " + certPath);
        }


        SifenConfig config = new SifenConfig(
            TipoAmbiente.valueOf(properties.getAmbiente().toUpperCase()),
            properties.getCscId(),
            properties.getCsc(),
            TipoCertificadoCliente.valueOf(properties.getCertificado().getTipo().toUpperCase()),
            certPath,
            properties.getCertificado().getContrasena()
        );

        config.setHabilitarNotaTecnica13(properties.isHabilitarNotaTecnica13());

        // Inicializa la configuración global de la librería
        try {
            Sifen.setSifenConfig(config);
        } catch (SifenException e) {
            log.error("Error al inicializar la configuración de SIFEN: {}", e.getMessage());
            e.printStackTrace();
        }
        return config;
    }
}
```

## 3. Base de Datos y Enums

La persistencia de los datos de facturación electrónica se realiza en un esquema `financiero`. A continuación se detallan las tablas, sus columnas y los `enums` de PostgreSQL asociados.

### 3.1. Tipo Enum Personalizado para PostgreSQL

Para mapear los `enums` de Java a tipos `enum` nativos de PostgreSQL, se utiliza una clase `PostgreSQLEnumType` personalizada que extiende `org.hibernate.type.EnumType`.

**`PostgreSQLEnumType.java`**
```java
package com.franco.dev.utilitarios;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLEnumType extends org.hibernate.type.EnumType {

    public void nullSafeSet(
            PreparedStatement st,
            Object value,
            int index,
            SharedSessionContractImplementor session)
            throws HibernateException, SQLException {
        st.setObject(
                index,
                value != null ?
                        ((Enum) value).name() :
                        null,
                Types.OTHER
        );
    }
}
```
Esta clase debe ser referenciada en las entidades que usan enums con la anotación `@TypeDef`.

### 3.2. Enums

#### `EstadoDE.java`
Define los estados por los que puede pasar un Documento Electrónico.
```java
package com.franco.dev.domain.financiero.enums;

public enum EstadoDE {
    PENDIENTE, // Creado y listo para ser incluido en un lote.
    EN_LOTE,   // Ha sido asignado a un lote para su envío.
    APROBADO,  // Confirmado por SIFEN como válido.
    RECHAZADO, // Rechazado por SIFEN.
    CANCELADO  // Anulado por el usuario.
}
```
**Tipo PostgreSQL (financiero.estado_de_enum):** `CREATE TYPE financiero.estado_de_enum AS ENUM ('PENDIENTE', 'EN_LOTE', 'APROBADO', 'RECHAZADO', 'CANCELADO');`

#### `EstadoLoteDE.java`
Define los estados de un lote de Documentos Electrónicos.
```java
package com.franco.dev.domain.financiero.enums;

public enum EstadoLoteDE {
    PENDIENTE_ENVIO,      // Creado, con DEs asignados, pero aún no enviado a SIFEN.
    EN_PROCESO,           // Lote enviado y recibido exitosamente por SIFEN, esperando el resultado.
    PROCESADO,            // Se ha consultado el resultado del lote, y todos los DEs dentro de él han sido actualizados.
    PROCESADO_CON_ERRORES,// Se consultó el resultado, pero hubo inconsistencias.
    ERROR_ENVIO,          // Falló el envío del lote por un problema de comunicación. Se reintentará.
    ERROR_RED,            // Error de conectividad/red. No se reintenta hasta que se restablezca la conexión.
    ERROR_PERMANENTE,     // El lote superó el número de reintentos y requiere intervención manual.
    RECHAZADO             // El lote fue rechazado en el envío inicial.
}
```
**Tipo PostgreSQL (financiero.estado_lote_de_enum):** `CREATE TYPE financiero.estado_lote_de_enum AS ENUM ('PENDIENTE_ENVIO', 'EN_PROCESO', 'PROCESADO', 'PROCESADO_CON_ERRORES', 'ERROR_ENVIO', 'ERROR_RED', 'ERROR_PERMANENTE', 'RECHAZADO');`

#### `EstadoEvento.java`
Define los estados para los eventos de SIFEN (Cancelación, Nominación, etc.).
```java
package com.franco.dev.domain.financiero.enums;

public enum EstadoEvento {
    PENDIENTE,          // Evento enviado, esperando procesamiento
    APROBADO,           // Evento aprobado por SIFEN
    RECHAZADO,          // Evento rechazado por SIFEN
    ERROR_ENVIO         // Error al enviar el evento
}
```
**Tipo PostgreSQL (financiero.estado_evento_enum):** `CREATE TYPE financiero.estado_evento_enum AS ENUM ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'ERROR_ENVIO');`


### 3.3. Esquema de Tablas

#### Tabla `financiero.documento_electronico`
Almacena cada Documento Electrónico (DE) generado.

| column_name               | data_type                   | is_nullable | column_default                                           |
| ------------------------- | --------------------------- | ----------- | -------------------------------------------------------- |
| id                        | bigint                      | NO          | nextval('financiero.documento_electronico_id_seq')       |
| sucursal_id               | bigint                      | NO          |                                                          |
| factura_legal_id          | bigint                      | NO          |                                                          |
| cdc                       | character varying(50)       | YES         |                                                          |
| url_qr                    | character varying(500)      | YES         |                                                          |
| xml_firmado               | text                        | YES         |                                                          |
| xml_original              | text                        | YES         |                                                          |
| codigo_respuesta_sifen    | character varying(10)       | YES         |                                                          |
| mensaje_respuesta_sifen   | character varying(500)      | YES         |                                                          |
| numero_documento          | character varying(20)       | YES         |                                                          |
| tipo_documento            | character varying(20)       | YES         |                                                          |
| fecha_emision             | timestamp without time zone | YES         |                                                          |
| fecha_recepcion_sifen     | timestamp without time zone | YES         |                                                          |
| activo                    | boolean                     | YES         | true                                                     |
| creado_en                 | timestamp without time zone | YES         | CURRENT_TIMESTAMP                                        |
| actualizado_en            | timestamp without time zone | YES         | CURRENT_TIMESTAMP                                        |
| usuario_id                | bigint                      | YES         |                                                          |
| lote_de_id                | bigint                      | YES         |                                                          |
| estado                    | estado_de_enum              | NO          | 'PENDIENTE'::financiero.estado_de_enum                   |


#### Tabla `financiero.lote_de`
Agrupa los Documentos Electrónicos para ser enviados a SIFEN.

| column_name          | data_type                   | is_nullable | column_default                          |
| -------------------- | --------------------------- | ----------- | --------------------------------------- |
| id                   | bigint                      | NO          | nextval('financiero.lote_de_id_seq')    |
| estado               | character varying(255)      | NO          |                                         |
| fecha_procesado      | timestamp without time zone | YES         |                                         |
| fecha_ultimo_intento | timestamp without time zone | YES         |                                         |
| intentos             | integer                     | YES         | 0                                       |
| respuesta_sifen      | text                        | YES         |                                         |
| protocolo            | character varying(255)      | YES         |                                         |
| creado_en            | timestamp without time zone | YES         | CURRENT_TIMESTAMP                       |
| actualizado_en       | timestamp without time zone | YES         | CURRENT_TIMESTAMP                       |
| usuario_id           | bigint                      | YES         |                                         |
| aprobado             | boolean                     | YES         |                                         |


#### Tabla `financiero.evento_cancelacion_de`
Registra los eventos de cancelación de un Documento Electrónico.

| column_name              | data_type                   | is_nullable | column_default                                       |
| ------------------------ | --------------------------- | ----------- | ---------------------------------------------------- |
| id                       | bigint                      | NO          | nextval('financiero.evento_cancelacion_de_id_seq')   |
| documento_electronico_id | bigint                      | NO          |                                                      |
| evento_id                | character varying(255)      | NO          |                                                      |
| fecha_firma              | timestamp without time zone | NO          |                                                      |
| cdc_documento            | character varying(44)       | NO          |                                                      |
| motivo_cancelacion       | text                        | YES         |                                                      |
| xml_evento               | text                        | YES         |                                                      |
| estado                   | estado_evento_enum          | NO          | 'PENDIENTE'::financiero.estado_evento_enum           |
| fecha_procesamiento      | timestamp without time zone | YES         |                                                      |
| protocolo_autorizacion   | character varying(100)      | YES         |                                                      |
| codigo_respuesta         | character varying(10)       | YES         |                                                      |
| mensaje_respuesta        | text                        | YES         |                                                      |
| respuesta_bruta          | text                        | YES         |                                                      |
| activo                   | boolean                     | YES         | true                                                 |
| creado_en                | timestamp without time zone | YES         | CURRENT_TIMESTAMP                                    |
| actualizado_en           | timestamp without time zone | YES         | CURRENT_TIMESTAMP                                    |
| usuario_id               | bigint                      | YES         |                                                      |


#### Tabla `financiero.evento_nominacion_de`
Registra los eventos de nominación para identificar al receptor de un DE innominado.

| column_name              | data_type                   | is_nullable | column_default                                     |
| ------------------------ | --------------------------- | ----------- | -------------------------------------------------- |
| id                       | bigint                      | NO          | nextval('financiero.evento_nominacion_de_id_seq')  |
| documento_electronico_id | bigint                      | NO          |                                                    |
| evento_id                | character varying(50)       | YES         |                                                    |
| fecha_firma              | timestamp without time zone | YES         |                                                    |
| cdc_documento            | character varying(50)       | YES         |                                                    |
| cliente_id               | bigint                      | YES         |                                                    |
| nombre_receptor          | character varying(255)      | YES         |                                                    |
| documento_receptor       | character varying(50)       | YES         |                                                    |
| tipo_receptor            | character varying(50)       | YES         |                                                    |
| total_factura            | numeric                     | YES         |                                                    |
| fecha_emision            | timestamp without time zone | YES         |                                                    |
| fecha_recepcion          | timestamp without time zone | YES         |                                                    |
| xml_evento               | text                        | YES         |                                                    |
| estado                   | estado_evento_enum          | YES         |                                                    |
| fecha_procesamiento      | timestamp without time zone | YES         |                                                    |
| protocolo_autorizacion   | character varying(50)       | YES         |                                                    |
| codigo_respuesta         | character varying(10)       | YES         |                                                    |
| mensaje_respuesta        | text                        | YES         |                                                    |
| respuesta_bruta          | text                        | YES         |                                                    |
| activo                   | boolean                     | YES         | true                                               |
| creado_en                | timestamp without time zone | YES         | CURRENT_TIMESTAMP                                  |
| actualizado_en           | timestamp without time zone | YES         | CURRENT_TIMESTAMP                                  |
| usuario_id               | bigint                      | YES         |                                                    |

## 4. Entidades Principales del Dominio
A continuación se describen las clases de entidad que modelan los objetos de negocio.

### 4.1. `DocumentoElectronico.java`
Representa un único Documento Electrónico (DE), como una factura. Contiene toda la información necesaria para generar el XML, así como los datos de respuesta de SIFEN.

```java
package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(
        name = "estado_de_enum",
        typeClass = PostgreSQLEnumType.class
)
@Entity
@Table(name = "documento_electronico", schema = "financiero")
public class DocumentoElectronico implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sucursalId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_legal_id", nullable = false, unique = true)
    private FacturaLegal facturaLegal;

    // Información del documento electrónico
    private String cdc;
    private String urlQr;
    @Column(columnDefinition = "TEXT")
    private String xmlFirmado;
    @Column(columnDefinition = "TEXT")
    private String xmlOriginal;
    
    // Estado del documento
    @Enumerated(EnumType.STRING)
    @Type(type = "estado_de_enum")
    @Column(columnDefinition = "financiero.estado_de_enum")
    private EstadoDE estado;
    private String codigoRespuestaSifen;
    private String mensajeRespuestaSifen;
    
    // Información adicional
    private String numeroDocumento;
    private String tipoDocumento;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaRecepcionSifen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_de_id")
    private LoteDE loteDe;
    
    // Campos de auditoría
    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
```

### 4.2. `LoteDE.java`
Representa un lote que agrupa varios `DocumentoElectronico` para su envío conjunto a SIFEN.

```java
package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lote_de", schema = "financiero")
public class LoteDE implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private EstadoLoteDE estado;

    private LocalDateTime fechaProcesado;
    private LocalDateTime fechaUltimoIntento;
    private Integer intentos;

    @Column(columnDefinition = "TEXT")
    private String respuestaSifen;
    private String protocolo;

    @OneToMany(mappedBy = "loteDe", fetch = FetchType.LAZY)
    private List<DocumentoElectronico> documentosElectronicos;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
```

### 4.3. `EventoCancelacionDE.java`
Modela un evento de cancelación de un DE. Almacena tanto la solicitud de cancelación como la respuesta de SIFEN.

```java
package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(
        name = "estado_evento_enum",
        typeClass = PostgreSQLEnumType.class
)
@Entity
@Table(name = "evento_cancelacion_de", schema = "financiero")
public class EventoCancelacionDE implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con el documento electrónico cancelado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_electronico_id", nullable = false)
    private DocumentoElectronico documentoElectronico;

    // Datos del evento de cancelación enviado
    private String eventoId;           // ID único del evento
    private LocalDateTime fechaFirma;   // Fecha de firma del evento
    private String cdcDocumento;        // CDC del documento que se está cancelando
    private String motivoCancelacion;   // Motivo de la cancelación
    
    @Column(columnDefinition = "TEXT")
    private String xmlEvento;           // XML del evento enviado

    // Respuesta de SIFEN
    @Enumerated(EnumType.STRING)
    @Type(type = "estado_evento_enum")
    @Column(columnDefinition = "financiero.estado_evento_enum")
    private EstadoEvento estado;        // PENDIENTE, APROBADO, RECHAZADO
    
    private LocalDateTime fechaProcesamiento; // Fecha de procesamiento por SIFEN
    private String protocoloAutorizacion;     // Protocolo de autorización de SIFEN
    private String codigoRespuesta;           // Código de respuesta (ej: 0600)
    private String mensajeRespuesta;          // Mensaje de respuesta
    
    @Column(columnDefinition = "TEXT")
    private String respuestaBruta;      // Respuesta completa de SIFEN (XML)

    // Auditoría
    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
```

### 4.4. `EventoNominacionDE.java`
Modela un evento de nominación de receptor, utilizado para identificar al comprador de una factura emitida como "innominada".

```java
package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(
        name = "estado_evento_enum",
        typeClass = PostgreSQLEnumType.class
)
@Entity
@Table(name = "evento_nominacion_de", schema = "financiero")
public class EventoNominacionDE implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con el documento electrónico nominado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_electronico_id", nullable = false)
    private DocumentoElectronico documentoElectronico;

    // Datos del evento de nominación enviado
    private String eventoId;           // ID único del evento
    private LocalDateTime fechaFirma;   // Fecha de firma del evento
    private String cdcDocumento;        // CDC del documento que se está nominando
    
    // Datos del receptor nominado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;            // Cliente nominado como receptor
    
    private String nombreReceptor;      // Nombre del receptor nominado
    private String documentoReceptor;   // Documento del receptor nominado
    private String tipoReceptor;        // CONTRIBUYENTE / NO_CONTRIBUYENTE
    
    private BigDecimal totalFactura;    // Total de la factura nominada
    private LocalDateTime fechaEmision; // Fecha de emisión de la factura
    private LocalDateTime fechaRecepcion; // Fecha de recepción por el receptor
    
    @Column(columnDefinition = "TEXT")
    private String xmlEvento;           // XML del evento enviado

    // Respuesta de SIFEN
    @Enumerated(EnumType.STRING)
    @Type(type = "estado_evento_enum")
    @Column(columnDefinition = "financiero.estado_evento_enum")
    private EstadoEvento estado;        // PENDIENTE, APROBADO, RECHAZADO
    
    private LocalDateTime fechaProcesamiento; // Fecha de procesamiento por SIFEN
    private String protocoloAutorizacion;     // Protocolo de autorización de SIFEN
    private String codigoRespuesta;           // Código de respuesta (ej: 0600)
    private String mensajeRespuesta;          // Mensaje de respuesta
    
    @Column(columnDefinition = "TEXT")
    private String respuestaBruta;      // Respuesta completa de SIFEN (XML)

    // Auditoría
    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
```

## 5. Clases de Utilidad

El paquete `com.franco.dev.service.sifen.util` contiene clases helper cruciales para la interacción con SIFEN.

### 5.1. `SifenReceptorHelper.java`
Esta es una de las clases más importantes. Se encarga de aplicar la compleja lógica de negocio definida por SIFEN para determinar el tipo de receptor (B2B, B2C, B2G, B2F) y configurar correctamente los datos del comprador en el XML.

**Responsabilidades:**
-   Clasificar a los clientes como contribuyentes o no contribuyentes.
-   Manejar casos de facturas innominadas y validar el monto máximo permitido.
-   Determinar el tipo de operación (`iTiOpe`).
-   Configurar el tipo y número de documento del receptor.
-   Identificar entidades gubernamentales.
-   Calcular el dígito verificador (DV) del RUC.

**`SifenReceptorHelper.java`**
```java
package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.utilitarios.CalcularVerificadorRuc;
import com.roshka.sifen.core.types.TiNatRec;
import com.roshka.sifen.core.types.TiTiOpe;
import com.roshka.sifen.core.types.TiTipCont;
import com.roshka.sifen.core.types.TiTipDocRec;
import com.roshka.sifen.core.types.PaisType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SifenReceptorHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(SifenReceptorHelper.class);
    
    public static class ConfiguracionReceptor {
        // ... (contenido de la clase interna)
    }
    
    public static ConfiguracionReceptor determinarConfiguracionReceptor(Cliente cliente, Double montoTotal) {
        // ... (lógica principal)
    }
    
    // ... (métodos privados de lógica)
}
```

### 5.2. `SifenXmlParser.java`
Proporciona métodos para extraer información directamente de las respuestas XML de SIFEN sin necesidad de un parseador completo. Es útil para obtener rápidamente valores como el CDC, la URL del QR, el estado de un documento, etc.

**Responsabilidades:**
-   Extraer el valor de un tag XML.
-   Extraer la URL del QR (`dCarQR`).
-   Extraer el CDC (`Id`).
-   Extraer el `DigestValue`.

**`SifenXmlParser.java`**
```java
package com.franco.dev.service.sifen.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SifenXmlParser {
    
    private static final Logger logger = LoggerFactory.getLogger(SifenXmlParser.class);

    public static String extractTagValue(String xml, String tagName) {
        // ...
    }

    public static String extractUrlQr(String xml) {
        return extractTagValue(xml, "dCarQR");
    }
    
    // ... (otros métodos de extracción)
}
```

### 5.3. `SifenEventoParser.java`
Clase especializada en parsear los bloques de "eventos asociados" que vienen en las respuestas de consulta de un DE. Cuando se consulta un DE, SIFEN puede devolver información sobre eventos de cancelación o nominación que se le hayan aplicado.

**Responsabilidades:**
-   Buscar y extraer eventos de cancelación (`rGeVeCan`) del XML de respuesta.
-   Buscar y extraer eventos de nominación (`rGeVeNotRec`).
-   Mapear los datos del XML a DTOs (`EventoCancelacion`, `EventoNominacion`).

**`SifenEventoParser.java`**
```java
package com.franco.dev.service.sifen.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
public class SifenEventoParser {

    public static List<EventoCancelacion> extraerEventosCancelacion(String xmlRespuesta) {
        // ...
    }
    
    public static List<EventoNominacion> extraerEventosNominacion(String xmlRespuesta) {
        // ...
    }

    @Data
    public static class EventoCancelacion {
        // ... (DTO con campos del evento)
    }
    
    @Data
    public static class EventoNominacion {
        // ... (DTO con campos del evento)
    }
}
```

### 5.4. `CodigosGeograficos.java`
Esta clase contiene enums que mapean los códigos de departamentos y distritos de Paraguay, utilizados al generar el DE.

**Propósito:**
-   Proporcionar una correspondencia entre los nombres de departamentos/distritos y sus códigos numéricos oficiales requeridos por SIFEN.

**`CodigosGeograficos.java` (extracto)**
```java
package com.franco.dev.service.sifen.util;

public class CodigosGeograficos {

    public enum Departamento {
        CAPITAL(1),
        CONCEPCION(2),
        SAN_PEDRO(3),
        // ... resto de departamentos
    }

    public enum Distrito {
        ASUNCION_DISTRITO(1),
        CONCEPCION_MUNICIPIO(2),
        // ... resto de distritos
    }
}
```

## 6. Servicios Principales de SIFEN

La lógica de negocio para interactuar con SIFEN se concentra en dos servicios principales: `SifenService` para la gestión de documentos y lotes, y `SifenEventoService` para la gestión de eventos.

### 6.1. `SifenService.java`
Este servicio es el núcleo de la integración. Orquesta la creación, envío y consulta de Documentos Electrónicos y lotes.

**Responsabilidades Clave:**
-   **`crearDocumentoElectronico(FacturaLegal factura)`**:
    -   Toma una `FacturaLegal` y sus items.
    -   Genera el objeto `DocumentoElectronico` de SIFEN.
    -   Calcula el CDC.
    -   Genera el XML original y lo almacena en la entidad `DocumentoElectronico`. Esto es **crítico** para poder reenviar o consultar el DE de forma idéntica a como se generó originalmente.
    -   Extrae la URL del QR.
    -   Guarda la entidad `DocumentoElectronico` en la base de datos con estado `PENDIENTE`.

-   **`crearLote()` y `vincularDocumentosALote(...)`**:
    -   Gestionan la creación de un `LoteDE` y la asignación de `DocumentoElectronico` pendientes a dicho lote, cambiando su estado a `EN_LOTE`.

-   **`enviarLote(LoteDE lote)`**:
    -   Recupera los DEs del lote.
    -   **Reconstruye** los objetos `DocumentoElectronico` de la librería SIFEN a partir del `xmlOriginal` guardado en la base de datos.
    -   Envía el lote a SIFEN (`Sifen.recepcionLoteDE`).
    -   Actualiza el estado del lote a `EN_PROCESO` si el envío es exitoso (código 0300) o a `ERROR_ENVIO` si falla.
    -   Guarda el protocolo de consulta devuelto por SIFEN.

-   **`consultarLote(LoteDE lote)`**:
    -   Consulta el estado de un lote previamente enviado usando su protocolo.
    -   Procesa la respuesta para actualizar el estado del lote (`PROCESADO`, `RECHAZADO`, etc.).
    -   Analiza la respuesta XML para obtener el estado individual de cada DE dentro del lote y actualiza las entidades `DocumentoElectronico` correspondientes a `APROBADO` o `RECHAZADO`.

-   **`consultarDE(String cdc)`**:
    -   Consulta el estado de un único DE usando su CDC.
    -   Implementa una lógica de **reintentos con backoff exponencial** para manejar errores intermitentes de SIFEN.
    -   Actualiza el estado del DE en la base de datos (`APROBADO`, `RECHAZADO`).
    -   Utiliza `SifenEventoParser` para buscar y procesar eventos asociados (como cancelaciones o nominaciones) que puedan haber ocurrido, asegurando que el estado final del DE sea consistente (ej. `CANCELADO`).

**`SifenService.java`**
```java
// Contenido de SifenService.java
package com.franco.dev.service.sifen.service;
// ... (imports)

@Slf4j
@Service
public class SifenService {

    // ... (inyección de dependencias)

    @Transactional
    public com.franco.dev.domain.financiero.DocumentoElectronico crearDocumentoElectronico(
            FacturaLegal factura) throws Exception {
        // ...
    }

    @Transactional
    public LoteDE crearLote() {
        // ...
    }

    @Transactional
    public void vincularDocumentosALote(LoteDE lote, 
            List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos) {
        // ...
    }

    @Transactional
    public void enviarLote(LoteDE lote) throws SifenException {
        // ...
    }

    @Transactional
    public void consultarLote(LoteDE lote) throws SifenException {
        // ...
    }
    
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public RespuestaConsultaDE consultarDE(String cdc) {
        // ...
    }

    // ... (métodos privados y auxiliares)
}
```

### 6.2. `SifenEventoService.java`
Este servicio maneja el ciclo de vida de los eventos que modifican un DE después de haber sido aprobado.

**Responsabilidades Clave:**
-   **`cancelarDE(String cdc, String motivo)`**:
    -   Gestiona la cancelación de un DE aprobado.
    -   Verifica que no exista ya una cancelación aprobada.
    -   Crea un nuevo `EventoCancelacionDE` y lo guarda con estado `PENDIENTE`.
    -   Envía el evento a SIFEN.
    -   Procesa la respuesta para actualizar el estado del evento a `APROBADO` o `RECHAZADO`.
    -   Si el evento es aprobado, actualiza el estado del `DocumentoElectronico` a `CANCELADO`.

-   **`inutilizarNumeros(...)`**:
    -   Permite declarar un rango de números de factura como inutilizados (por saltos, errores, etc.).
    -   Genera y envía el evento de inutilización a SIFEN.

-   **`nominarReceptor(String cdc, Cliente cliente)`**:
    -   Permite identificar al receptor de una factura emitida previamente como innominada.
    -   Utiliza `SifenReceptorHelper` para construir los datos del receptor nominado.
    -   Crea un `EventoNominacionDE` y lo envía a SIFEN.
    -   Si el evento es `APROBADO`, actualiza la `FacturaLegal` original para asociarla con el `Cliente` nominado.

**`SifenEventoService.java`**
```java
// Contenido de SifenEventoService.java
package com.franco.dev.service.sifen.service;
// ... (imports)

@Slf4j
@Service
public class SifenEventoService {

    // ... (inyección de dependencias)

    @Transactional
    public RespuestaRecepcionEvento cancelarDE(String cdc, String motivo) throws SifenException {
        // ...
    }

    @Transactional
    public RespuestaRecepcionEvento inutilizarNumeros(
            Timbrado timbrado,
            String establecimiento,
            String puntoExpedicion,
            int numeroInicio,
            int numeroFin,
            TTiDE tipoDE,
            String motivo) throws SifenException {
        // ...
    }

    @Transactional
    public RespuestaRecepcionEvento nominarReceptor(String cdc, Cliente cliente) throws SifenException {
        // ...
    }

    // ... (métodos privados y auxiliares)
}
```

## 7. Entidad de Soporte Principal

Si bien las entidades anteriores son específicas del módulo de facturación electrónica, todas dependen de una entidad central que representa la factura en el sistema.

### 7.1. `FacturaLegal.java`
Esta entidad es el punto de partida para todo el proceso. Contiene los datos maestros de la transacción comercial (cliente, ítems, totales, timbrado) que se utilizarán para construir el Documento Electrónico. La entidad `DocumentoElectronico` tiene una relación `OneToOne` con `FacturaLegal`, vinculando el registro electrónico con la transacción original.

**`FacturaLegal.java`**
```java
package com.franco.dev.domain.financiero;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "factura_legal", schema = "financiero")
public class FacturaLegal implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sucursalId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "timbrado_detalle_id", nullable = true)
    private TimbradoDetalle timbradoDetalle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "caja_id", nullable = true)
    private PdvCaja caja;

    private Boolean viaTributaria;

    private Boolean autoimpreso;

    @Column(name = "numeroFactura")
    private Integer numeroFactura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = true)
    private Venta venta;

    private LocalDateTime fecha;
    private Boolean credito;
    private String nombre;
    private String ruc;
    private String direccion;
    private String cdc;
    
    // Relación con documento electrónico (opcional)
    @OneToOne(mappedBy = "facturaLegal", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private DocumentoElectronico documentoElectronico;

    @Column(name = "iva_parcial_0")
    private Double ivaParcial0;
    @Column(name = "iva_parcial_5")
    private Double ivaParcial5;
    @Column(name = "iva_parcial_10")
    private Double ivaParcial10;
    @Column(name = "total_parcial_0")
    private Double totalParcial0;
    @Column(name = "total_parcial_5")
    private Double totalParcial5;
    @Column(name = "total_parcial_10")
    private Double totalParcial10;

    private Double totalFinal;
    private Double descuento;

    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
```

#### Tabla `financiero.factura_legal`
| column_name         | data_type                   | is_nullable | column_default                                 |
| ------------------- | --------------------------- | ----------- | ---------------------------------------------- |
| id                  | bigint                      | NO          | nextval('financiero.factura_legal_id_seq')     |
| timbrado_detalle_id | bigint                      | NO          |                                                |
| numero_factura      | numeric                     | NO          |                                                |
| autoimpreso         | boolean                     | YES         | true                                           |
| cliente_id          | bigint                      | YES         |                                                |
| venta_id            | integer                     | YES         |                                                |
| fecha               | timestamp without time zone | YES         |                                                |
| credito             | boolean                     | YES         |                                                |
| nombre              | character varying           | YES         |                                                |
| ruc                 | character varying           | YES         |                                                |
| direccion           | character varying           | YES         |                                                |
| iva_parcial_0       | numeric                     | YES         |                                                |
| iva_parcial_5       | numeric                     | YES         |                                                |
| iva_parcial_10      | numeric                     | YES         |                                                |
| total_parcial_0     | numeric                     | YES         |                                                |
| total_parcial_5     | numeric                     | YES         |                                                |
| total_parcial_10    | numeric                     | YES         |                                                |
| total_final         | numeric                     | YES         |                                                |
| activo              | boolean                     | YES         | true                                           |
| creado_en           | timestamp without time zone | YES         |                                                |
| usuario_id          | bigint                      | YES         |                                                |
| via_tributaria      | boolean                     | YES         | false                                          |
| caja_id             | bigint                      | YES         |                                                |
| sucursal_id         | bigint                      | YES         |                                                |
| descuento           | numeric                     | YES         | 0                                              |
| cdc                 | character varying(255)      | YES         | NULL::character varying                        |
