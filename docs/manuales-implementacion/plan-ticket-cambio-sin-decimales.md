# Ticket en moneda extranjera: cambio en guaraníes sin decimales

## Problema

El ticket 58mm de factura en moneda extranjera (`printTicket58mmFacturaMonedaExtranjera`) imprime
`Cambio: 6500,00 Gs/DS`. El cambio está en guaraníes, que no llevan decimales: debe salir
`Cambio: 6.500 Gs/DS`.

## Fase única

- `FacturaLegalGraphQL.java`: `String.format(Locale.GERMAN, "%.2f", tipoCambio)` →
  `"%,.0f"` (separador de miles `.`, sin decimales, redondeo HALF_UP).
- Los demás `%.2f` del archivo son montos en Rs/Ds y quedan como están.
- Tests: no hay test del ticket; se verifica el formato con `jshell` y se imprime un ticket real.

## Auditoría (paso 5, hecha inline: el cambio es un literal de formato)

- **Eje A (¿a quién le llega?)**: los 4 llamadores (`FacturaLegalGraphQL` x2, `TicketEscposGraphQL`,
  `FacturaLegalApiService`) imprimen el mismo ticket; no cambia ninguna firma, esquema GraphQL ni
  dato persistido. Central, desktop y PWA: N/A, no tocan este texto.
- **Eje B (reversibilidad)**: solo cambia texto impreso; revertir es revertir el commit. Riesgo:
  un cambio con fracción (p. ej. 7350,50) se imprime redondeado (7.351); en guaraníes es lo
  esperado.

## Datos nuevos

Ninguno.

## Sin verificar

- Que el ancho de la línea siga entrando en 58mm: se acorta, así que no empeora.
