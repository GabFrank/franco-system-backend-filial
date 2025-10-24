# Actualización v3.0.7: Implementación de Facturación Electrónica (SIFEN)

Esta actualización introduce el sistema de facturación electrónica a través del SIFEN (Sistema Integrado de Facturación Electrónica Nacional).

## Instrucciones de Configuración

A continuación, se detallan los pasos para configurar la facturación electrónica en las sucursales que la requieran.

### Para sucursales que NO utilizan Facturación Electrónica

No es necesario realizar ninguna configuración adicional. El sistema continuará operando con la facturación convencional.

### Para sucursales que SÍ utilizan Facturación Electrónica

Siga los siguientes pasos:

#### 1. Instalar el Certificado Digital
Copie el archivo del certificado digital (extensión `.pfx`) en el directorio `/FRC/certificados/` del servidor.

#### 2. Actualizar el archivo `application.properties`
Agregue las siguientes líneas de configuración:

```properties
# Habilita el módulo de SIFEN
sifen.enabled=true

# Habilita las tareas programadas de SIFEN (consultas, envío de lotes, etc.)
sifen.scheduler.enabled=true

# Ruta completa al archivo del certificado digital
sifen.certificado.archivo=/path/del/certificado.pfx

# Contraseña del certificado digital
sifen.certificado.contrasena=fasa1701

# Código de Seguridad del Contribuyente (CSC)
sifen.csc=D37561586c1CAd69A2e7747E73f9F03B
```

**Importante:**
- Reemplace `/path/del/certificado.pfx` con la ruta y nombre real de su certificado.
- Asegúrese de que la contraseña y el CSC sean los correctos.

#### 3. Configuración del Timbrado
Una vez completados los pasos anteriores, el sistema intentará conectarse con SIFEN. Para comenzar a emitir facturas electrónicas, es indispensable configurar un **nuevo timbrado** específico para facturación electrónica desde el sistema FRC.

