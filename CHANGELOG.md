# Changelog

Todos los cambios notables de este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [3.0.7-2] - 2025-11-26

### 🎯 Título del Release
**v3.0.7-2 - Implementación Completa de Facturación Electrónica SIFEN y Mejoras Significativas**

### 🚀 Principales Características
- ✅ **Facturación Electrónica SIFEN**: Implementación completa con creación/consulta de DE, lotes, scheduler automático, manejo de eventos y consulta de RUC
- ✅ **API REST para Facturas**: Nueva API para creación de facturas desde servidor externo
- ✅ **Sistema de Backup Automático**: Backup local y remoto con integración Google Drive
- ✅ **46 migraciones de base de datos** con optimizaciones e índices
- ✅ **60+ commits** con mejoras en facturación, delivery, consultas y correcciones críticas

### ⚠️ Notas Importantes
1. **Actualización de Base de Datos Requerida**: Ejecutar migraciones Flyway (V10 a V55)
2. **Configuración SIFEN**: Requiere configuración de credenciales y propiedades
3. **RabbitMQ Removido**: El soporte para RabbitMQ ha sido eliminado

### 📚 Resumen
- Implementación completa de facturación electrónica con integración SIFEN
- API REST para facturas legales
- Sistema de backup automático local y remoto
- Múltiples mejoras en facturación, delivery e impresión
- Correcciones de bugs y optimizaciones

Ver [changelog/3.0.7-2.md](changelog/3.0.7-2.md) para detalles completos.

## [3.0.7-1] - Versión anterior

Versión base del release anterior.

