# Plan — Facturar a receptor extranjero (filial #134)

Issue: GabFrank/franco-system-backend-filial#134. Rama `fix/sifen-receptor-extranjero` desde `develop`
en filial y desktop. **Etapa 1 = filial + desktop. Central = etapa 2, issue aparte.**

## Diagnóstico verificado (paso 3)

1. **Rechazo 0160 `'Otro'`**: el fork `jsifenlib 0.2.4-frc.15` escribe
   `dDTipIDRec = coalesce(iTipIDRec.getDescripcion(), dDTipIDRec)` (`TgDatRec.java:52`). Con `OTRO` sale
   siempre `"Otro"`, que el XSD rechaza. Hoy **todo** documento que no son 6-8 dígitos (con letras, 9+
   dígitos o 1-5 dígitos) cae en `OTRO` y rebota. `PASAPORTE`/`CEDULA_EXTRANJERA` sacan descripciones
   válidas. **No se emite nunca más `OTRO`.** El fork no se toca.
2. **B2F**: MT v150 (copia Scribd): `iTiOpe=4` exige `iNatRec=2` y `cPaisRec≠PRY`; otro `iTiOpe` exige PRY.
   El extranjero va **B2F**. En la librería, `iTiOpe` solo cambia el `gDatRec` (con 4 escribe siempre
   `dDirRec` + `dNumCasRec`, sin departamento/ciudad) y B2G en `TgCamFE`/`TgCamItem`. El #64 upstream ya
   está en el fork; el NPE del #78 es por `iTipIDRec` nulo, que nunca mandamos.
3. **Los clientes extranjeros nacen en la filial**: `FacturaLegalGraphQL.java:289-303` (factura con
   RUC/nombre tipeados) y `ClienteGraphQL.saveCliente` sin `personaId` crean la `Persona` localmente. El
   F8 (`cliente-para-factura-dialog`) solo busca: si no hay cliente, el cajero vende innominado (es el
   camino probable de los 6 «aprobados»; la consulta del anexo lo confirma).
4. **Réplica**: `central_pub` publica `persona` sin lista de columnas. Una columna nueva en el central
   viaja en **cualquier** escritura de `persona` y corta el apply de toda filial que no la tenga. En la
   filial sola, una columna de más **no** rompe nada (el suscriptor puede tener columnas extra, y un
   UPDATE del central no toca las que no publica).
5. `general.pais` tiene códigos mezclados (`PY`, `BR`, `ARG`, `US`).

## Decisiones

- **`personas.persona.pais_id` solo en la filial** (etapa 1). Nulo = Paraguay. Sin FK (convención del
  filial para tablas suscriptas, `V81.1`), y `@NotFound(IGNORE)` en `Persona.pais`: un id que la filial no
  tiene se trata como «sin país».
- **Extranjero** (país mapeado ≠ PRY) → `iNatRec=2`, `B2F`, `cPaisRec`, `iTipIDRec` = `PASAPORTE` si el
  documento tiene letras, `CEDULA_EXTRANJERA` si son solo dígitos; `dDirRec` = `direccion.trim()` o el
  nombre del país si está vacía. No se mira `tributa`.
  `shortcut: pasaporte por letras; uno solo numérico sale como cédula extranjera, que SIFEN acepta igual`.
- **Código de país → `PaisType`**: `trim().toUpperCase()`; 3 letras → `PaisType.valueOf`; 2 letras →
  `Locale.getISO3Country()`; cualquier fallo (`PAR`, `XX`, null) → excepción «país X sin código SIFEN».
  La comparación con PRY va después de mapear (`PY` → nacional).
- **Sin país / Paraguay**: 1-8 dígitos → `CEDULA_PARAGUAYA` (las cédulas viejas tienen menos de 6).
  Con letras o 9+ dígitos → `ReceptorSinPaisException` («cargue el país del cliente»): **la venta se rechaza** (`VentaNoFacturableException`, como el tope del innominado). Decidido por Franco el 19/11.
- **Más de un llamador del helper** en la filial: `SifenService.construirDatosReceptor`,
  `construirCamposFE` (segunda llamada), `SifenEventoService` (nominación) y el ensayo de
  `FacturaLegalBuilder`. La excepción sale con su mensaje en los cuatro; ninguno queda en loop (el
  reintento de DE hereda el error como hoy hereda el 0160).

## Tabla de datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `persona.pais_id` (filial) | `FacturaLegalGraphQL` al crear o encontrar la persona (`FacturaLegalInput.paisId`) y `ClienteGraphQL.saveCliente` (`ClienteInput.paisId`), desde `add-factura-legal-dialog` | `SifenReceptorHelper` (filial); `Persona.pais` en el schema del filial (desktop, al mostrar el cliente) |

## Fases

### Fase 1 — filial
- `V116.1__persona_pais.sql`: `ALTER TABLE personas.persona ADD COLUMN IF NOT EXISTS pais_id bigint;`
- `Persona.pais` (`@ManyToOne EAGER`, `@NotFound(IGNORE)`, `@JsonIgnore`).
- `persona.graphqls`: `pais: Pais` en `Persona`. `FacturaLegalInput.paisId` y `ClienteInput.paisId`
  (+ sus `.graphqls`), asignados si vienen; en `FacturaLegalGraphQL` también sobre la persona encontrada.
- `SifenReceptorHelper`: rama extranjero, `paisSifen`, `determinarTipoDocumento` sin `OTRO`,
  `ReceptorSinPaisException`, `ConfiguracionReceptor.dDirRec`.
- `SifenService.construirDatosReceptor`: `setdDirRec`.
- `FacturaLegalBuilder.ensayarDE`: `ReceptorSinPaisException` → `VentaNoFacturableException`.
- **Tests** (unitarios, sin contexto): helper: BR + 11 dígitos → B2F/BRA/CEDULA_EXTRANJERA/dDirRec;
  letras → PASAPORTE; `ARG`, `US`, `br`, `PY` (nacional), `PAR`/`XX`/null (error); `dDirRec` vacío o
  `"  "` → país; paraguayo 5 y 7 dígitos → cédula/B2C/PRY sin cambios; sin país con 11 dígitos o letras →
  `ReceptorSinPaisException`; innominado y contribuyente sin cambios. `construirDatosReceptor` copia
  `dDirRec` y el `gDatRec` serializado lleva `dDTipIDRec="Cédula extranjera"`. `ensayarDE` convierte la
  excepción en `VentaNoFacturableException`. Revertir el fix → fallan.

### Fase 2 — desktop
- `Persona`/`Cliente` model: `pais`; `paisId` en el input solo si está cargado (un filial viejo no ve la
  clave).
- `add-factura-legal-dialog` (cliente nuevo y cliente encontrado): selector **País** con Paraguay
  explícito como primera opción (para poder corregir), reusando `PaisService`/`buscarPais`.
- F8 (`cliente-para-factura-dialog`): sin cambios de flujo; el mensaje del rechazo le dice al cajero
  dónde cargar el país.
- `clientePorPersonaDocumento*`: pedir `pais { id descripcion }` **solo después** de que el filial del canal
  tenga el schema (va al filial en el PDV).

## Orden de despliegue

Por canal: filial (`develop` → alpha, `release/beta` → farmacia, `master` → bodega) **antes** que el
desktop del mismo canal. No hay migración en el central: nada que coordinar con la réplica.

## Pruebas de runtime (paso 9)

Filial propio 8082 (sucursal 24, política a pedido, SIFEN sin envío) + desktop `ng serve -c web`:
- Factura manual con cliente nuevo, país Brasil, `12345678901` → `xml_original` con `iTiOpe=4`,
  `cPaisRec=BRA`, `iTipIDRec=3`, `dDTipIDRec=Cédula extranjera`, `dDirRec`. Pasaporte `FX123456` → 2.
- El mismo cliente por F8 → mismo XML.
- Cliente sin país con `12345678901` → la venta se rechaza con el mensaje.
- Paraguayo de 7 dígitos y RUC contribuyente → XML idéntico al de `develop`.
- `gDatRec` validado contra `DE_v150.xsd` de ekuatia.

## Etapa 2 (issue aparte, central)

Copia del helper en el central, `SifenNotaCreditoBuilder`/`SifenNotaRemisionBuilder` (fijan PRY/B2C/cédula),
`pais` en el schema del central y en el alta de admin. Lleva migración sobre tabla publicada: el central
se despliega recién cuando **todas** las filiales de su canal tienen la columna (chequeo por
`information_schema.columns` en cada una; nodos apagados como farmacia 5 o la 25 quedan en crash-loop
hasta migrar).

## Sin verificar / riesgos

- La regla «`iTiOpe≠4` exige PRY» sale de una copia del MT; el upstream #78 muestra un B2F aprobado.
- No hay SIFEN de prueba desde local: la primera factura B2F real es la verificación final. Conviene
  una venta controlada en alpha antes de beta.
- Reglas del servidor SIFEN para B2F en IVA/exportación: no relevadas.

## Anexo — consulta (bodega, solo lectura, la corre Franco): `q134.sql`

## Cambios durante la implementación (19/11)

- **La persona no se crea en la filial**: `PersonaService.save` la crea en el central por REST y la
  espera por réplica, y rechaza ediciones. El país se graba con `PersonaService.asignarPais`
  (UPDATE directo de `pais_id`). Verificado en un par publicación/suscripción de prueba que un UPDATE
  que baja del central no pisa una columna que solo tiene el suscriptor. El país queda **por sucursal**
  hasta que lo tenga el central (etapa 2).
- **Pasaportes con letras**: el campo documento del diálogo de factura era solo numérico; ahora acepta
  letras con un país extranjero (`[numericOnly]` apagable). Dos colisiones que eso destapó, corregidas:
  las búsquedas por documento probaban de nuevo con solo los dígitos (`FX987654` → `987654`), y la
  consulta de RUC a SET también (dio de alta al contribuyente del RUC 987654). Con letras ya no se
  reduce a dígitos ni se consulta SET: queda como no contribuyente.
- **Desktop**: el selector de país va solo en el modo PDV del diálogo de factura (contra el filial). En
  modo servidor la factura pasa por el central, que no conoce `paisId`: etapa 2. Al encontrar un cliente
  el país no se precarga (la query detallada también la usa el central); vacío no lo cambia.
- **F8**: sin cambios de pantalla. El cliente se elige en «Falta el cliente para facturar» y sale B2F si
  ya tiene país.
- Probado por pantalla (filial propio 8082 + central develop 8081 + desktop): CPF nuevo con Brasil →
  B2F/BRA/cédula extranjera; sin país → venta rechazada con el motivo; corrección en el mismo diálogo →
  B2F; cliente ya cargado sin tocar el selector → B2F; pasaporte → B2F/Pasaporte; paraguayo → B2C sin
  cambios; F8 con cliente extranjero → B2F.
- **Sin verificar**: validación contra el XSD de ekuatia (no descargado) y envío real a SIFEN.
