# Plan — la impresión no se cuelga si falla el envío a la impresora (#205)

Rama `fix/impresion-no-cuelga` (desde `develop`). Pieza: **filial**. Sin migración ni cambios de API.

## Problema

`utilitarios/print/output/PrinterOutputStream` es un `PipedOutputStream`. El hilo del request escribe el
ticket en el pipe y otro hilo (`threadPrint`) lo lee y llama a `DocPrintJob.print(...)`.

Si ese hilo termina **sin haber leído**, porque `print` tiró `PrintException` («Printer is not accepting job») o
porque volvió sin consumir el stream, el `PipedInputStream` nunca registra un lector. Cuando se llena el buffer
de 1024 bytes, `write` espera espacio para siempre: el request no termina y el hilo de Tomcat queda bloqueado.
Cada reintento suma otro hilo.

## Cambio

1. **Clase base nueva `SalidaConEnvioEnHilo`**, que usan `PrinterOutputStream` (CUPS) y `TcpIpOutputStream`
   (impresoras de red, que tenían el mismo problema si el socket no conecta).
   - El hilo de envío, al terminar, **siempre cierra el `PipedInputStream`** (`finally`), le haya ido bien o mal.
     Con el lector cerrado, el `write` pendiente sale con `IOException` en menos de un segundo, porque
     `PipedInputStream.awaitSpace` revisa el estado en cada vuelta.
   - Si el envío falló, se guarda la causa y `write` relanza
     `IOException("No se pudo imprimir: <motivo>", causa)`.
   - El error se sigue logueando como hoy.
2. **`FacturaLegalGraphQL`** (`printTicket58mmFactura`, en sus dos variantes): crea siempre un `PrinterOutputStream`
   nuevo. Antes reutilizaba `this.printerOutputStream` si no era `null`, y el campo solo volvía a `null` después de
   un cierre exitoso. Después de una falla, todas las facturas del filial seguían usando el stream muerto hasta
   reiniciar: era el «después sigue sin imprimir» de la issue.

**No cambia:**
- se imprime en otro hilo;
- `close()` no espera;
- `closeYEsperarEnvio` sigue igual;
- `IOException` es *checked*, así que una falla de impresión dentro de `saveVenta` no deja la transacción
  rollback-only (además `saveVenta` atrapa la excepción y devuelve la venta).

## Llamadores y qué ve el usuario

- **Los que atrapan `Exception` y devuelven `false`** (reimpresiones, balance y otros): el desktop recibe `false` o
  el mensaje del error al toque, en vez del timeout de 5 minutos.
- **`saveVenta` / `facturarEImprimir`:** la venta se guarda igual y el ticket no sale. Es lo mismo que hoy, pero sin
  dejar la transacción abierta para siempre.

## Tests

- **`PrinterOutputStreamTest`** (Mockito), cinco casos:
  - el envío falla;
  - el envío falla mientras el que escribe ya espera lugar;
  - la impresora vuelve sin leer;
  - envío normal;
  - ticket chico con el envío fallado.

  Con el código anterior, los casos de falla se cuelgan hasta el límite de 5 s.
- **`TcpIpOutputStreamTest`:** puerto cerrado → error; servidor que atiende → recibe los 5000 bytes.

## Auditoría (paso 5)

Veredicto: revisar. Se incorporó:
- **Crítico:** el campo reutilizado de `FacturaLegalGraphQL` (punto 2 del cambio).
- **Alto:** prueba de runtime con factura y sin factura, sin reiniciar entre falla y éxito.
- **Medio:** `TcpIpOutputStream` con el mismo patrón (punto 1).
- **Menor:** se sacó el override de `flush()`, que `PipedOutputStream` nunca hace fallar.

Riesgos y pendientes declarados:
- La `IOException` es *checked* y no cruza ningún proxy `@Transactional`: `saveVenta` la atrapa y guarda la venta.
- En `saveDeliveryEstado` (PARA_ENTREGA), un error de impresión ahora llega enseguida. Si el cajero reintenta,
  puede crear una segunda factura: eso ya pasaba después del timeout de hoy, pero ahora el reintento es más
  probable. Queda para otra issue.
- El central tiene las mismas dos clases y el mismo campo reutilizado: conviene una issue de seguimiento.
- Ya existe una carrera entre cajas por los campos compartidos (`printerOutputStream`) de los resolvers. Queda fuera
  de alcance.

## Prueba de runtime (28/10)

Jar de la rama y jar actual de alpha (5.0.0-alpha.60), en el 8092 contra `general` local, misma secuencia y sin
reiniciar entre pasos. Cola `zz_rechaza` que no acepta trabajos y `zz_ok` hacia una impresora falsa local.

| Paso | alpha.60 | rama |
|---|---|---|
| 1. Reimprimir factura → cola que rechaza | se cuelga (timeout) | responde en 4 s |
| 2. Reimprimir factura → cola que acepta | se cuelga (stream muerto reutilizado) | imprime (16279 bytes) |
| 3. Reimprimir ticket sin factura → cola que rechaza | se cuelga | error inmediato |
| 4. Reimprimir ticket sin factura → cola que acepta | imprime | imprime (6630 bytes) |
| Hilos colgados al final (`jstack`) | 3 | 0 |

Build: `./mvnw clean verify`, 655 tests.

## Riesgo y vuelta atrás

- **Riesgo:** bajo. Toca las dos salidas de impresión y una línea en dos métodos de `FacturaLegalGraphQL`; no hay
  base ni API.
- **Vuelta atrás:** revertir el commit.
