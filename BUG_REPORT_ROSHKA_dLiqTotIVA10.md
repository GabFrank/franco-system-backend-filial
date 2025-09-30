# Bug Report: dLiqTotIVA10 se genera con valor 0

## 🐛 Descripción del Bug

La clase `TgTotSub` genera automáticamente los totales del DE, pero el campo `dLiqTotIVA10` queda con valor `0` en lugar de tener el mismo valor que `dIVA10`.

## 📊 Comportamiento Actual

**XML Generado:**
```xml
<gTotSub>
    <dIVA10>1727</dIVA10>
    <dLiqTotIVA10>0</dLiqTotIVA10>  ❌ INCORRECTO
</gTotSub>
```

## ✅ Comportamiento Esperado

**XML Correcto:**
```xml
<gTotSub>
    <dIVA10>1727</dIVA10>
    <dLiqTotIVA10>1727</dLiqTotIVA10>  ✅ CORRECTO
</gTotSub>
```

## 🔧 Impacto

Este bug causa que SIFEN puede rechazar el documento electrónico con errores relacionados a inconsistencias en los totales de IVA.

Según la especificación técnica de SIFEN v150:
- `dIVA10`: Liquidación del IVA (10%)
- `dLiqTotIVA10`: Total de la liquidación del IVA (10%)
- **Ambos campos deben tener el mismo valor**

## 📌 Versión

- **Librería:** rshk-jsifenlib 0.2.4
- **Java:** 11+
- **Fecha:** 2025-09-30

## 🔍 Código de Reproducción

```java
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.fields.request.de.*;
import com.roshka.sifen.core.types.*;
import java.math.BigDecimal;

// Crear DE con un item IVA 10%
DocumentoElectronico DE = new DocumentoElectronico();

// ... configurar emisor, receptor, timbrado, etc ...

// Agregar un item con IVA 10%
TgCamItem item = new TgCamItem();
item.setdCodInt("001");
item.setdDesProSer("PRODUCTO TEST");
item.setcUniMed(TcUniMed.UNI);
item.setdCantProSer(BigDecimal.ONE);

TgValorItem valorItem = new TgValorItem();
valorItem.setdPUniProSer(BigDecimal.valueOf(19000));
TgValorRestaItem valorResta = new TgValorRestaItem();
valorItem.setgValorRestaItem(valorResta);
item.setgValorItem(valorItem);

TgCamIVA camIVA = new TgCamIVA();
camIVA.setiAfecIVA(TiAfecIVA.GRAVADO);
camIVA.setdPropIVA(BigDecimal.valueOf(100));
camIVA.setdTasaIVA(BigDecimal.valueOf(10));
item.setgCamIVA(camIVA);

// Agregar item y generar totales
gDtipDE.setgCamItemList(Arrays.asList(item));
DE.setgDtipDE(gDtipDE);
DE.setgTotSub(new TgTotSub());

// VERIFICAR BUG
TgTotSub totales = DE.getgTotSub();
System.out.println("dIVA10: " + totales.getdIVA10());           // 1727 ✅
System.out.println("dLiqTotIVA10: " + totales.getdLiqTotIVA10()); // 0 ❌
```

## 💡 Solución Esperada

La clase `TgTotSub` debería:
1. Setear automáticamente `dLiqTotIVA10 = dIVA10`
2. Setear automáticamente `dLiqTotIVA5 = dIVA5`
3. O exponer métodos `setdLiqTotIVA10()` y `setdLiqTotIVA5()` para permitir seteo manual

## 📎 XML Completo Generado

Ver archivo adjunto con el XML SOAP completo que muestra el problema.

## 🔗 Relación con Otros Issues

Posiblemente relacionado con #50 "Error de calculo de iva cuando la base no es 100"

## ⚠️ Workaround Temporal (NO FUNCIONA)

Intentamos usar reflection para setear el valor manualmente:

```java
Field field = TgTotSub.class.getDeclaredField("dLiqTotIVA10");
field.setAccessible(true);
field.set(totales, totales.getdIVA10());
```

**PROBLEMA:** `Sifen.recepcionDE()` regenera los totales internamente en `setupSOAPElements()`, sobrescribiendo cualquier cambio manual.

## 📧 Contacto

- Proyecto: frc-sistemas-informaticos/backend/filial/frc-filial-server
- Usuario: gabfranck
- Fecha reporte: 2025-09-30

## 🙏 Solicitud

¿Podrían verificar el código fuente de `TgTotSub` y corregir la lógica que calcula `dLiqTotIVA10` y `dLiqTotIVA5`?

Este bug afecta a todos los usuarios que generen facturas electrónicas con IVA 10% o 5%.

Gracias por su atención y excelente trabajo con la librería.
