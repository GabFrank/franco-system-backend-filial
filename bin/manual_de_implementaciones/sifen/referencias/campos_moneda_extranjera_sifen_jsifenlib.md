
# Campos que deben modificarse para emitir un Documento Electrónico en Moneda Extranjera  
Basado en **Manual Técnico SIFEN v150** y en la estructura de la librería **rshk-jsifenlib**.

Este documento resume **todos los campos** que deben configurarse o modificarse cuando la **moneda de la operación es distinta de PYG** (guaraníes), incluyendo encabezado, ítems, totales, pagos y cuotas.

---

# 1. Encabezado – Grupo `gDatGralOpe`  
Corresponde a los campos **D015 a D018** del Manual Técnico.  
En `rshk-jsifenlib` estos campos suelen estar en el objeto:

```java
de.getgDatGralOpe()
```

## 1.1. Moneda de la operación  
**Campo SIFEN:** D015 – `cMoneOpe`  
**Tipo:** ISO 4217  
**Obligatorio si:** Siempre (PYG o extranjera)

Ejemplo:
```java
de.getgDatGralOpe().setcMoneOpe("USD");
```

---

## 1.2. Descripción de la moneda  
**Campo:** D016 – `dDesMoneOpe`  
**Debe coincidir con D015**

Ejemplo:
```java
de.getgDatGralOpe().setdDesMoneOpe("Dólares Americanos");
```

---

## 1.3. Condición del tipo de cambio  
**Campo:** D017 – `iCondTiCam`  
**Requerido si:** `cMoneOpe != "PYG"`  
**Valores posibles:**
- `1` = Global  
- `2` = Por ítem  

Ejemplo:
```java
de.getgDatGralOpe().setiCondTiCam((short)1);
```

---

## 1.4. Tipo de cambio global  
**Campo:** D018 – `dTiCam`  
**Obligatorio si:** `iCondTiCam = 1`  
**No debe informarse si:** `iCondTiCam = 2` o `cMoneOpe = "PYG"`

Ejemplo:
```java
de.getgDatGralOpe().setdTiCam(new BigDecimal("7.3500"));
```

---

# 2. Ítems – Grupo `gCamItem` y subgrupo `gValorItem`
Corresponde al campo **E725** del Manual Técnico.  
En jsifenlib, usualmente:

```java
item.getgValorItem().setdTiCamIt(...)
```

## 2.1. Tipo de cambio por ítem  
**Campo:** E725 – `dTiCamIt`  
**Obligatorio si:**  
- `cMoneOpe != "PYG"`  
- `iCondTiCam = 2` (por ítem)  
**Prohibido si:**  
- `iCondTiCam = 1`  
- `cMoneOpe = "PYG"`

Ejemplo:
```java
item.getgValorItem().setdTiCamIt(new BigDecimal("7.3400"));
```

---

# 3. Totales – Grupo `gTotSub`
Corresponde a campos **F014 y F023**.

En jsifenlib:
```java
de.getgTotSub()
```

---

## 3.1. Total general en moneda de la operación  
**Campo:** F014 – `dTotGralOpe`  
**Es siempre obligatorio**.

Ejemplo:
```java
de.getgTotSub().setdTotGralOpe(new BigDecimal("150.00"));
```

---

## 3.2. Total general en guaraníes  
**Campo:** F023 – `dTotalGs`  
**Requerido si:** `cMoneOpe != "PYG"`  
**No debe informarse si:** `cMoneOpe = "PYG"`

### Validaciones:
- Si `iCondTiCam = 1`  
  `dTotalGs = dTotGralOpe × dTiCam`
- Si `iCondTiCam = 2`  
  `dTotalGs = Σ(EA009)` (total en guaraníes por ítem)

Ejemplo:
```java
de.getgTotSub().setdTotalGs(new BigDecimal("1095000.00"));
```

---

# 4. Pagos – Grupo `gPaConEIni` / `gPagCont`
Corresponde a **E608 – E611**.

En jsifenlib esto suele ser una lista de objetos:
```java
de.getgPaConEIni().add(pago);
```

### Campos afectados:

| Campo SIFEN | Nombre jsifenlib | Cuándo |
|------------|------------------|--------|
| E608 | `dMonTiPag` | Siempre |
| E609 | `cMoneTiPag` | Siempre |
| E610 | `dDMoneTiPag` | Siempre |
| E611 | `dTiCamTiPag` | Obligatorio si la moneda del pago ≠ PYG |

### Ejemplo:
```java
pago.setcMoneTiPag("USD");
pago.setdDMoneTiPag("Dólares Americanos");
pago.setdMonTiPag(new BigDecimal("150.00"));
pago.setdTiCamTiPag(new BigDecimal("7.3500"));
```

---

# 5. Crédito en cuotas – Grupo `gPagCred` / `gCuotas`
Corresponde a **E653 – E654**.

Únicamente relevante si la operación es a crédito.

| Campo | Descripción |
|-------|-------------|
| E653 | `cMoneCuo` – Moneda de la cuota |
| E654 | `dDMoneCuo` – Descripción de la moneda |

En jsifenlib:
```java
cuota.setcMoneCuo("USD");
cuota.setdDMoneCuo("Dólares Americanos");
```

---

# 6. Checklist completo para moneda extranjera

### Encabezado
- [x] `cMoneOpe`
- [x] `dDesMoneOpe`
- [x] `iCondTiCam`
- [x] `dTiCam` (solo si global)

### Ítems (si tipo cambio por ítem)
- [x] `dTiCamIt` en cada item

### Totales
- [x] `dTotGralOpe`
- [x] `dTotalGs`

### Pago(s)
- [x] `dMonTiPag`
- [x] `cMoneTiPag`
- [x] `dDMoneTiPag`
- [x] `dTiCamTiPag` (si moneda ≠ PYG)

### Cuotas
- [x] `cMoneCuo`
- [x] `dDMoneCuo`

---

# 7. Resumen general

Para que una **factura en moneda extranjera** sea aceptada por SIFEN y validada a través de `rshk-jsifenlib`, es obligatorio:

1. Indicar la moneda ISO y su descripción.  
2. Indicar la condición del tipo de cambio.  
3. Proveer el tipo de cambio global o por ítem.  
4. Calcular y enviar el total en guaraníes.  
5. Ajustar las formas de pago según moneda y tipo de cambio.  
6. Mantener coherencia de moneda entre encabezado, ítems, totales y pagos.

---

Documento generado para integración con **FRC Sistemas – SIFEN v150**.
