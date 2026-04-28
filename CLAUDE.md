# FRC Filial Server

## Stack
- Java / Spring Boot
- Maven
- PostgreSQL

## Build & Run
- Build: `./mvnw clean package`
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test`

## Project Structure
- `src/main/java/` - Application source code
- `src/main/resources/` - Configuration and resources
- `src/test/` - Tests

## Conventions
- Follow existing code patterns and naming conventions
- Use Spanish for business domain terms as established in the codebase
- Write clear commit messages in English
# CLAUDE.md — frc-comercial/filial

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`franco-dev-systems-filial` (artifactId), branch-office (sucursal) server del producto **Franco Systems 3.0.9**. Es uno de los 4 componentes que forman `frc-comercial/` dentro del workspace `frc-sistemas-informaticos/`. Vive como repo git independiente (`GabFrank/franco-system-backend-filial`) y se sincroniza con el server central vía replicación lógica de PostgreSQL.

Stack: **Spring Boot 2.1.15 / Java 8 / PostgreSQL** + **GraphQL** (`graphql-java-kickstart`) + integración **SIFEN** (Paraguay) vía paquete `sifen/` propio (no comparte con `frc-efact`).

Package root: `com.franco.dev`. Layout estándar: `config/ controller/ domain/ dto/ graphql/ print/ repository/ security/ service/ sifen/ udpTest/ utilitarios/`.

## Build & Run

```bash
./mvnw spring-boot:run                    # Run con profile activo (ver application.properties)
./mvnw clean package                      # Build → target/franco-dev-systems-filial-3.0.9.jar
./mvnw test                               # Tests
```

El JAR final se llama `frc-filial-server.jar` (configurado vía `<jar.finalName>`) — **no cambiar** este nombre, los scripts de auto-update de las filiales en producción lo esperan literal (ver `cicd-implementation/scripts/check-update.{sh,ps1}`, `start-filial.{bat,ps1}`).

## ⚠️ Overrides locales: NO tocar `application-dev.properties`

Este repo tiene un mecanismo dedicado para que cada desarrollador pueda overridear configuración por máquina **sin meter diffs personales en git**. Usalo siempre que necesites cambiar paths absolutos, credenciales DB locales, certificados, puertos, etc.

### Cómo funciona

1. La clase `com.franco.dev.config.UserDevPropertiesEnvironmentPostProcessor` (registrada en `META-INF/spring.factories`) corre como `EnvironmentPostProcessor` muy temprano en el startup.
2. **Solo** cuando el profile `dev` está activo, carga `src/main/resources/application-user-dev.properties` desde el classpath.
3. Lo inserta con `addFirst()` → **prioridad máxima**, sobreescribe cualquier valor que venga de `application.properties` o `application-dev.properties`, incluso valores resueltos con `${VAR:default}`.
4. El archivo `application-user-dev.properties` está **ignorado por git** vía `**/application-user-*.properties` en `.gitignore` (línea 48). `git ls-files` lo confirma — no está trackeado.

### Cuándo usarlo

- Path absoluto al certificado SIFEN (`.pfx`) en tu máquina
- URL/usuario/password de tu PostgreSQL local
- Puerto del server distinto al default
- Cualquier IP/hostname específico de tu red local (servidor central de pruebas, etc.)
- Override de feature flags para debugging puntual

### Cómo usarlo

Editá `src/main/resources/application-user-dev.properties`. El archivo ya viene con líneas de ejemplo comentadas para los casos más comunes (DB personal, ruta del certificado, contraseña, IP del servidor central, sucursalId). Descomentá y adaptá. Al startup vas a ver en los logs:

```
✓ User-specific development properties loaded from: application-user-dev.properties (N properties loaded)
  → SIFEN certificate path will be: /Users/.../tu-cert.pfx
```

Si esa línea no aparece, o estás corriendo sin profile `dev` activo, o el archivo no existe.

### Por qué este mecanismo existe (no inventar otro)

Antes los devs editaban `application-dev.properties` directamente con sus paths personales y los pusheaban por error a `develop`. **`develop` dispara release alpha automática vía semantic-release**, así que cada push accidental de configuración local rompía el dev de otra persona y generaba un release tag con paths basura. Por eso se introdujo este mecanismo dedicado. **No editar `application-dev.properties` para cambios locales — siempre usar `application-user-dev.properties`.**

## CI/CD

Mismo modelo que el central. Ver guía consolidada [../../cicd-implementation/guia-desarrollo-cicd.md](../../cicd-implementation/guia-desarrollo-cicd.md) para el detalle completo.

### Branches

- Tres branches long-lived: `develop` (alpha) → `release/beta` (beta) → `master` (stable)
- Este repo usa **`master`, no `main`**, y **`release/beta` long-lived**. Ambas protegidas con `enforce_admins=true` — siempre PR.
- Branch naming: `feature/modulo-descripcion`, `fix/modulo-descripcion`, `refactor/modulo-descripcion`, `chore/descripcion`, `hotfix/descripcion`. Minúsculas, guiones, sin acentos ni espacios.
- `feature/*`, `fix/*`, etc. salen siempre de `develop`. **`hotfix/*` sale de `master`.**

### Releases automáticos

- `semantic-release` lee commits convencionales: `feat:` → minor, `fix:` → patch, `feat!:` o `BREAKING CHANGE:` → major. `chore:`/`refactor:`/`ci:`/`docs:`/`test:`/`perf:` no liberan.
- **Promoción `release/beta → master`: merge commit, NO squash.**
- Push a cualquiera de las 3 branches dispara release. **Nunca pushear sin confirmación explícita del usuario.**

### ⚙️ Deploy: AUTOMÁTICO cada 15 minutos

A diferencia del central (deploy manual), **el filial se actualiza solo**. Cada filial corre un script (`check-update.sh` en Linux / `check-update.ps1` en Windows con Task Scheduler) **cada 15 minutos** que:

1. Consulta GitHub Releases del canal configurado (alpha / beta / stable).
2. Si hay versión nueva, descarga el asset `frc-filial-server.jar` del release.
3. Lo copia localmente como `frc-server.jar` (nombre que conoce el servicio systemd / WinSW).
4. Reinicia el servicio.

**Implicancias:**

- **Cualquier merge a `develop` se va a propagar automáticamente** a todas las filiales en canal alpha en los próximos 15 minutos. No hace falta deploy manual para que llegue. **Nunca pushear sin confirmación explícita del usuario.**
- El nombre del JAR del release **debe** ser exactamente `frc-filial-server.jar` — el script lo busca por nombre literal. Por eso `<jar.finalName>` no se cambia.
- Los scripts de auto-update viven en [../../cicd-implementation/scripts/](../../cicd-implementation/scripts/) (`check-update.sh`, `check-update.ps1`, `start-filial.bat`, `start-filial.ps1`).

### Hotfix flow (urgencia en producción)

1. `git checkout master && git pull` → branch desde **master**
2. `git checkout -b hotfix/descripcion`
3. Fix + commit `fix(modulo): ...` + push
4. PR `hotfix/* → master`, CI verde, merge → semantic-release genera versión de producción
5. Las filiales en canal `stable` la van a tomar automáticamente en los próximos 15 minutos
6. **Inmediatamente después: PR `master → develop`** para que `develop` tenga el fix. **Nunca dejar un hotfix solo en `master`.**

## ⚠️ CRÍTICO: Migraciones Flyway — siempre aditivas

Esta es la regla más importante para este repo. Una migración mal hecha puede dejar el sistema inoperativo y **el rollback automático NO la revierte** (Flyway no hace down migrations; rollback de JAR no rollback de DB). En el filial el riesgo es aún mayor porque las actualizaciones son automáticas — un mal merge a `develop` se propaga a todas las filiales alpha en 15 minutos.

### Por qué importa

```
Ejemplo de desastre:
1. Versión 3.1.0: V5__... hace DROP COLUMN telefono
2. Filial recibe 3.1.0 vía auto-update → funciona (ya no usa telefono)
3. Bug crítico → merge de revert a develop, nueva versión 3.1.1
4. Filial recibe 3.1.1 con código que SÍ lee telefono → CRASH
5. Sistema caído. Restore manual de DB en CADA filial afectada.
```

### Permitido vs Prohibido

| ✅ PERMITIDO | ❌ PROHIBIDO |
|---|---|
| `CREATE TABLE` | `DROP TABLE` |
| `ALTER TABLE ADD COLUMN` (nullable o con default) | `ALTER TABLE DROP COLUMN` |
| `CREATE INDEX` | `ALTER TABLE RENAME COLUMN` |
| `ALTER TABLE ALTER COLUMN SET DEFAULT` | `ALTER TABLE ALTER COLUMN TYPE` (cambiando tipo) |
| `INSERT INTO` (datos de referencia / catálogos) | `DELETE FROM` / `TRUNCATE` |

### Eliminar o renombrar columnas: estrategia de 2 versiones

**Versión N (preparación):** crear la columna nueva, el código deja de leer la vieja y empieza a usar la nueva. Ambas coexisten.
```sql
-- V5__deprecar_campo_telefono.sql
ALTER TABLE clientes ADD COLUMN telefono_nuevo VARCHAR(20);
```

**Versión N+1 (limpieza, solo cuando N está estable en producción):**
```sql
-- V8__eliminar_campo_telefono_viejo.sql
ALTER TABLE clientes DROP COLUMN telefono;
ALTER TABLE clientes RENAME COLUMN telefono_nuevo TO telefono;
```

### Reglas de naming

- Formato: `V{numero}__{descripcion_con_underscores}.sql`
- Numeración secuencial y única. **Nunca reusar** un número.
- **Nunca modificar una migración ya aplicada.** Flyway compara checksums y falla.
- Probar localmente:
  ```bash
  SPRING_PROFILES_ACTIVE=ci ./mvnw clean verify
  ```

### Checklist obligatorio para PR con cambio de DB

- [ ] Migración versionada (`V{n}__...sql`) con número único
- [ ] Probada localmente con `SPRING_PROFILES_ACTIVE=ci ./mvnw clean verify`
- [ ] Es **retrocompatible** con la versión anterior del backend
- [ ] No hace `DROP`/`RENAME`/cambio de tipo sin la estrategia de 2 versiones
- [ ] La descripción del PR documenta el impacto en DB y el plan de rollback

## Otros cambios con riesgo de rollback

### Variables de entorno nuevas

Si tu código necesita una variable nueva (`API_KEY_NUEVA`), **avisar al líder técnico ANTES de crear el PR**. Como el deploy es automático, si la variable no existe en las filiales **antes** de que llegue el JAR nuevo, el servicio falla al arrancar y queda caído hasta intervención manual.

### Carpetas locales en el server filial

Si tu código espera que exista una carpeta en disco, debe crearla programáticamente con `Files.createDirectories()`. **No asumir** que las filiales la tienen preconfigurada — cada filial tiene su propio sistema de archivos.

### Cambios en la API GraphQL

Si modificás un resolver/schema que el desktop o mobile consumen:

1. **No eliminar campos de golpe.** Agregar el nuevo, mantener el viejo.
2. Recién cuando todos los clientes estén actualizados, eliminar el viejo en versión posterior.
3. Si es inevitable: `feat!: ...` (breaking change → MAJOR). Implica que filiales + desktop + mobile se actualicen coordinadamente.

## Pull Requests

- **Tamaño**: idealmente menos de 400 líneas de cambio neto. Una responsabilidad por PR.
- **Descripción del PR debe incluir**: qué resuelve, cómo probarlo, impacto en DB (si aplica), impacto en rollback (si aplica), riesgo (bajo/medio/alto).
- **Sin commits "WIP"** al mergear.
- **Revisar impacto cross-proyecto**: si cambiás un endpoint, verificar que el desktop/mobile no se rompen.

## Lo que NUNCA hacer

1. Push directo a `master`, `release/beta` o `develop` — siempre vía PR
2. `git push --force` a ramas compartidas
3. Modificar migraciones de Flyway ya aplicadas
4. `DROP TABLE/COLUMN` sin la estrategia de 2 versiones
5. Commitear secretos (`.env`, keystores, tokens, certificados `.pfx`)
6. Squash merge en PRs — usar **merge commit**
7. Pushear los viernes (auto-update propaga en 15 min, sin nadie monitoreando)
8. Saltear el CI con `--no-verify` — si falla, corregir, no buscar bypass
9. Cambiar `<jar.finalName>` (`frc-filial-server.jar`) sin coordinar con el equipo y actualizar los scripts de auto-update primero
10. Asumir que la DB de las filiales tiene el mismo estado — son N instancias independientes

## Convenciones

- **Idioma de dominio:** español (`razon_social`, `numero_factura`, etc.). Identificadores genéricos en inglés (`id`, `username`).
- **Package root:** `com.franco.dev` (compartido con `central`). No mezclar con `com.frcefact` (ese es otro proyecto, `frc-efact`).
- **GraphQL, no REST.** Endpoints nuevos van en `graphql/` (resolvers + schema), no `controller/`.
- **JAR finalName:** `frc-filial-server.jar` — no renombrar.
- **Scripts de auto-update** en producción esperan que el asset del GitHub Release se llame `frc-filial-server.jar` y lo copian localmente como `frc-server.jar` (nombre que conoce WinSW/systemd).

## Referencias relacionadas

- [../../REPORTE_VULNERABILIDADES.md](../../REPORTE_VULNERABILIDADES.md) — Auditoría 2026-04-02. Tiene hallazgos críticos abiertos en código de auth de **este** repo (plaintext passwords en `TokenController.java`, password en JWT claims en `JwtGenerator.java`). Leer antes de tocar `security/`.
- [../../CLAUDE.md](../../CLAUDE.md) — Mapa cross-project del workspace (los 4 componentes + frc-efact + sifen).
- [../central/CLAUDE.md](../central/CLAUDE.md) — Server central, usa el mismo mecanismo de `application-user-dev.properties`.

## Automated Issue Resolution (Claude Code Action)

Este repo esta configurado para resolucion automatizada de issues via Jira + Claude Code.

### Branch naming
Crear desde `develop`: `auto/{jira-key}-{slug}`
- `{jira-key}`: Jira key en minusculas (ej: `frc-42`)
- `{slug}`: max 40 chars, minusculas, solo hyphens, del titulo del issue
- Ejemplo: `auto/frc-42-fix-validacion-ruc`

### Commit format
`fix(scope): descripcion en minusculas` o `feat(scope): descripcion`
- Scope: modulo afectado (ej: `clientes`, `ventas`, `auth`)
- Max 72 chars en subject
- Referenciar Jira key en el body del commit

### Preflight: correr tests antes de abrir PR
`./mvnw clean verify -B`

Si los tests fallan, NO abrir PR — comentar en el issue explicando el fallo.

### PR rules
- SIEMPRE draft: nunca PR ready-for-review
- Target: `develop`
- Titulo: Conventional Commits con Jira key (ej: `fix(clientes): validacion RUC [FRC-42]`)
- Body: que cambio, como testear, impacto DB, riesgo rollback
- NUNCA mergear — requiere review humano
- NUNCA push directo a `master`, `release/beta`, o `develop`

### Archivos que NO tocar
- Secretos, `.env`, keystores, certificados
- Migraciones Flyway ya aplicadas
- `DROP TABLE`, `DROP COLUMN`, `RENAME COLUMN` sin estrategia 2 versiones
- Nombre de artefacto `frc-filial-server.jar`
- Codigo de auth en `security/TokenController.java` o `security/jwt/JwtGenerator.java` (ver REPORTE_VULNERABILIDADES.md)
