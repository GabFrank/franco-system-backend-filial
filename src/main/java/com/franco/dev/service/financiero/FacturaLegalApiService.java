package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.dto.factura.CrearFacturaLegalRequestDTO;
import com.franco.dev.dto.factura.DisponibilidadTimbradoDetalleResponseDTO;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.sifen.service.SifenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class FacturaLegalApiService {

    private final TimbradoDetalleService timbradoDetalleService;
    private final FacturaLegalService facturaLegalService;
    private final FacturaLegalItemService facturaLegalItemService;
    private final ClienteService clienteService;
    private final SifenService sifenService;
    private final FacturaLegalGraphQL facturaLegalGraphQL;
    private final ObjectMapper objectMapper;

    /**
     * Verifica la disponibilidad de un timbrado detalle para emitir facturas.
     * 
     * @param timbradoDetalleId ID del timbrado detalle a verificar
     * @return Información sobre la disponibilidad del timbrado detalle
     */
    public DisponibilidadTimbradoDetalleResponseDTO verificarDisponibilidadTimbradoDetalle(Long timbradoDetalleId) {
        log.info("Verificando disponibilidad del timbrado detalle ID: {}", timbradoDetalleId);

        Optional<TimbradoDetalle> timbradoDetalleOpt = timbradoDetalleService.findById(timbradoDetalleId);

        if (!timbradoDetalleOpt.isPresent()) {
            return DisponibilidadTimbradoDetalleResponseDTO.builder()
                    .disponible(false)
                    .mensaje("El timbrado detalle no existe")
                    .timbradoDetalleId(timbradoDetalleId)
                    .build();
        }

        TimbradoDetalle timbradoDetalle = timbradoDetalleOpt.get();

        // Verificar que el timbrado detalle esté activo
        if (!Boolean.TRUE.equals(timbradoDetalle.getActivo())) {
            return DisponibilidadTimbradoDetalleResponseDTO.builder()
                    .disponible(false)
                    .mensaje("El timbrado detalle no está activo")
                    .timbradoDetalleId(timbradoDetalleId)
                    .activo(false)
                    .build();
        }

        // Verificar que el timbrado esté activo
        if (timbradoDetalle.getTimbrado() == null) {
            return DisponibilidadTimbradoDetalleResponseDTO.builder()
                    .disponible(false)
                    .mensaje("El timbrado detalle no tiene un timbrado asociado")
                    .timbradoDetalleId(timbradoDetalleId)
                    .build();
        }

        if (!Boolean.TRUE.equals(timbradoDetalle.getTimbrado().getActivo())) {
            return DisponibilidadTimbradoDetalleResponseDTO.builder()
                    .disponible(false)
                    .mensaje("El timbrado asociado no está activo")
                    .timbradoDetalleId(timbradoDetalleId)
                    .timbradoActivo(false)
                    .build();
        }

        // Verificar que el timbrado sea electrónico si SIFEN está habilitado
        Boolean esElectronico = Boolean.TRUE.equals(timbradoDetalle.getTimbrado().getIsElectronico());
        boolean sifenHabilitado = sifenService != null && sifenService.isSifenEnabled();

        if (sifenHabilitado && !esElectronico) {
            return DisponibilidadTimbradoDetalleResponseDTO.builder()
                    .disponible(false)
                    .mensaje("SIFEN está habilitado y se requiere un timbrado electrónico")
                    .timbradoDetalleId(timbradoDetalleId)
                    .esElectronico(false)
                    .build();
        }

        if (!sifenHabilitado && esElectronico) {
            return DisponibilidadTimbradoDetalleResponseDTO.builder()
                    .disponible(false)
                    .mensaje("SIFEN está deshabilitado y el timbrado es electrónico")
                    .timbradoDetalleId(timbradoDetalleId)
                    .esElectronico(true)
                    .build();
        }

        // Verificar disponibilidad de números
        Long numeroActual = timbradoDetalle.getNumeroActual() != null ? timbradoDetalle.getNumeroActual() : 0L;
        Long rangoHasta = timbradoDetalle.getRangoHasta() != null ? timbradoDetalle.getRangoHasta() : Long.MAX_VALUE;
        Long numerosDisponibles = rangoHasta - numeroActual;

        if (numerosDisponibles <= 0) {
            return DisponibilidadTimbradoDetalleResponseDTO.builder()
                    .disponible(false)
                    .mensaje("No hay números disponibles en el rango del timbrado")
                    .timbradoDetalleId(timbradoDetalleId)
                    .numeroActual(numeroActual)
                    .rangoHasta(rangoHasta)
                    .numerosDisponibles(0L)
                    .esElectronico(esElectronico)
                    .numeroTimbrado(timbradoDetalle.getTimbrado().getNumero())
                    .activo(true)
                    .timbradoActivo(true)
                    .build();
        }

        return DisponibilidadTimbradoDetalleResponseDTO.builder()
                .disponible(true)
                .mensaje("El timbrado detalle está disponible para emitir facturas")
                .timbradoDetalleId(timbradoDetalleId)
                .numeroActual(numeroActual)
                .rangoDesde(timbradoDetalle.getRangoDesde())
                .rangoHasta(rangoHasta)
                .numerosDisponibles(numerosDisponibles)
                .esElectronico(esElectronico)
                .numeroTimbrado(timbradoDetalle.getTimbrado().getNumero())
                .activo(true)
                .timbradoActivo(true)
                .build();
    }

    /**
     * Crea una factura legal sin estar vinculada a una venta.
     * 
     * @param request DTO con los datos de la factura
     * @return DTO con la información de la factura creada
     */
    @Transactional
    public com.franco.dev.dto.factura.CrearFacturaLegalResponseDTO crearFacturaLegal(
            CrearFacturaLegalRequestDTO request) {
        
        log.info("Creando factura legal para cliente: {} (RUC: {}), Timbrado: {}, Items: {}", 
                request.getNombre(), 
                request.getRuc(),
                request.getTimbradoDetalleId(),
                request.getItems() != null ? request.getItems().size() : 0);
        
        // Log detallado solo en modo debug
        if (log.isDebugEnabled()) {
            try {
                String requestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
                log.debug("DTO recibido:\n{}", requestJson);
            } catch (Exception e) {
                log.debug("No se pudo serializar el DTO a JSON: {}", e.getMessage());
            }
        }

        // Verificar disponibilidad del timbrado detalle
        DisponibilidadTimbradoDetalleResponseDTO disponibilidad = 
                verificarDisponibilidadTimbradoDetalle(request.getTimbradoDetalleId());

        if (!disponibilidad.getDisponible()) {
            throw new IllegalStateException("El timbrado detalle no está disponible: " + disponibilidad.getMensaje());
        }

        // Obtener el timbrado detalle
        TimbradoDetalle timbradoDetalle = timbradoDetalleService.findById(request.getTimbradoDetalleId())
                .orElseThrow(() -> new IllegalStateException("Timbrado detalle no encontrado"));

        // Crear la factura legal
        FacturaLegal facturaLegal = new FacturaLegal();
        facturaLegal.setTimbradoDetalle(timbradoDetalle);
        facturaLegal.setViaTributaria(request.getViaTributaria() != null ? request.getViaTributaria() : false);
        facturaLegal.setCredito(request.getCredito() != null ? request.getCredito() : false);
        facturaLegal.setNombre(request.getNombre());
        facturaLegal.setRuc(request.getRuc());
        facturaLegal.setDireccion(request.getDireccion());
        
        // Usar fecha actual del servidor para evitar problemas con fechas incorrectas del servidor externo
        // La fecha del request se puede usar para referencia, pero la fecha de emisión debe ser la actual
        LocalDateTime fechaActual = LocalDateTime.now();
        if (request.getFecha() != null) {
            log.debug("Fecha recibida del servidor externo: {}", request.getFecha());
            // Validar que la fecha no sea futura (máximo 1 hora de diferencia permitida para ajustes de zona horaria)
            if (request.getFecha().isAfter(fechaActual.plusHours(1))) {
                log.warn("⚠️  Fecha del request ({}) es futura, usando fecha actual del servidor ({})", 
                        request.getFecha(), fechaActual);
                facturaLegal.setFecha(fechaActual);
            } else {
                // Si la fecha es válida (pasada o presente), usarla
                log.debug("✅ Usando fecha del request: {}", request.getFecha());
                facturaLegal.setFecha(request.getFecha());
            }
        } else {
            log.debug("No se recibió fecha en el request, usando fecha actual: {}", fechaActual);
            facturaLegal.setFecha(fechaActual);
        }
        facturaLegal.setIvaParcial0(request.getIvaParcial0() != null ? request.getIvaParcial0() : 0.0);
        facturaLegal.setIvaParcial5(request.getIvaParcial5() != null ? request.getIvaParcial5() : 0.0);
        facturaLegal.setIvaParcial10(request.getIvaParcial10() != null ? request.getIvaParcial10() : 0.0);
        facturaLegal.setTotalParcial0(request.getTotalParcial0() != null ? request.getTotalParcial0() : 0.0);
        facturaLegal.setTotalParcial5(request.getTotalParcial5() != null ? request.getTotalParcial5() : 0.0);
        facturaLegal.setTotalParcial10(request.getTotalParcial10() != null ? request.getTotalParcial10() : 0.0);
        facturaLegal.setTotalFinal(request.getTotalFinal());
        facturaLegal.setDescuento(request.getDescuento() != null ? request.getDescuento() : 0.0);
        
        // Validar y configurar moneda extranjera
        validarYConfigurarMonedaExtranjera(request, facturaLegal);
        facturaLegal.setActivo(true);

        // Asignar sucursal desde el timbrado detalle
        if (timbradoDetalle.getSucursal() != null && timbradoDetalle.getSucursal().getId() != null) {
            facturaLegal.setSucursalId(timbradoDetalle.getSucursal().getId());
        } else {
            throw new IllegalStateException("El timbrado detalle no tiene una sucursal asignada");
        }

        // Asignar cliente si se proporciona
        if (request.getClienteId() != null) {
            clienteService.findById(request.getClienteId())
                    .ifPresent(facturaLegal::setCliente);
        }

        // Asignar caja si se proporciona
        if (request.getCajaId() != null) {
            // La caja se puede asignar aquí si es necesario en el futuro
            log.debug("Caja ID {} proporcionada pero no asignada (funcionalidad pendiente)", request.getCajaId());
        }

        // Incrementar número de factura
        Long numeroFactura = timbradoDetalle.getNumeroActual() != null 
                ? timbradoDetalle.getNumeroActual() + 1 
                : 1L;
        facturaLegal.setNumeroFactura(numeroFactura.intValue());

        // Guardar la factura legal
        FacturaLegal facturaLegalGuardada = facturaLegalService.save(facturaLegal);

        // Guardar los items y calcular totales
        Double totalParcial0 = 0.0;
        Double totalParcial5 = 0.0;
        Double totalParcial10 = 0.0;
        Double ivaParcial5 = 0.0;
        Double ivaParcial10 = 0.0;
        
        for (com.franco.dev.dto.factura.FacturaLegalItemDTO itemDTO : request.getItems()) {
            FacturaLegalItem item = new FacturaLegalItem();
            item.setFacturaLegal(facturaLegalGuardada);
            item.setDescripcion(itemDTO.getDescripcion());
            item.setCantidad(itemDTO.getCantidad().floatValue());
            item.setPrecioUnitario(itemDTO.getPrecioUnitario());
            
            // Calcular total del item si no viene
            Double totalItem = itemDTO.getTotal() != null 
                    ? itemDTO.getTotal() 
                    : itemDTO.getPrecioUnitario() * itemDTO.getCantidad();
            item.setTotal(totalItem);
            
            item.setUnidadMedida(itemDTO.getUnidadMedida());
            item.setIva(itemDTO.getIva());
            item.setSucursalId(facturaLegalGuardada.getSucursalId());

            // Asignar producto si se proporciona
            if (itemDTO.getProductoId() != null) {
                // El producto se puede asignar aquí si es necesario en el futuro
                log.debug("Producto ID {} proporcionado para item pero no asignado (funcionalidad pendiente)", 
                        itemDTO.getProductoId());
            }

            facturaLegalItemService.save(item);
            
            // Calcular totales de IVA basándose en los items
            Integer iva = itemDTO.getIva() != null ? itemDTO.getIva() : 10; // Default 10%
            if (iva == 10) {
                totalParcial10 += totalItem;
                ivaParcial10 += totalItem / 11.0; // IVA incluido: total / 11
            } else if (iva == 5) {
                totalParcial5 += totalItem;
                ivaParcial5 += totalItem / 21.0; // IVA incluido: total / 21
            } else {
                totalParcial0 += totalItem;
            }
        }
        
        // Validar y recalcular totales si es necesario
        boolean totalesIncorrectos = (request.getIvaParcial0() == null || request.getIvaParcial0() == 0.0)
                && (request.getIvaParcial5() == null || request.getIvaParcial5() == 0.0)
                && (request.getIvaParcial10() == null || request.getIvaParcial10() == 0.0)
                && (totalParcial10 > 0 || totalParcial5 > 0); // Hay items con IVA pero totales en 0
        
        if (totalesIncorrectos) {
            log.warn("⚠️  Los totales de IVA vienen en 0.0 pero hay items con IVA. Recalculando totales desde los items...");
            facturaLegalGuardada.setTotalParcial0(totalParcial0);
            facturaLegalGuardada.setTotalParcial5(totalParcial5);
            facturaLegalGuardada.setTotalParcial10(totalParcial10);
            facturaLegalGuardada.setIvaParcial5(ivaParcial5);
            facturaLegalGuardada.setIvaParcial10(ivaParcial10);
            
            // Recalcular total final
            Double totalFinalRecalculado = totalParcial0 + totalParcial5 + totalParcial10;
            facturaLegalGuardada.setTotalFinal(totalFinalRecalculado);
            
            log.info("✅ Totales recalculados - Total Parcial 0%: {}, 5%: {}, 10%: {}, IVA 5%: {}, IVA 10%: {}, Total Final: {}", 
                    totalParcial0, totalParcial5, totalParcial10, ivaParcial5, ivaParcial10, totalFinalRecalculado);
            
            // Guardar factura con totales recalculados
            facturaLegalGuardada = facturaLegalService.save(facturaLegalGuardada);
        } else {
            // Validar que los totales enviados sean consistentes (opcional, solo warning)
            Double totalCalculado = totalParcial0 + totalParcial5 + totalParcial10;
            Double totalEnviado = request.getTotalFinal();
            if (totalEnviado != null && Math.abs(totalCalculado - totalEnviado) > 0.01) {
                log.warn("⚠️  Diferencia entre total calculado ({}) y total enviado ({}). Usando total enviado.", 
                        totalCalculado, totalEnviado);
            }
        }

        // Actualizar número actual del timbrado detalle
        timbradoDetalle.setNumeroActual(numeroFactura);
        timbradoDetalleService.save(timbradoDetalle);

        // Si es factura electrónica, generar el documento electrónico
        Boolean esElectronica = Boolean.TRUE.equals(timbradoDetalle.getTimbrado().getIsElectronico());
        boolean documentoElectronicoGenerado = false;
        String cdc = null;
        String urlQr = null;
        String estadoDocumentoElectronico = null;
        String mensajeRespuestaSifen = null;

        if (esElectronica && sifenService != null && sifenService.isSifenEnabled()) {
            try {
                boolean esMonedaExt = esMonedaExtranjera(facturaLegalGuardada);
                if (esMonedaExt) {
                    log.info("Generando documento electrónico en moneda extranjera {} (cambio: {}) para factura ID: {}", 
                            facturaLegalGuardada.getMonedaExtranjera(), 
                            facturaLegalGuardada.getTipoCambio(), 
                            facturaLegalGuardada.getId());
                } else {
                    log.info("Generando documento electrónico para factura ID: {}", facturaLegalGuardada.getId());
                }
                
                // SifenService leerá automáticamente monedaExtranjera y tipoCambio de la factura
                com.franco.dev.domain.financiero.DocumentoElectronico de = 
                        sifenService.crearDocumentoElectronico(facturaLegalGuardada);
                
                facturaLegalGuardada.setCdc(de.getCdc());
                facturaLegalGuardada = facturaLegalService.save(facturaLegalGuardada);
                
                documentoElectronicoGenerado = true;
                cdc = de.getCdc();
                urlQr = de.getUrlQr();
                estadoDocumentoElectronico = de.getEstado() != null ? de.getEstado().toString() : null;
                mensajeRespuestaSifen = de.getMensajeRespuestaSifen();
                
                log.info("Documento electrónico generado exitosamente - CDC: {}", cdc);
            } catch (Exception e) {
                log.error("Error al generar documento electrónico para factura ID: {}", 
                        facturaLegalGuardada.getId(), e);
                mensajeRespuestaSifen = "Error al generar documento electrónico: " + e.getMessage();
                // No lanzamos excepción para no romper el guardado de la factura
            }
        }

        // Imprimir si se solicita
        if (request.getImprimir() != null && request.getImprimir() && request.getPrinterName() != null) {
            try {
                log.info("🖨️  Imprimiendo factura legal ID: {} en impresora: {}", 
                        facturaLegalGuardada.getId(), request.getPrinterName());
                
                // Verificar si es moneda extranjera
                boolean esMonedaExtranjera = esMonedaExtranjera(facturaLegalGuardada);
                
                // Obtener items de la factura
                List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(facturaLegalGuardada.getId());
                
                if (esMonedaExtranjera) {
                    // Imprimir en moneda extranjera
                    facturaLegalGraphQL.printTicket58mmFacturaMonedaExtranjera(
                            null, // No hay venta asociada
                            facturaLegalGuardada, 
                            items, 
                            request.getPrinterName(), 
                            facturaLegalGuardada.getMonedaExtranjera(), 
                            facturaLegalGuardada.getTipoCambio());
                } else {
                    // Imprimir normal
                    facturaLegalGraphQL.printTicket58mmFactura(
                            null, // No hay venta asociada
                            facturaLegalGuardada, 
                            items, 
                            request.getPrinterName());
                }
                
                log.info("✅ Factura impresa exitosamente");
                
            } catch (Exception e) {
                log.error("❌ Error al imprimir factura legal ID: {}", facturaLegalGuardada.getId(), e);
                log.error("   Detalle del error: {}", e.getMessage());
                // No lanzamos excepción para no romper el guardado de la factura
            }
        } else if (request.getImprimir() != null && request.getImprimir() && request.getPrinterName() == null) {
            log.warn("⚠️  Se solicitó imprimir la factura pero no se proporcionó el nombre de la impresora");
        }

        return com.franco.dev.dto.factura.CrearFacturaLegalResponseDTO.builder()
                .id(facturaLegalGuardada.getId())
                .numeroFactura(facturaLegalGuardada.getNumeroFactura())
                .nombre(facturaLegalGuardada.getNombre())
                .ruc(facturaLegalGuardada.getRuc())
                .direccion(facturaLegalGuardada.getDireccion())
                .fecha(facturaLegalGuardada.getFecha())
                .totalFinal(facturaLegalGuardada.getTotalFinal())
                .esElectronica(esElectronica)
                .cdc(cdc)
                .urlQr(urlQr)
                .estadoDocumentoElectronico(estadoDocumentoElectronico)
                .mensajeRespuestaSifen(mensajeRespuestaSifen)
                .documentoElectronicoGenerado(documentoElectronicoGenerado)
                .build();
    }

    /**
     * Valida y configura la moneda extranjera en la factura.
     * 
     * Validaciones:
     * - Si hay moneda extranjera, debe haber tipo de cambio
     * - Si hay tipo de cambio, debe haber moneda extranjera
     * - El tipo de cambio debe ser positivo
     * - La moneda debe ser un código ISO válido (máximo 3 caracteres)
     * 
     * @param request DTO con los datos de la factura
     * @param facturaLegal Factura legal a configurar
     */
    private void validarYConfigurarMonedaExtranjera(
            CrearFacturaLegalRequestDTO request, 
            FacturaLegal facturaLegal) {
        
        String monedaExtranjera = request.getMonedaExtranjera();
        Double tipoCambio = request.getTipoCambio();
        
        // Si ambos son null, no hay moneda extranjera
        if (monedaExtranjera == null && tipoCambio == null) {
            facturaLegal.setMonedaExtranjera(null);
            facturaLegal.setTipoCambio(null);
            return;
        }
        
        // Validar que ambos estén presentes
        if (monedaExtranjera == null || monedaExtranjera.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Si se proporciona tipo de cambio, debe proporcionarse también la moneda extranjera");
        }
        
        if (tipoCambio == null) {
            throw new IllegalArgumentException(
                "Si se proporciona moneda extranjera, debe proporcionarse también el tipo de cambio");
        }
        
        // Validar que el tipo de cambio sea positivo
        if (tipoCambio <= 0) {
            throw new IllegalArgumentException(
                "El tipo de cambio debe ser un valor positivo. Valor recibido: " + tipoCambio);
        }
        
        // Validar y normalizar código de moneda (máximo 3 caracteres, ISO 4217)
        String monedaNormalizada = monedaExtranjera.trim().toUpperCase();
        if (monedaNormalizada.length() > 3) {
            log.warn("Moneda extranjera '{}' excede 3 caracteres, truncando a los primeros 3 caracteres", 
                    monedaExtranjera);
            monedaNormalizada = monedaNormalizada.substring(0, 3);
        }
        
        // Validar que no sea PYG (guaraníes es la moneda local)
        if ("PYG".equals(monedaNormalizada)) {
            throw new IllegalArgumentException(
                "PYG (Guaraníes) es la moneda local. No se puede usar como moneda extranjera");
        }
        
        facturaLegal.setMonedaExtranjera(monedaNormalizada);
        facturaLegal.setTipoCambio(tipoCambio);
        
        log.debug("Moneda extranjera configurada: {} con tipo de cambio: {}", monedaNormalizada, tipoCambio);
    }

    /**
     * Determina si una factura usa moneda extranjera.
     * 
     * @param facturaLegal La factura a verificar
     * @return true si la factura usa moneda extranjera
     */
    private boolean esMonedaExtranjera(FacturaLegal facturaLegal) {
        return facturaLegal.getMonedaExtranjera() != null 
                && !facturaLegal.getMonedaExtranjera().trim().isEmpty()
                && facturaLegal.getTipoCambio() != null
                && facturaLegal.getTipoCambio() > 0;
    }
}

