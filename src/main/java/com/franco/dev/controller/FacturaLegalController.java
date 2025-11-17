package com.franco.dev.controller;

import com.franco.dev.dto.factura.CrearFacturaLegalRequestDTO;
import com.franco.dev.dto.factura.CrearFacturaLegalResponseDTO;
import com.franco.dev.dto.factura.DisponibilidadTimbradoDetalleResponseDTO;
import com.franco.dev.service.financiero.FacturaLegalApiService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/facturas-legales")
@AllArgsConstructor
@Validated
public class FacturaLegalController {

    private final FacturaLegalApiService facturaLegalApiService;

    /**
     * Verifica la disponibilidad de un timbrado detalle para emitir facturas.
     * 
     * @param timbradoDetalleId ID del timbrado detalle a verificar
     * @return Información sobre la disponibilidad del timbrado detalle
     */
    @GetMapping("/timbrado-detalle/{timbradoDetalleId}/disponibilidad")
    public ResponseEntity<DisponibilidadTimbradoDetalleResponseDTO> verificarDisponibilidad(
            @PathVariable Long timbradoDetalleId) {
        log.info("Solicitud de verificación de disponibilidad para timbrado detalle ID: {}", timbradoDetalleId);
        
        DisponibilidadTimbradoDetalleResponseDTO respuesta = 
                facturaLegalApiService.verificarDisponibilidadTimbradoDetalle(timbradoDetalleId);
        
        HttpStatus status = respuesta.getDisponible() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(respuesta);
    }

    /**
     * Crea una nueva factura legal sin estar vinculada a una venta.
     * La factura puede ser electrónica o normal dependiendo del timbrado utilizado.
     * 
     * @param request DTO con los datos de la factura
     * @return DTO con la información de la factura creada
     */
    @PostMapping
    public ResponseEntity<CrearFacturaLegalResponseDTO> crearFacturaLegal(
            @Valid @RequestBody CrearFacturaLegalRequestDTO request) {
        log.info("Solicitud de creación de factura legal para cliente: {} (RUC: {})", 
                request.getNombre(), request.getRuc());
        
        try {
            CrearFacturaLegalResponseDTO respuesta = facturaLegalApiService.crearFacturaLegal(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (IllegalStateException e) {
            log.error("Error al crear factura legal: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error inesperado al crear factura legal", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

