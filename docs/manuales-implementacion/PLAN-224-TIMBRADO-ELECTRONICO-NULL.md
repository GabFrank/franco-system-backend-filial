# Plan #224 — la factura no se imprime si el timbrado tiene `is_electronico` en null

Rama: `fix/factura-timbrado-electronico-null` (desde `origin/develop`, `e5d41c9`). Pieza: filial.

## Causa

`FacturaLegalGraphQL` compara `getTimbrado().getIsElectronico() != true` en
`printTicket58mmFactura` (línea 1024) y `printTicket58mmFacturaMonedaExtranjera` (línea 1655).
`Boolean null != true` hace unboxing y tira `NullPointerException`: la factura no se imprime.

Llegan ahí: PARA ENTREGA de un delivery, «Venta + Ticket», reimprimir venta, factura y delivery,
por la impresora del filial y por `ticketEscpos` (cliente).

Los demás usos de `getIsElectronico()` en el filial ya están protegidos (`!= null &&` en las líneas
1479 y 1850 del mismo archivo, `Boolean.TRUE.equals` en `FacturaLegalBuilder` y
`FacturaLegalApiService`). Las consultas de repositorio (`IsElectronicoTrue`) no hacen unboxing.

## Fase 1 — fix + tests (un commit)

- Las dos líneas pasan a `!Boolean.TRUE.equals(...getIsElectronico())`: null se trata como no
  electrónico, igual que en el resto del código (imprime las fechas de vigencia).
- Tests en `FacturaLegalTicketDestinoTest`: timbrado con `isElectronico` null en las dos
  variantes (guaraní y moneda extranjera); el ticket sale, lleva la línea «De … a …» y no el
  bloque del DE. Hecho: con el código viejo los dos fallan con la NPE del issue.

## Datos nuevos

Ninguno. Sin migración, sin cambios de GraphQL ni de clientes.

## Pruebas

- `./mvnw clean verify -B` (en una copia fuera de `dev-frc`, por jdtls).
- Runtime: filial propio en 8082 (perfil dev, SIFEN apagado) contra `general`, reimprimir por
  pantalla del desktop una factura con timbrado de `is_electronico` null (la 33137 del issue o la
  que exista), por la impresora del filial y por el cliente.

## Despliegue

Merge a `develop` → filiales alpha en ≤15 min con reinicio del servicio. Sin riesgo de rollback:
solo código, la versión anterior sigue funcionando igual.

## Auditoría del plan (paso 5)

- Eje A: sin cambio de contrato; el central no tiene el patrón (`!= null &&`). `getCredito() == false`
  (1045/1676) también hace unboxing, pero todos los caminos que crean la factura le ponen valor:
  fuera de alcance.
- Eje A y B: con null el ticket imprime «De fechaInicio a fechaFin»; si las fechas fueran null, la
  NPE se mudaría a `.format`. El central lo impide con `check_fechas_requeridas_no_electronico`
  (V69.1) y el timbrado solo nace allá. Verificado en local: ningún timbrado null sin fechas en
  `general` ni `bodega`.
- Eje B: el test fija además que con null no sale el bloque del DE (QR/consulta).
- Eje B: `FacturaService.java:147` imprime la vigencia sin mirar `isElectronico`; no hace unboxing
  (otro asunto, fuera de alcance).
- Canal: `develop` → filial alpha (mauro). Las filiales de bodega lo reciben recién con el release
  a `master`.

## Sin verificar

- Cuántos timbrados de producción tienen `is_electronico` null (el issue solo miró copias locales).
  No cambia el fix.
