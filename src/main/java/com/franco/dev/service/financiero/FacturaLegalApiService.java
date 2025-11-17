package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.dto.factura.CrearFacturaLegalRequestDTO;
import com.franco.dev.dto.factura.DisponibilidadTimbradoDetalleResponseDTO;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.sifen.service.SifenService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        log.info("Creando factura legal para cliente: {} (RUC: {})", request.getNombre(), request.getRuc());

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
        facturaLegal.setFecha(request.getFecha() != null ? request.getFecha() : LocalDateTime.now());
        facturaLegal.setIvaParcial0(request.getIvaParcial0() != null ? request.getIvaParcial0() : 0.0);
        facturaLegal.setIvaParcial5(request.getIvaParcial5() != null ? request.getIvaParcial5() : 0.0);
        facturaLegal.setIvaParcial10(request.getIvaParcial10() != null ? request.getIvaParcial10() : 0.0);
        facturaLegal.setTotalParcial0(request.getTotalParcial0() != null ? request.getTotalParcial0() : 0.0);
        facturaLegal.setTotalParcial5(request.getTotalParcial5() != null ? request.getTotalParcial5() : 0.0);
        facturaLegal.setTotalParcial10(request.getTotalParcial10() != null ? request.getTotalParcial10() : 0.0);
        facturaLegal.setTotalFinal(request.getTotalFinal());
        facturaLegal.setDescuento(request.getDescuento() != null ? request.getDescuento() : 0.0);
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
        // TODO: Implementar asignación de caja si es necesario

        // Incrementar número de factura
        Long numeroFactura = timbradoDetalle.getNumeroActual() != null 
                ? timbradoDetalle.getNumeroActual() + 1 
                : 1L;
        facturaLegal.setNumeroFactura(numeroFactura.intValue());

        // Guardar la factura legal
        FacturaLegal facturaLegalGuardada = facturaLegalService.save(facturaLegal);

        // Guardar los items
        for (com.franco.dev.dto.factura.FacturaLegalItemDTO itemDTO : request.getItems()) {
            FacturaLegalItem item = new FacturaLegalItem();
            item.setFacturaLegal(facturaLegalGuardada);
            item.setDescripcion(itemDTO.getDescripcion());
            item.setCantidad(itemDTO.getCantidad().floatValue());
            item.setPrecioUnitario(itemDTO.getPrecioUnitario());
            item.setTotal(itemDTO.getTotal() != null 
                    ? itemDTO.getTotal() 
                    : itemDTO.getPrecioUnitario() * itemDTO.getCantidad());
            item.setSucursalId(facturaLegalGuardada.getSucursalId());

            // Asignar producto si se proporciona
            if (itemDTO.getProductoId() != null) {
                // TODO: Implementar asignación de producto si es necesario
            }

            facturaLegalItemService.save(item);
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
                log.info("Generando documento electrónico para factura ID: {}", facturaLegalGuardada.getId());
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
            }
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
}

