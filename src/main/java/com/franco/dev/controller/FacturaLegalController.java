package com.franco.dev.controller;

import com.franco.dev.dto.factura.CrearFacturaLegalRequestDTO;
import com.franco.dev.dto.factura.CrearFacturaLegalResponseDTO;
import com.franco.dev.dto.factura.DisponibilidadTimbradoDetalleResponseDTO;
import com.franco.dev.dto.factura.ErrorResponseDTO;
import com.franco.dev.service.financiero.FacturaLegalApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

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
     * @return DTO con la información de la factura creada o mensaje de error
     */
    @PostMapping
    public ResponseEntity<?> crearFacturaLegal(
            @Valid @RequestBody CrearFacturaLegalRequestDTO request) {
        
        // Log detallado de todos los datos recibidos del servidor
        try {
            String requestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
            log.info("═══════════════════════════════════════════════════════════════");
            log.info("📥 DATOS RECIBIDOS DEL SERVIDOR - CREAR FACTURA LEGAL");
            log.info("═══════════════════════════════════════════════════════════════");
            log.info("Request completo:\n{}", requestJson);
            log.info("═══════════════════════════════════════════════════════════════");
        } catch (Exception e) {
            log.warn("⚠️ No se pudo serializar el request a JSON para logging: {}", e.getMessage());
            log.info("📥 DATOS RECIBIDOS - Cliente: {} (RUC: {}), TimbradoDetalleId: {}, Items: {}", 
                    request.getNombre(), 
                    request.getRuc(),
                    request.getTimbradoDetalleId(),
                    request.getItems() != null ? request.getItems().size() : 0);
        }
        
        log.info("Solicitud de creación de factura legal para cliente: {} (RUC: {})", 
                request.getNombre(), request.getRuc());
        
        try {
            CrearFacturaLegalResponseDTO respuesta = facturaLegalApiService.crearFacturaLegal(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (IllegalStateException e) {
            log.error("Error al crear factura legal: {}", e.getMessage());
            ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
                    .mensaje(e.getMessage())
                    .error("BAD_REQUEST")
                    .codigo(HttpStatus.BAD_REQUEST.value())
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            log.error("Error inesperado al crear factura legal", e);
            ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
                    .mensaje(e.getMessage() != null ? e.getMessage() : "Error interno del servidor")
                    .error("INTERNAL_SERVER_ERROR")
                    .codigo(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

