# Hotfix: la 5.5.1 no puede generar el XML del DE (saaj-impl 1.5.3)

Rama `hotfix/sifen-saaj-impl-1-5-1`, desde `master` (v5.5.1). PR a `master`; después, PR `master → develop`.

## Problema

Desde el 2026-11-02 10:00, toda venta con factura electrónica en las filiales de bodega con la
5.5.1 falla: `SifenService.crearDocumentoElectronico` → `DocumentoElectronico.generarXml` →
`setIdAttributeNode` tira `DOMException: NOT_FOUND_ERR`, y `saveVenta` se revierte entera
(«Transaction silently rolled back»). En la filial 1: 12 intentos, 0 facturas entre 10:00 y 10:15.

## Causa

`saaj-impl` llega de rebote por `jsifenlib`: el pom de la fork 0.2.4-frc.13 declara la **1.5.3**
(runtime). El BOM de Boot 2.1 la bajaba a **1.5.0**; el de Boot 2.7 (#202) ya no, y la 1.5.3 rompe
`setIdAttributeNode` (confirmado con los JAR de 5.5.0 y 5.5.1 en la filial 1). Deuda: bajarla en
la fork `GabFrank/rshk-jsifenlib`, para que no dependa del BOM de quien la use.

Afecta también a los eventos (cancelación, inutilización, conformidad): `TrGesEve.setupSOAPElements`
usa la misma llamada. El central declara `saaj-impl` 1.5.1
explícita y emite sin problema.

Ningún test lo detectó: los de SIFEN reemplazan `crearDocumentoElectronico`, y el ensayo del DE
(#170) arma el DE pero no llama a `generarXml`.

## Cambio (una fase)

| Archivo | Cambio |
|---|---|
| `pom.xml` | dependencia explícita `com.sun.xml.messaging.saaj:saaj-impl:1.5.1`, igual que el central |
| `SifenGenerarXmlTest` (nuevo) | con un certificado autofirmado descartable (keytool en `@TempDir`): arma el DE con `generarDEDesdeFacturaDatosReales` y llama a `generarXml` (verifica `Id` = CDC y `SignatureValue`), y arma y firma un evento de cancelación como `SifenEventoService` |

Efecto lateral, igual al central: `javax.xml.soap-api` 1.4.0 pasa adelante de `jakarta.xml.soap-api`
1.4.2 en el classpath (los dos ya estaban en master).

Sin migraciones, sin datos nuevos, sin cambios de API, sin variables de entorno.

## Tests

- `SifenGenerarXmlTest`: los dos casos **fallan** con el pom de `master` (`NOT_FOUND_ERR`, el mismo
  error de producción) y **pasan** con el arreglo (JDK 17).
- La release se arma con `-DskipTests` y `release.yml` no espera a `ci.yml`: **mergear solo con el
  check del PR en verde**.
- Batería completa `./mvnw clean verify` y verificación de que el JAR empaqueta `saaj-impl-1.5.1.jar`.

## Reversibilidad

Volver atrás es sacar la dependencia (vuelve la 1.5.3 y el error). La 5.5.0, que ya está corriendo
en las filiales tras el rollback manual, sigue siendo el respaldo.

Commit `fix(sifen): ...`: un `test:` o `chore:` no publica release y la 5.5.2 no saldría.

## Operación (fuera del repo)

- 2026-11-02 10:16–10:22: filiales 1, 3, 6, 8, 9, 10, 12 y 23 vueltas a 5.5.0, con el cron de
  `check-update.sh` comentado (`#PAUSA-5.5.1`). Las 8 arrancaron; la 1 facturó a las 10:19.
- **No descomentar el cron antes de que exista la 5.5.2**: `releases/latest` sigue siendo v5.5.1,
  que arranca bien (el health check pasa) y no factura. Resguardo propuesto: marcar v5.5.1 como
  pre-release en GitHub, así `latest` vuelve a ser v5.5.0 (a decidir por Franco).
- **Salida de la 5.5.2 de a una**: descomentar primero una filial (la 1), confirmar una venta con
  factura, su `documento_electronico` y el lote aprobado por SIFEN; recién ahí las otras 7. Las que
  tienen el cron activo (4, 7, 11) la toman solas: mirarlas igual.
- Si la 5.5.2 tampoco factura: mismo rollback de hoy (symlink `current` → `releases/5.5.0`,
  `.current-version`, restart) y volver a pausar el cron.
- Cierre: en cada una de las 8, `crontab -l | grep -c '#PAUSA-5.5.1'` = 0 y `.current-version` = 5.5.2.
- Sin tocar: 7 y 11 (sin red desde esta máquina) y 4 (Windows, pide contraseña). Quedan en 5.5.1
  hasta que llegue la 5.5.2.
- `develop` (alpha) tiene la misma dependencia rota: el PR `master → develop` va **apenas se
  mergee el hotfix**. Hasta entonces, las filiales alpha no facturan ni cancelan.

## Sin verificar

- Las otras dependencias que cambió el salto a Boot 2.7 y ningún test toca (mismo patrón): no se
  compararon árbol contra árbol.
- Envío real a SIFEN con el certificado de producción: el test firma con un certificado
  autofirmado. Se verifica con la primera venta facturada en una filial con 5.5.2.
