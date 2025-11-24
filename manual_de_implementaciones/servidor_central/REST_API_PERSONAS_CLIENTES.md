# Manual de Implementación - REST API para Sincronización de Personas y Clientes

Este documento describe los endpoints REST que debe implementar el **servidor central** para recibir peticiones de sincronización desde los **servidores filiales**.

---

## Configuración Base

**URL Base:** Configurada en la propiedad `ipServidorCentral` del servidor filial  
**Ejemplo:** `http://localhost:8081` o `http://192.168.1.100:8081`

**Content-Type:** `application/json`  
**Método HTTP:** `POST`

---

## Endpoint 1: Sincronización de Personas

### URL
```
POST /api/personas
```

### Descripción
Recibe una petición para crear o actualizar una persona en el servidor central.  
Si la persona ya existe (por ID o documento), debe actualizarse. Si no existe, debe crearse.

### Request Body - PersonaSyncRequest

```json
{
  "id": 123,                    // Long | Opcional - ID de la persona si ya existe
  "nombre": "JUAN PEREZ",       // String | Opcional - Nombre completo
  "apodo": "JUANCHO",           // String | Opcional - Apodo
  "sexo": "M",                  // String | Opcional - Sexo (M/F)
  "nacimiento": "1990-01-15T00:00:00",  // String | Opcional - Fecha en formato ISO 8601 (yyyy-MM-ddTHH:mm:ss)
  "documento": "1234567",       // String | Opcional - Número de documento (RUC, CI, etc.)
  "email": "juan@example.com",  // String | Opcional - Email
  "direccion": "AV. PRINCIPAL 123",  // String | Opcional - Dirección
  "telefono": "0981234567",     // String | Opcional - Teléfono
  "socialMedia": "facebook.com/juan",  // String | Opcional - Redes sociales
  "imagenes": "url1,url2",      // String | Opcional - URLs de imágenes separadas por coma
  "ciudadId": 1,                // Long | Opcional - ID de la ciudad
  "usuarioId": 5                // Long | Opcional - ID del usuario que creó/modificó
}
```

### Response - Persona

**Status Code:** `200 OK`

```json
{
  "id": 123,                    // Long | REQUERIDO - ID de la persona (generado o existente)
  "nombre": "JUAN PEREZ",       // String | Nombre completo
  "apodo": "JUANCHO",           // String | Apodo
  "sexo": "M",                  // String | Sexo
  "nacimiento": "1990-01-15T00:00:00",  // String | Fecha de nacimiento (ISO 8601)
  "documento": "1234567",       // String | Número de documento
  "email": "juan@example.com",  // String | Email
  "direccion": "AV. PRINCIPAL 123",  // String | Dirección
  "telefono": "0981234567",     // String | Teléfono
  "socialMedia": "facebook.com/juan",  // String | Redes sociales
  "imagenes": "url1,url2",      // String | URLs de imágenes
  "creadoEn": "2025-01-15T10:30:00"   // String | Fecha de creación (ISO 8601)
}
```

### Lógica de Negocio

1. **Si `id` está presente y existe en BD:**
   - Actualizar la persona existente con los datos proporcionados
   - Retornar la persona actualizada con su ID

2. **Si `id` está presente pero NO existe en BD:**
   - Crear nueva persona con ese ID (si el sistema lo permite)
   - O generar nuevo ID y crear la persona

3. **Si `id` es null:**
   - Buscar por `documento` si está presente
   - Si existe, actualizar
   - Si no existe, crear nueva persona con ID generado

4. **Validaciones importantes:**
   - El campo `id` en la respuesta es **OBLIGATORIO**
   - Si no se puede crear/actualizar, retornar error HTTP 4xx/5xx
   - El formato de fecha `nacimiento` debe ser ISO 8601: `yyyy-MM-ddTHH:mm:ss`

### Ejemplo de Petición

```bash
curl -X POST http://localhost:8081/api/personas \
  -H "Content-Type: application/json" \
  -d '{
    "documento": "80017582",
    "nombre": "COOPERATIVA MULTIACTIVA NEULAND LIMITADA",
    "direccion": "AV. PRINCIPAL 123",
    "email": "contacto@cooperativa.com"
  }'
```

### Ejemplo de Respuesta Exitosa

```json
{
  "id": 456,
  "nombre": "COOPERATIVA MULTIACTIVA NEULAND LIMITADA",
  "documento": "80017582",
  "direccion": "AV. PRINCIPAL 123",
  "email": "contacto@cooperativa.com",
  "creadoEn": "2025-11-19T11:17:49"
}
```

---

## Endpoint 2: Sincronización de Clientes

### URL
```
POST /api/personas/clientes
```

### Descripción
Recibe una petición para crear o actualizar un cliente en el servidor central.  
El cliente debe estar asociado a una persona (personaId).

### Request Body - ClienteSyncRequest

```json
{
  "id": 789,                    // Long | Opcional - ID del cliente si ya existe
  "tipo": "MINORISTA",          // String | Opcional - Tipo de cliente (enum: MINORISTA, MAYORISTA, etc.)
  "credito": 1000000.0,         // Float | Opcional - Límite de crédito
  "codigo": "CLI-001",          // String | Opcional - Código del cliente
  "tributa": true,              // Boolean | Opcional - Si tributa IVA
  "verificadoSet": true,        // Boolean | Opcional - Si está verificado en SET
  "personaId": 456,             // Long | Opcional - ID de la persona asociada (REQUERIDO para crear)
  "sucursalId": 1,              // Long | Opcional - ID de la sucursal
  "usuarioId": 5                // Long | Opcional - ID del usuario que creó/modificó
}
```

### Response - Cliente

**Status Code:** `200 OK`

```json
{
  "id": 789,                    // Long | REQUERIDO - ID del cliente (generado o existente)
  "tipo": "MINORISTA",          // String | Tipo de cliente
  "credito": 1000000.0,         // Float | Límite de crédito
  "codigo": "CLI-001",          // String | Código del cliente
  "tributa": true,              // Boolean | Si tributa IVA
  "verificadoSet": true,        // Boolean | Si está verificado en SET
  "creadoEn": "2025-11-19T11:17:49"  // String | Fecha de creación (ISO 8601)
}
```

### Lógica de Negocio

1. **Si `id` está presente y existe en BD:**
   - Actualizar el cliente existente con los datos proporcionados
   - Retornar el cliente actualizado con su ID

2. **Si `id` está presente pero NO existe en BD:**
   - Crear nuevo cliente con ese ID (si el sistema lo permite)
   - O generar nuevo ID y crear el cliente

3. **Si `id` es null:**
   - Buscar cliente por `personaId` si está presente
   - Si existe, actualizar
   - Si no existe, crear nuevo cliente con ID generado

4. **Validaciones importantes:**
   - El campo `id` en la respuesta es **OBLIGATORIO**
   - Si `personaId` está presente, debe existir la persona en BD
   - Si no se puede crear/actualizar, retornar error HTTP 4xx/5xx
   - El enum `tipo` debe ser uno de los valores válidos del sistema

### Ejemplo de Petición

```bash
curl -X POST http://localhost:8081/api/personas/clientes \
  -H "Content-Type: application/json" \
  -d '{
    "personaId": 456,
    "tipo": "MINORISTA",
    "tributa": true,
    "verificadoSet": true,
    "credito": 5000000.0
  }'
```

### Ejemplo de Respuesta Exitosa

```json
{
  "id": 789,
  "tipo": "MINORISTA",
  "tributa": true,
  "verificadoSet": true,
  "credito": 5000000.0,
  "creadoEn": "2025-11-19T11:17:49"
}
```

---

## Manejo de Errores

### Errores HTTP Comunes

#### 400 Bad Request
**Causa:** Datos inválidos en el request  
**Ejemplo:**
```json
{
  "error": "Datos inválidos",
  "message": "El campo 'documento' es requerido para crear una nueva persona"
}
```

#### 404 Not Found
**Causa:** Recurso relacionado no encontrado  
**Ejemplo:**
```json
{
  "error": "Recurso no encontrado",
  "message": "La persona con ID 456 no existe"
}
```

#### 500 Internal Server Error
**Causa:** Error interno del servidor  
**Ejemplo:**
```json
{
  "error": "Error interno del servidor",
  "message": "Error al guardar en la base de datos"
}
```

### Importante
- **NUNCA** retornar `null` en el body de la respuesta
- **SIEMPRE** retornar un objeto JSON válido
- El campo `id` en la respuesta es **CRÍTICO** - debe estar presente siempre

---

## Formato de Fechas

Todas las fechas deben estar en formato **ISO 8601**:
- Formato: `yyyy-MM-ddTHH:mm:ss`
- Ejemplo: `2025-11-19T11:17:49`
- Zona horaria: Se asume la zona horaria del servidor

---

## Ejemplo de Implementación (Spring Boot)

### Controller

```java
@RestController
@RequestMapping("/api/personas")
public class PersonaSyncController {

    @Autowired
    private PersonaService personaService;

    @PostMapping
    public ResponseEntity<Persona> syncPersona(@RequestBody PersonaSyncRequest request) {
        try {
            // Buscar o crear persona
            Persona persona = personaService.syncPersona(request);
            
            // Retornar persona con ID
            return ResponseEntity.ok(persona);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/clientes")
    public ResponseEntity<Cliente> syncCliente(@RequestBody ClienteSyncRequest request) {
        try {
            // Buscar o crear cliente
            Cliente cliente = clienteService.syncCliente(request);
            
            // Retornar cliente con ID
            return ResponseEntity.ok(cliente);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
```

### DTOs de Request

```java
@Data
public class PersonaSyncRequest {
    private Long id;
    private String nombre;
    private String apodo;
    private String sexo;
    private String nacimiento;  // ISO 8601 format
    private String documento;
    private String email;
    private String direccion;
    private String telefono;
    private String socialMedia;
    private String imagenes;
    private Long ciudadId;
    private Long usuarioId;
}

@Data
public class ClienteSyncRequest {
    private Long id;
    private String tipo;  // Enum como String
    private Float credito;
    private String codigo;
    private Long sucursalId;
    private Long personaId;
    private Long usuarioId;
    private Boolean tributa;
    private Boolean verificadoSet;
}
```

---

## Notas Importantes

1. **IDs Obligatorios:** Los objetos de respuesta **DEBEN** incluir el campo `id`. Este es crítico para que el servidor filial pueda guardar la referencia local.

2. **Upsert Logic:** Los endpoints deben implementar lógica de "upsert" (update or insert):
   - Si existe (por ID o documento/personaId), actualizar
   - Si no existe, crear nuevo

3. **Validaciones:** Validar que:
   - Las relaciones existan (personaId, ciudadId, usuarioId, sucursalId)
   - Los enums sean válidos
   - Los campos requeridos estén presentes

4. **Transacciones:** Las operaciones deben ser atómicas (transaccionales)

5. **Logging:** Registrar todas las operaciones para auditoría

---

## Testing

### Test con cURL - Persona

```bash
# Crear nueva persona
curl -X POST http://localhost:8081/api/personas \
  -H "Content-Type: application/json" \
  -d '{
    "documento": "80017582",
    "nombre": "COOPERATIVA MULTIACTIVA NEULAND LIMITADA",
    "direccion": "AV. PRINCIPAL 123"
  }'

# Actualizar persona existente
curl -X POST http://localhost:8081/api/personas \
  -H "Content-Type: application/json" \
  -d '{
    "id": 456,
    "nombre": "COOPERATIVA MULTIACTIVA NEULAND LIMITADA ACTUALIZADA",
    "direccion": "AV. PRINCIPAL 456"
  }'
```

### Test con cURL - Cliente

```bash
# Crear nuevo cliente
curl -X POST http://localhost:8081/api/personas/clientes \
  -H "Content-Type: application/json" \
  -d '{
    "personaId": 456,
    "tipo": "MINORISTA",
    "tributa": true,
    "verificadoSet": true
  }'
```

---

**Documento generado para integración Servidor Central - Servidor Filial**  
**Fecha:** 2025-11-19

