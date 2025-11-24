# API de Facturas Legales

Esta API permite crear facturas legales sin estar vinculadas a una venta. Las facturas pueden ser electrónicas o normales dependiendo del timbrado utilizado.

## Endpoints

### 1. Verificar Disponibilidad de Timbrado Detalle

Verifica si un timbrado detalle está disponible para emitir facturas.

**Endpoint:** `GET /api/facturas-legales/timbrado-detalle/{timbradoDetalleId}/disponibilidad`

**Parámetros de URL:**
- `timbradoDetalleId` (Long, requerido): ID del timbrado detalle a verificar

**Respuesta Exitosa (200 OK):**
```json
{
  "disponible": true,
  "mensaje": "El timbrado detalle está disponible para emitir facturas",
  "timbradoDetalleId": 1,
  "numeroActual": 100,
  "rangoDesde": 1,
  "rangoHasta": 1000,
  "numerosDisponibles": 900,
  "esElectronico": true,
  "numeroTimbrado": "18270044",
  "activo": true,
  "timbradoActivo": true
}
```

**Respuesta de Error (400 Bad Request):**
```json
{
  "disponible": false,
  "mensaje": "El timbrado detalle no está activo",
  "timbradoDetalleId": 1,
  "activo": false
}
```

**Códigos de Estado:**
- `200 OK`: El timbrado detalle está disponible
- `400 Bad Request`: El timbrado detalle no está disponible (ver mensaje para detalles)

**Ejemplo de Uso:**
```bash
curl -X GET "http://localhost:8082/api/facturas-legales/timbrado-detalle/1/disponibilidad"
```

---

### 2. Crear Factura Legal

Crea una nueva factura legal sin estar vinculada a una venta. Si el timbrado es electrónico y SIFEN está habilitado, se generará automáticamente el documento electrónico.

**Endpoint:** `POST /api/facturas-legales`

**Headers:**
```
Content-Type: application/json
```

**Body (JSON):**
```json
{
  "timbradoDetalleId": 1,
  "cajaId": 5,
  "clienteId": 10,
  "nombre": "JUAN PEREZ",
  "ruc": "1234567-8",
  "direccion": "Av. Principal 123",
  "email": "juan.perez@example.com",
  "viaTributaria": false,
  "credito": false,
  "fecha": "2024-01-15T10:30:00",
  "items": [
    {
      "productoId": 100,
      "descripcion": "Producto A",
      "cantidad": 2.0,
      "precioUnitario": 50000.0,
      "total": 100000.0,
      "unidadMedida": "UNIDAD",
      "iva": 10
    },
    {
      "productoId": 101,
      "descripcion": "Producto B",
      "cantidad": 1.0,
      "precioUnitario": 75000.0,
      "total": 75000.0,
      "unidadMedida": "KILO",
      "iva": 5
    }
  ],
  "ivaParcial0": 0.0,
  "ivaParcial5": 0.0,
  "ivaParcial10": 17500.0,
  "totalParcial0": 0.0,
  "totalParcial5": 0.0,
  "totalParcial10": 192500.0,
  "totalFinal": 192500.0,
  "descuento": 0.0,
  "monedaExtranjera": "USD",
  "tipoCambio": 7500.0,
  "imprimir": true,
  "printerName": "EPSON TM-T20"
}
```

**Campos del Request:**

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `timbradoDetalleId` | Long | Sí | ID del timbrado detalle a utilizar |
| `cajaId` | Long | No | ID de la caja (opcional) |
| `clienteId` | Long | No | ID del cliente (opcional) |
| `nombre` | String | Sí | Nombre del cliente (máx. 255 caracteres) |
| `ruc` | String | Sí | RUC del cliente (máx. 20 caracteres) |
| `direccion` | String | No | Dirección del cliente (máx. 500 caracteres) |
| `email` | String | No | Email del cliente |
| `viaTributaria` | Boolean | No | Indica si es vía tributaria (default: false) |
| `credito` | Boolean | No | Indica si es crédito (default: false) |
| `fecha` | DateTime | No | Fecha de la factura (default: fecha actual) |
| `items` | Array | Sí | Lista de items de la factura (mín. 1 item) |
| `ivaParcial0` | Double | No | IVA parcial al 0% (default: 0.0) |
| `ivaParcial5` | Double | No | IVA parcial al 5% (default: 0.0) |
| `ivaParcial10` | Double | No | IVA parcial al 10% (default: 0.0) |
| `totalParcial0` | Double | No | Total parcial al 0% (default: 0.0) |
| `totalParcial5` | Double | No | Total parcial al 5% (default: 0.0) |
| `totalParcial10` | Double | No | Total parcial al 10% (default: 0.0) |
| `totalFinal` | Double | Sí | Total final de la factura |
| `descuento` | Double | No | Descuento total (default: 0.0) |
| `monedaExtranjera` | String | No | Código de moneda extranjera (3 caracteres, ej: "USD", "BRL") |
| `tipoCambio` | Double | No | Tipo de cambio (ej: 7500.0 para 1 USD = 7500 Gs) |
| `imprimir` | Boolean | No | Indica si se debe imprimir la factura en este servidor (default: false) |
| `printerName` | String | No | Nombre de la impresora (requerido si `imprimir` es true) |

**Campos de Items:**

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `productoId` | Long | No | ID del producto (opcional) |
| `descripcion` | String | Sí | Descripción del item |
| `cantidad` | Double | Sí | Cantidad (debe ser > 0) |
| `precioUnitario` | Double | Sí | Precio unitario (debe ser >= 0) |
| `total` | Double | No | Total del item (si no se proporciona, se calcula como cantidad × precioUnitario) |
| `unidadMedida` | String | No | Unidad de medida (ej: "UNIDAD", "KILO", "LITRO", "CAJA") |
| `iva` | Integer | No | Porcentaje de IVA del item (ej: 0, 5, 10) |

**Respuesta Exitosa (201 Created):**
```json
{
  "id": 123,
  "numeroFactura": 101,
  "nombre": "JUAN PEREZ",
  "ruc": "1234567-8",
  "direccion": "Av. Principal 123",
  "fecha": "2024-01-15T10:30:00",
  "totalFinal": 192500.0,
  "esElectronica": true,
  "cdc": "01800012345678901234567890123456789012345678",
  "urlQr": "https://ekuatia.set.gov.py/consultas/qr?nro=01800012345678901234567890123456789012345678",
  "estadoDocumentoElectronico": "PENDIENTE_ENVIO",
  "mensajeRespuestaSifen": null,
  "documentoElectronicoGenerado": true
}
```

**Respuesta de Error (400 Bad Request):**
- El timbrado detalle no está disponible
- Datos de validación incorrectos
- El timbrado detalle no tiene una sucursal asignada

**Respuesta de Error (500 Internal Server Error):**
- Error inesperado al crear la factura

**Códigos de Estado:**
- `201 Created`: Factura creada exitosamente
- `400 Bad Request`: Error en los datos proporcionados o timbrado no disponible
- `500 Internal Server Error`: Error interno del servidor

**Ejemplo de Uso:**
```bash
curl -X POST "http://localhost:8082/api/facturas-legales" \
  -H "Content-Type: application/json" \
  -d '{
    "timbradoDetalleId": 1,
    "nombre": "JUAN PEREZ",
    "ruc": "1234567-8",
    "direccion": "Av. Principal 123",
    "items": [
      {
        "descripcion": "Producto A",
        "cantidad": 2.0,
        "precioUnitario": 50000.0,
        "unidadMedida": "UNIDAD",
        "iva": 10
      }
    ],
    "totalFinal": 100000.0
  }'
```

---

## Flujo de Uso Recomendado

### Paso 1: Verificar Disponibilidad

Antes de crear una factura, siempre verifica que el timbrado detalle esté disponible:

```bash
GET /api/facturas-legales/timbrado-detalle/{timbradoDetalleId}/disponibilidad
```

**Verificaciones que realiza el sistema:**
- El timbrado detalle existe
- El timbrado detalle está activo
- El timbrado asociado está activo
- Hay números disponibles en el rango
- Compatibilidad con SIFEN (si está habilitado, el timbrado debe ser electrónico)

### Paso 2: Crear la Factura

Si el timbrado detalle está disponible, procede a crear la factura:

```bash
POST /api/facturas-legales
```

**Proceso automático:**
1. Se valida la disponibilidad del timbrado detalle
2. Se crea la factura legal
3. Se crean los items de la factura
4. Se incrementa el número actual del timbrado detalle
5. Si el timbrado es electrónico y SIFEN está habilitado:
   - Se genera automáticamente el documento electrónico
   - Se asigna el CDC a la factura
   - Se genera la URL QR
   - Se guarda el estado del documento electrónico
6. Si `imprimir` es `true` y se proporciona `printerName`:
   - Se imprime la factura en la impresora especificada
   - Si la factura es en moneda extranjera, se imprime con los valores convertidos
   - Si hay error en la impresión, se registra en los logs pero no se interrumpe el proceso

---

## Facturas Electrónicas vs Normales

### Facturas Electrónicas

- Requieren que SIFEN esté habilitado (`sifen.enabled=true`)
- Requieren un timbrado con `isElectronico=true`
- Se genera automáticamente el documento electrónico al crear la factura
- Incluyen CDC (Código de Control) y URL QR en la respuesta
- El documento electrónico se envía a SIFEN en lotes (procesamiento asíncrono)

### Facturas Normales

- No requieren SIFEN habilitado
- Requieren un timbrado con `isElectronico=false`
- No se genera documento electrónico
- Los campos `cdc`, `urlQr`, `estadoDocumentoElectronico` serán `null` en la respuesta

---

## Impresión de Facturas

### Configuración de Impresión

Para imprimir una factura en el servidor, debes incluir los siguientes campos en el request:

- `imprimir`: `true` para indicar que se debe imprimir, `false` o `null` para no imprimir
- `printerName`: Nombre de la impresora (requerido si `imprimir` es `true`)

### Comportamiento

- Si `imprimir` es `true` y se proporciona `printerName`:
  - La factura se imprimirá automáticamente después de ser creada
  - Si la factura es en moneda extranjera, se imprimirá con los valores convertidos
  - El formato de impresión es un ticket de 58mm
  - Si hay error en la impresión, se registra en los logs pero **no se interrumpe** el proceso de creación de la factura

- Si `imprimir` es `false` o `null`:
  - No se intentará imprimir la factura
  - La factura se creará normalmente

- Si `imprimir` es `true` pero `printerName` es `null`:
  - Se registrará una advertencia en los logs
  - La factura se creará normalmente sin imprimir

### Notas Importantes

- La impresión se realiza **después** de crear la factura y generar el documento electrónico (si aplica)
- Si la factura es electrónica, el ticket incluirá el código QR y el CDC
- Los errores de impresión no afectan la creación de la factura
- El nombre de la impresora debe coincidir exactamente con el nombre configurado en el sistema operativo

---

## Validaciones

### Validaciones de Timbrado Detalle

- Debe existir
- Debe estar activo
- El timbrado asociado debe estar activo
- Debe tener números disponibles en el rango
- Debe ser compatible con el estado de SIFEN

### Validaciones de Request

- `timbradoDetalleId`: Requerido
- `nombre`: Requerido, máximo 255 caracteres
- `ruc`: Requerido, máximo 20 caracteres
- `items`: Requerido, mínimo 1 item
- `totalFinal`: Requerido, debe ser >= 0
- Cada item debe tener:
  - `descripcion`: Requerida
  - `cantidad`: Requerida, debe ser > 0
  - `precioUnitario`: Requerido, debe ser >= 0

---

## Manejo de Errores

### Errores Comunes

1. **Timbrado detalle no disponible**
   - Verifica que el timbrado detalle exista y esté activo
   - Verifica que haya números disponibles
   - Verifica la compatibilidad con SIFEN

2. **Error al generar documento electrónico**
   - La factura se crea correctamente
   - El campo `documentoElectronicoGenerado` será `false`
   - El campo `mensajeRespuestaSifen` contendrá el error
   - Puedes intentar regenerar el documento electrónico más tarde

3. **Validación de datos**
   - Revisa que todos los campos requeridos estén presentes
   - Verifica los tipos de datos y rangos permitidos

---

## Notas Importantes

1. **Transaccionalidad**: La creación de la factura es transaccional. Si falla algún paso, se revierte toda la operación.

2. **Números de Factura**: El sistema incrementa automáticamente el número actual del timbrado detalle. Asegúrate de que haya números disponibles antes de crear la factura.

3. **Documentos Electrónicos**: Si el timbrado es electrónico, el documento electrónico se crea automáticamente pero se envía a SIFEN de forma asíncrona en lotes. El estado inicial será `PENDIENTE_ENVIO`.

4. **Sucursal**: La factura hereda automáticamente la sucursal del timbrado detalle. Si el timbrado detalle no tiene sucursal asignada, la operación fallará.

5. **Cliente**: Si proporcionas un `clienteId`, el sistema lo asociará a la factura. Si no, la factura se crea sin cliente asociado.

---

## Ejemplos Completos

### Ejemplo 1: Factura Normal

```bash
# 1. Verificar disponibilidad
curl -X GET "http://localhost:8082/api/facturas-legales/timbrado-detalle/1/disponibilidad"

# 2. Crear factura
curl -X POST "http://localhost:8082/api/facturas-legales" \
  -H "Content-Type: application/json" \
  -d '{
    "timbradoDetalleId": 1,
    "nombre": "MARIA GONZALEZ",
    "ruc": "9876543-2",
    "direccion": "Calle Secundaria 456",
    "items": [
      {
        "descripcion": "Servicio de Consultoría",
        "cantidad": 1.0,
        "precioUnitario": 500000.0
      }
    ],
    "ivaParcial10": 50000.0,
    "totalParcial10": 550000.0,
    "totalFinal": 550000.0
  }'
```

### Ejemplo 2: Factura Electrónica

```bash
# 1. Verificar disponibilidad
curl -X GET "http://localhost:8082/api/facturas-legales/timbrado-detalle/5/disponibilidad"

# 2. Crear factura electrónica
curl -X POST "http://localhost:8082/api/facturas-legales" \
  -H "Content-Type: application/json" \
  -d '{
    "timbradoDetalleId": 5,
    "clienteId": 10,
    "nombre": "EMPRESA ABC S.A.",
    "ruc": "80012345-6",
    "direccion": "Av. Principal 789",
    "email": "contacto@empresaabc.com.py",
    "items": [
      {
        "productoId": 100,
        "descripcion": "Producto X",
        "cantidad": 10.0,
        "precioUnitario": 100000.0,
        "total": 1000000.0,
        "unidadMedida": "UNIDAD",
        "iva": 10
      },
      {
        "productoId": 101,
        "descripcion": "Producto Y",
        "cantidad": 5.0,
        "precioUnitario": 200000.0,
        "total": 1000000.0,
        "unidadMedida": "KILO",
        "iva": 10
      }
    ],
    "ivaParcial10": 200000.0,
    "totalParcial10": 2200000.0,
    "totalFinal": 2200000.0
  }'
```

---

## Soporte

Para más información o soporte, contacta al equipo de desarrollo.

