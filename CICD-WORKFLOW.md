# CI/CD Workflow - FRC Filial Server

> **NOTA:** Este repo usa `master` (no `main`). Los workflows escuchan ambas ramas.

## Flujo General

```
feature/* --PR--> develop (alpha) --merge--> release/next (beta) --merge--> master (stable)
                     |                           |                             |
               semantic-release            semantic-release              semantic-release
               v2.5.0-alpha.1             v2.5.0-beta.1                   v2.5.0
                     |                           |                             |
                 GitHub Release             GitHub Release               GitHub Release
                     |                           |                             |
              Auto-update filiales       Auto-update filiales        Auto-update filiales
              (canal: alpha)             (canal: beta)               (canal: stable)
```

## 1. Desarrollo (Alpha)

1. Crear rama desde `develop`:
   ```
   git checkout develop && git pull
   git checkout -b feature/agregar-sync-productos
   ```
2. Hacer commits con prefijos convencionales:
   - `feat: agregar sincronizacion de productos` -- genera bump **minor**
   - `fix: corregir reconexion a base de datos` -- genera bump **patch**
   - `chore:`, `ci:`, `docs:` -- **NO generan release**
3. Crear **PR** hacia `develop` (nunca push directo, `enforce_admins=true`).
4. Al mergear el PR, `semantic-release` genera una release alpha (ej: `v2.5.0-alpha.1`).
5. Eliminar la rama feature despues del merge.

## 2. Promocion a Beta

1. Mergear `develop` en `release/next`:
   ```
   git checkout release/next && git pull
   git merge develop
   git push
   ```
2. `semantic-release` genera una release beta (ej: `v2.5.0-beta.1`).

## 3. Promocion a Stable (Produccion)

> **IMPORTANTE: Usar MERGE COMMIT, NO squash.**
>
> Si se hace squash con mensaje `chore: merge release/next into master`, semantic-release
> solo ve un commit `chore:` y hace un patch bump (o ninguno). Se debe usar **merge commit**
> para que semantic-release vea los commits originales `feat:` y `fix:` y calcule
> correctamente el bump de version.

1. Crear PR de `release/next` hacia `master`.
2. Mergear con **"Create a merge commit"** (NO "Squash and merge").
3. `semantic-release` genera la release estable (ej: `v2.5.0`).

## 4. Deploy (Automatico via Auto-Update)

Las filiales se actualizan **automaticamente** cada 15 minutos. No requiere intervencion manual.

### Mecanismo

Cada filial ejecuta un script de auto-update via cron (Linux) o Task Scheduler (Windows):

| SO      | Script              | Ubicacion                           |
|---------|---------------------|-------------------------------------|
| Linux   | `check-update.sh`   | `/opt/frc-filial/check-update.sh`   |
| Windows | `check-update.ps1`  | `C:\frc-filial\check-update.ps1`    |

### Archivo `.channel`

Cada filial tiene un archivo `.channel` que determina que releases recibe:

```
# Opciones: alpha, beta, stable
echo "stable" > /opt/frc-filial/.channel       # Linux
Set-Content C:\frc-filial\.channel "stable"     # Windows
```

| Canal    | Recibe                                  |
|----------|-----------------------------------------|
| `alpha`  | Prereleases con `-alpha` en el tag      |
| `beta`   | Prereleases con `-beta` en el tag       |
| `stable` | Solo releases estables (latest)         |

### Proceso de actualizacion

1. El script consulta GitHub Releases API segun el canal configurado.
2. Compara con la version actual (`.current-version`).
3. Si hay version nueva: descarga el JAR, actualiza el symlink `current/`, reinicia el servicio.
4. Ejecuta health check (`/actuator/health`, timeout 120s).
5. Si el health check falla: **rollback automatico** a la version anterior.
6. Notifica a GitHub Deployments API el resultado (exito o fallo).

### Archivos de configuracion por filial

| Archivo            | Contenido                                |
|--------------------|------------------------------------------|
| `.channel`         | `alpha`, `beta`, o `stable`              |
| `.github-token`    | Token de GitHub con acceso a releases    |
| `.filial-id`       | Identificador de la filial (ej: `suc-01`)|
| `.env`             | Variables de entorno (SERVER_PORT, etc.) |
| `.current-version` | Version actualmente instalada            |

## 5. Hotfix en Beta

Si hay un bug critico en beta:

1. Crear rama desde `release/next`:
   ```
   git checkout release/next && git pull
   git checkout -b fix/corregir-timeout-sync
   ```
2. Crear PR hacia `release/next` con prefijo `fix:`.
3. Al mergear, se genera nueva release beta. Las filiales en canal `beta` se actualizan automaticamente.

## Prefijos de Commits

| Prefijo  | Bump    | Ejemplo                                    |
|----------|---------|--------------------------------------------|
| `feat:`  | minor   | `feat: agregar modulo de inventario`       |
| `fix:`   | patch   | `fix: corregir validacion de RUC`          |
| `chore:` | ninguno | `chore: actualizar dependencias`           |
| `ci:`    | ninguno | `ci: agregar step de cache en workflow`     |
| `docs:`  | ninguno | `docs: documentar API de reportes`         |

## Proteccion de Ramas

- `master` y `develop`: `enforce_admins=true`, requieren PR, no push directo.
- Siempre usar PRs, incluso siendo administrador.
