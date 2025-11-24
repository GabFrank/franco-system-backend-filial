# Manual de Implementación: Impresión de Facturas Legales

Este manual describe cómo implementar los métodos de impresión de facturas legales en otro servidor, incluyendo soporte para moneda extranjera.

## Índice

1. [Descripción General](#descripción-general)
2. [Requisitos Previos](#requisitos-previos)
3. [Dependencias Necesarias](#dependencias-necesarias)
4. [Estructura de Métodos](#estructura-de-métodos)
5. [Implementación Paso a Paso](#implementación-paso-a-paso)
6. [Configuración de Campos](#configuración-de-campos)
7. [Ejemplos de Uso](#ejemplos-de-uso)
8. [Consideraciones Importantes](#consideraciones-importantes)
9. [Troubleshooting](#troubleshooting)

---

## Descripción General

El sistema de impresión de facturas legales incluye dos métodos principales:

1. **`printTicket58mmFactura`**: Imprime facturas en guaraníes (moneda local)
2. **`printTicket58mmFacturaMonedaExtranjera`**: Imprime facturas en moneda extranjera (USD, EUR, etc.)

Ambos métodos generan tickets de 58mm compatibles con impresoras térmicas ESC/POS y soportan:
- Facturas electrónicas con código QR
- Desglose de IVA (5%, 10%, exento)
- Totales por moneda
- Detalles de pago y vuelto
- Formato profesional con logo

---

## Requisitos Previos

### Base de Datos

La tabla `factura_legal` debe tener los siguientes campos adicionales para soporte de moneda extranjera:

```sql
ALTER TABLE financiero.factura_legal 
ADD COLUMN moneda_extranjera VARCHAR(10),
ADD COLUMN tipo_cambio DOUBLE PRECISION;
```

La tabla `factura_legal_item` debe tener:

```sql
ALTER TABLE financiero.factura_legal_item 
ADD COLUMN unidad_medida VARCHAR(50),
ADD COLUMN iva INTEGER;
```

### Entidades Java

#### FacturaLegal.java

```java
@Column(name = "moneda_extranjera")
private String monedaExtranjera;

@Column(name = "tipo_cambio")
private Double tipoCambio;
```

#### FacturaLegalItem.java

```java
@Column(name = "unidad_medida")
private String unidadMedida;

@Column(name = "iva")
private Integer iva;
```

---

## Dependencias Necesarias

### Dependencias Maven

```xml
<!-- EscPos para impresión térmica -->
<dependency>
    <groupId>com.github.anastaciocintra</groupId>
    <artifactId>escpos-coffee</artifactId>
    <version>4.0.1</version>
</dependency>

<!-- ZXing para códigos QR -->
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.1</version>
</dependency>

<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.1</version>
</dependency>
```

### Utilidades Necesarias

#### 1. QRCodeImageGenerator

Clase utilitaria para generar imágenes de códigos QR:

```java
package com.franco.dev.utilitarios.print;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;

public class QRCodeImageGenerator {
    public static BufferedImage generateQRCodeImage(String text, int width, int height) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}
```

#### 2. PrinterOutputStream

Clase para manejar la salida a impresoras. Esta clase debe estar disponible en el proyecto o puede obtenerse de la librería `escpos-coffee`.

**Uso**:
```java
PrintService printService = PrinterOutputStream.getPrintServiceByName(printerName);
PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService);
```

#### 3. Método de Redimensionamiento de Imágenes

Método auxiliar para redimensionar imágenes (logo):

```java
public static BufferedImage resize(BufferedImage img, int newW, int newH) {
    Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
    BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
    
    Graphics2D g2d = dimg.createGraphics();
    g2d.drawImage(tmp, 0, 0, null);
    g2d.dispose();
    
    return dimg;
}
```

### Servicios Requeridos

Los siguientes servicios deben estar inyectados en tu clase:

```java
@Autowired
private FacturaLegalItemService facturaLegalItemService;

@Autowired
private SucursalService sucursalService;

@Autowired
private CambioService cambioService;

@Autowired
private MonedaService monedaService;

@Autowired
private DocumentoElectronicoService documentoElectronicoService;

@Autowired
private ImageService imageService;

@Autowired
private ImpresionService impresionService;
```

---

## Estructura de Métodos

### Método Principal: `printTicket58mmFactura`

**Ubicación**: `FacturaLegalGraphQL.java` (línea ~829)

**Firma**:
```java
public void printTicket58mmFactura(Venta venta, FacturaLegal facturaLegal,
        List<FacturaLegalItem> facturaLegalItemList, String printerName) throws Exception
```

**Funcionalidad**:
- Detecta automáticamente si la factura tiene moneda extranjera
- Redirige a `printTicket58mmFacturaMonedaExtranjera` si corresponde
- Imprime factura en guaraníes si no hay moneda extranjera

**Lógica de Detección**:
```java
// Verificar si es moneda extranjera
boolean esMonedaExtranjera = facturaLegal.getMonedaExtranjera() != null 
        && !facturaLegal.getMonedaExtranjera().trim().isEmpty()
        && facturaLegal.getTipoCambio() != null;

if (esMonedaExtranjera) {
    printTicket58mmFacturaMonedaExtranjera(facturaLegal.getVenta(), facturaLegal, 
            facturaLegalItemList, printerName, 
            facturaLegal.getMonedaExtranjera(), facturaLegal.getTipoCambio());
    return;
}
```

### Método de Moneda Extranjera: `printTicket58mmFacturaMonedaExtranjera`

**Ubicación**: `FacturaLegalGraphQL.java` (línea ~1499)

**Firma**:
```java
public void printTicket58mmFacturaMonedaExtranjera(Venta venta, FacturaLegal facturaLegal,
        List<FacturaLegalItem> facturaLegalItemList, String printerName, 
        String monedaExtranjera, Double tipoCambio) throws Exception
```

**Funcionalidad**:
- Convierte todos los valores a la moneda extranjera
- Muestra el tipo de cambio utilizado
- Formatea valores con 2-3 decimales según necesidad

---

## Implementación Paso a Paso

### Paso 1: Copiar Métodos Base

Copia los siguientes métodos desde `FacturaLegalGraphQL.java`:

1. `printTicket58mmFactura` (líneas ~829-1400)
2. `printTicket58mmFacturaMonedaExtranjera` (líneas ~1499-1687)
3. `formatearMonedaExtranjera` (líneas ~1808-1822) - Método auxiliar privado

### Paso 2: Implementar Lógica de IVA

**IMPORTANTE**: La obtención del IVA debe seguir esta prioridad:

```java
for (FacturaLegalItem vi : facturaLegalItemList) {
    // Prioridad 1: IVA del item directamente
    Integer iva = vi.getIva();
    
    // Prioridad 2: IVA del producto vinculado directamente
    if (iva == null && vi.getProducto() != null) {
        iva = vi.getProducto().getIva();
    }
    // Prioridad 3: IVA del producto a través de la presentación
    else if (iva == null && vi.getPresentacion() != null) {
        iva = vi.getPresentacion().getProducto().getIva();
    }
    
    // Default 10% si no se puede determinar el IVA
    if (iva == null) {
        iva = 10;
    }
    
    // Usar el IVA obtenido...
}
```

### Paso 3: Configurar Conversión de Moneda

En el método de moneda extranjera, todos los valores deben convertirse:

```java
// Convertir valores usando el tipo de cambio
Double totalParcialGs = totalFinal + descuento;
Double totalParcialExtranjera = totalParcialGs / tipoCambio;
Double totalFinalExtranjera = totalFinal / tipoCambio;
Double descuentoExtranjera = descuento / tipoCambio;
Double totalIva10Extranjera = totalIva10 / tipoCambio;
Double totalIva5Extranjera = totalIva5 / tipoCambio;
Double totalIvaExtranjera = totalIva / tipoCambio;
Double totalParcial0Extranjera = (facturaLegal.getTotalParcial0() != null ? 
    facturaLegal.getTotalParcial0() : 0.0) / tipoCambio;

// Para cada item:
Double precioUnitarioExtranjera = vi.getPrecioUnitario() / tipoCambio;
Double totalItemExtranjera = vi.getTotal() / tipoCambio;
```

### Paso 4: Implementar Formateo de Moneda Extranjera

El método `formatearMonedaExtranjera` formatea valores con 2-3 decimales:

```java
private String formatearMonedaExtranjera(Double valor) {
    if (valor == null || valor.isNaN() || valor.isInfinite()) {
        return "0.00";
    }
    
    BigDecimal valorBD = BigDecimal.valueOf(valor);
    BigDecimal valor2Dec = valorBD.setScale(2, RoundingMode.HALF_UP);
    BigDecimal diferencia = valorBD.subtract(valor2Dec).abs();
    BigDecimal umbral = new BigDecimal("0.005");
    
    if (diferencia.compareTo(umbral) > 0) {
        BigDecimal valor3Dec = valorBD.setScale(3, RoundingMode.UP);
        return String.format(Locale.GERMAN, "%.3f", valor3Dec.doubleValue());
    } else {
        return String.format(Locale.GERMAN, "%.2f", valor2Dec.doubleValue());
    }
}
```

### Paso 5: Configurar Impresión de QR

Para facturas electrónicas, se debe imprimir el código QR:

```java
if (facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico() != null
        && facturaLegal.getTimbradoDetalle().getTimbrado().getIsElectronico()) {

    DocumentoElectronico documentoElectronico = documentoElectronicoService
            .findByFacturaLegalIdAndSucursalId(facturaLegal.getId(), facturaLegal.getSucursalId());

    String cdc = documentoElectronico.getCdc();
    String urlQr = documentoElectronico.getUrlQr();

    if (urlQr != null) {
        BufferedImage qrImage = QRCodeImageGenerator.generateQRCodeImage(urlQr, 250, 250);
        imageWrapper.setJustification(EscPosConst.Justification.Center);
        EscPosImage escposImage = new EscPosImage(new CoffeeImageImpl(qrImage), algorithm);
        escpos.write(imageWrapper, escposImage);
        escpos.feed(1);
    }
    
    // Imprimir texto requerido por SIFEN
    escpos.writeLF(center,
            "Consulte la validez de esta Factura Electronica con el numero de CDC impreso abajo en:");
    escpos.writeLF(center, "https://ekuatia.set.gov.py/consultas");
    
    // Formatear CDC en grupos de 4 dígitos
    if (cdc != null) {
        String cdcFormateado = cdc.replaceAll("\\s+", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cdcFormateado.length(); i += 4) {
            if (i > 0) sb.append(" ");
            sb.append(cdcFormateado.substring(i, Math.min(i + 4, cdcFormateado.length())));
        }
        escpos.writeLF(center, sb.toString());
    }
}
```

---

## Configuración de Campos

### Campos Requeridos en FacturaLegal

| Campo | Tipo | Descripción | Ejemplo |
|-------|------|-------------|---------|
| `monedaExtranjera` | String | Código ISO de moneda | "USD", "EUR", "BRL" |
| `tipoCambio` | Double | Tipo de cambio utilizado | 6850.0 |

### Campos Requeridos en FacturaLegalItem

| Campo | Tipo | Descripción | Ejemplo |
|-------|------|-------------|---------|
| `iva` | Integer | Porcentaje de IVA | 5, 10, 0 |
| `unidadMedida` | String | Unidad de medida | "UNIDAD", "kg" |

---

## Ejemplos de Uso

### Ejemplo 1: Impresión Normal (Guaraníes)

```java
FacturaLegal factura = facturaLegalService.findById(facturaId).orElse(null);
List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(facturaId);

// No establecer moneda extranjera
factura.setMonedaExtranjera(null);
factura.setTipoCambio(null);

// Imprimir
facturaLegalGraphQL.printTicket58mmFactura(null, factura, items, "Impresora_Termica");
```

### Ejemplo 2: Impresión en Moneda Extranjera (USD)

```java
FacturaLegal factura = facturaLegalService.findById(facturaId).orElse(null);
List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(facturaId);

// Configurar moneda extranjera
factura.setMonedaExtranjera("USD");
factura.setTipoCambio(6850.0);

// Guardar cambios
facturaLegalService.save(factura);

// Imprimir (se detectará automáticamente la moneda extranjera)
facturaLegalGraphQL.printTicket58mmFactura(null, factura, items, "Impresora_Termica");
```

### Ejemplo 3: Impresión Directa en Moneda Extranjera

```java
FacturaLegal factura = facturaLegalService.findById(facturaId).orElse(null);
List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(facturaId);

// Llamar directamente al método de moneda extranjera
facturaLegalGraphQL.printTicket58mmFacturaMonedaExtranjera(
    null, 
    factura, 
    items, 
    "Impresora_Termica", 
    "USD", 
    6850.0
);
```

---

## Consideraciones Importantes

### 1. Prioridad del IVA

**CRÍTICO**: El IVA debe obtenerse en este orden:
1. `FacturaLegalItem.iva` (campo directo)
2. `Producto.iva` (si el item tiene producto)
3. `Presentacion.getProducto().getIva()` (si el item tiene presentación)
4. Default: 10%

**NO** usar solo el IVA del producto, ya que el item puede tener un IVA diferente.

### 2. Conversión de Moneda

- Todos los valores en la base de datos están en **guaraníes**
- La conversión se hace **solo para impresión**
- El tipo de cambio debe ser el mismo usado al crear la factura

### 3. Formateo de Decimales

- Guaraníes: Sin decimales (formato entero)
- Moneda extranjera: 2-3 decimales según necesidad
- Usar `Locale.GERMAN` para formateo con punto como separador de miles

### 4. Código QR

- Solo se imprime si la factura es electrónica
- Requiere que exista un `DocumentoElectronico` asociado
- La URL QR viene del campo `urlQr` del documento electrónico

### 5. Totales por Moneda

En el método normal, se muestran totales en:
- Guaraníes (Gs.)
- Reales (Rs.)
- Dólares (Us.)

En el método de moneda extranjera, solo se muestra la moneda extranjera seleccionada.

---

## Troubleshooting

### Problema: El IVA no se muestra correctamente

**Solución**: Verificar que el campo `iva` esté guardado en `FacturaLegalItem` y que la lógica de prioridad esté implementada correctamente.

### Problema: Los valores en moneda extranjera no coinciden

**Solución**: 
- Verificar que el tipo de cambio sea el mismo usado al crear la factura
- Asegurarse de que todos los valores se conviertan usando el mismo tipo de cambio
- Verificar que no haya redondeos intermedios

### Problema: El código QR no se imprime

**Solución**:
- Verificar que `timbrado.isElectronico == true`
- Verificar que exista un `DocumentoElectronico` asociado
- Verificar que el campo `urlQr` no sea null
- Verificar que la librería ZXing esté en el classpath

### Problema: La impresora no responde

**Solución**:
- Verificar que el nombre de la impresora sea correcto
- Verificar que la impresora esté conectada y encendida
- Verificar permisos del sistema operativo
- Verificar que la impresora soporte comandos ESC/POS

### Problema: Formato de números incorrecto

**Solución**:
- Usar `Locale.GERMAN` para formateo con punto como separador de miles
- Para guaraníes, usar `NumberFormat.getNumberInstance(Locale.GERMAN).format(valor.intValue())`
- Para moneda extranjera, usar `formatearMonedaExtranjera(valor)`

---

## Archivos de Referencia

Los siguientes archivos contienen la implementación completa:

### Archivos Principales

- `src/main/java/com/franco/dev/graphql/financiero/FacturaLegalGraphQL.java`
  - Método `printTicket58mmFactura` (línea ~829)
  - Método `printTicket58mmFacturaMonedaExtranjera` (línea ~1499)
  - Método `formatearMonedaExtranjera` (línea ~1808)

### Archivos de Utilidades

- `src/main/java/com/franco/dev/utilitarios/print/QRCodeImageGenerator.java`
  - Generación de códigos QR para facturas electrónicas

- `src/main/java/com/franco/dev/utilitarios/print/output/PrinterOutputStream.java`
  - Manejo de salida a impresoras térmicas

- `src/main/java/com/franco/dev/service/utils/PrintingService.java`
  - Métodos auxiliares para redimensionamiento de imágenes

---

## Notas Finales

- Los métodos están diseñados para trabajar con impresoras térmicas de 58mm
- El formato es compatible con estándares ESC/POS
- Se requiere la librería `escpos-coffee` para la generación de comandos
- El logo debe estar en la ruta configurada en `ImageService.storageDirectoryPath`
- Los métodos manejan automáticamente la detección de moneda extranjera

---

**Última actualización**: 2025-01-XX
**Versión**: 1.0

