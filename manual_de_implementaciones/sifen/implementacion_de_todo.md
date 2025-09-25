# Plan de Implementación: Facturación Electrónica Asíncrona y Offline

Este documento detalla los pasos necesarios para refactorizar la integración con SIFEN, implementando un sistema robusto, asíncrono y con capacidad de operación offline.

## Fase 1: Modelo de Datos y Migración

- [x] **Crear Enum `EstadoDE`**: Definir un `Enum` para los estados del Documento Electrónico (`PENDIENTE`, `EN_LOTE`, `APROBADO`, `RECHAZADO`, `CANCELADO`).
- [x] **Crear Enum `EstadoLoteDE`**: Definir un `Enum` para los estados del Lote (`PENDIENTE_ENVIO`, `EN_PROCESO`, `PROCESADO`, `PROCESADO_CON_ERRORES`, `ERROR_ENVIO`, `ERROR_PERMANENTE`).
- [x] **Crear Entidad `LoteDE`**:
    - [x] `id` (PK)
    - [x] `estado` (Enum `EstadoLoteDE`)
    - [x] `fechaCreacion`, `fechaUltimoIntento`, `fechaProcesado` (Timestamps)
    - [x] `intentos` (Integer)
    - [x] `respuestaSifen` (TEXT)
    - [x] `protocolo` (String)
    - [x] `@OneToMany` relación con `DocumentoElectronico`
- [x] **Modificar Entidad `DocumentoElectronico`**:
    - [x] Cambiar el campo `estado` de `String` a `Enum EstadoDE`.
    - [x] Añadir la relación `@ManyToOne` con `LoteDE`.
- [ ] **Crear Script de Migración Flyway**:
    - [ ] Crear la tabla `lote_de`.
    - [ ] Añadir la columna `lote_de_id` (FK) a `documento_electronico`.
    - [ ] Modificar la columna `estado` en `documento_electronico` para el nuevo tipo (puede requerir un paso intermedio o ser manejado por el ORM).

## Fase 2: Configuración y Lógica Central

- [ ] **Añadir Propiedades de Configuración** (`application.properties`):
    - [ ] `sifen.scheduler.fixed-delay`: Intervalo de ejecución del scheduler.
    - [ ] `sifen.lote.max-size`: Tamaño máximo de lote (ej: 50).
    - [ ] `sifen.lote.max-retries`: Número máximo de reintentos por lote.
- [ ] **Crear `SifenLoteService`**:
    - [ ] `crearLotesConDocumentosPendientes()`: Lógica para agrupar DEs `PENDIENTE` en nuevos lotes (`PENDIENTE_ENVIO`), respetando el tamaño máximo y el orden FIFO. Debe ser transaccional.
    - [ ] `enviarLote(LoteDE lote)`: Lógica para enviar un lote a SIFEN.
        - Manejar respuesta `0300` -> cambiar estado a `EN_PROCESO`.
        - Manejar rechazos -> cambiar estado a `RECHAZADO`.
        - Manejar errores de comunicación -> incrementar `intentos` y cambiar estado a `ERROR_ENVIO`. Si `intentos` > `max-retries`, cambiar a `ERROR_PERMANENTE`.
    - [ ] `consultarResultadoLote(LoteDE lote)`: Lógica para consultar el resultado de un lote `EN_PROCESO`.
        - Actualizar el estado de cada DE interno a `APROBADO` o `RECHAZADO` según la respuesta.
        - Actualizar el estado del lote a `PROCESADO` o `PROCESADO_CON_ERRORES`.

## Fase 3: Procesamiento en Segundo Plano

- [ ] **Crear `SifenSchedulerService`**:
    - [ ] Implementar el método principal `@Scheduled` (`procesarCicloSifen`).
    - [ ] Invocar a `sifenLoteService.crearLotesConDocumentosPendientes()`.
    - [ ] Obtener y recorrer lotes en estado `PENDIENTE_ENVIO` o `ERROR_ENVIO` para invocar a `sifenLoteService.enviarLote(lote)`. Implementar lógica de backoff exponencial si es posible.
    - [ ] Obtener y recorrer lotes en estado `EN_PROCESO` para invocar a `sifenLoteService.consultarResultadoLote(lote)`.
- [ ] **Añadir Logging Detallado**: Implementar logs exhaustivos en todo el proceso del scheduler para facilitar la depuración.

## Fase 4: Refactorización y Puesta en Marcha

- [ ] **Refactorizar `SifenService`**:
    - [ ] Modificar `generarDocumentoElectronico` para que su responsabilidad principal sea crear la entidad `DocumentoElectronico` en la base de datos con estado `PENDIENTE`.
    - [ ] Mantener un parámetro opcional `envioInmediato` que cree y envíe un lote de un solo elemento de forma síncrona, pero reutilizando la lógica de `SifenLoteService`.
- [ ] **Revisar Transaccionalidad**: Asegurar que todas las operaciones críticas que modifican la base de datos estén correctamente anotadas con `@Transactional`.

## Consideraciones Futuras

- [ ] **Dashboard de Monitoreo**: Crear una interfaz simple para visualizar el estado de los lotes y los documentos, permitiendo identificar rápidamente problemas.
- [ ] **Alertas**: Configurar un sistema de alertas (ej: por email o Slack) cuando un lote alcance el estado `ERROR_PERMANENTE`.
- [ ] **Acciones Manuales**: Implementar endpoints de administración para poder re-procesar manualmente un lote fallido.
