package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.sifen.service.SifenService;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import graphql.GraphQLException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class FacturaLegalService extends CrudService<FacturaLegal, FacturaLegalRepository> {

    private final FacturaLegalRepository repository;
    private final FacturaLegalItemService facturaLegalItemService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private SifenService sifenService;

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    @Override
    public FacturaLegalRepository getRepository() {
        return repository;
    }

    public List<FacturaLegal> findByCajaId(Long id) {
        return repository.findByCajaId(id);
    }

    public FacturaLegal findByVentaId(Long id) {
        return repository.findByVentaId(id);
    }

    /** La factura vigente de una venta: la activa mas nueva. No falla si la venta quedo con mas de una (#207). */
    public FacturaLegal findVigenteDeVenta(Long ventaId, Long sucursalId) {
        return repository.findByVentaIdAndSucursalIdOrderByIdDesc(ventaId, sucursalId).stream()
                .filter(f -> !Boolean.FALSE.equals(f.getActivo()))
                .findFirst().orElse(null);
    }

    /** Clave del lock que serializa la factura de una venta (#210, #221). */
    public static String claveFacturaVenta(Long sucursalId, Long ventaId) {
        return "factura-venta:" + sucursalId + ":" + ventaId;
    }

    /**
     * Una venta tiene una sola factura vigente: FINALIZAR con factura emitia otra si PARA ENTREGA ya habia facturado
     * (#221). Sin @Transactional a proposito: una excepcion de un metodo transaccional dejaria rollback-only la
     * transaccion del builder, que cuenta con que una GraphQLException no revierte nada.
     */
    public void exigirSinFacturaVigente(Long ventaId, Long sucursalId) {
        FacturaLegal vigente = findVigenteDeVenta(ventaId, sucursalId);
        if (vigente != null) {
            throw new GraphQLException("La venta ya tiene la factura N° " + vigente.getNumeroFactura()
                    + ": no se emite otra. Si el cliente la necesita a su nombre, primero cancelá la N° "
                    + vigente.getNumeroFactura() + " en Facturas legales.");
        }
    }

    // /**
    //  * Genera un documento electrónico completo para una factura legal.
    //  * Este método utiliza la librería SIFEN para generar el CDC, URL QR y XML firmado.
    //  *
    //  * @param facturaLegal La factura legal para la cual generar el documento electrónico
    //  * @return La factura legal actualizada con la información del documento electrónico
    //  */
    // @Transactional(isolation = Isolation.SERIALIZABLE)
    // public FacturaLegal generarDocumentoElectronico(FacturaLegal facturaLegal) {
    //     try {
    //         log.info("Iniciando generación de DE para factura legal ID: {}", facturaLegal.getId());

    //         List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(facturaLegal.getId());
    //         if (items.isEmpty()) {
    //             throw new IllegalStateException("La factura legal no tiene ítems asociados.");
    //         }

    //         SifenService.DocumentoElectronicoInfo infoDocumento = sifenService.generarDocumentoElectronico(facturaLegal, items);

    //         facturaLegal.setCdc(infoDocumento.getCdc());
    //         FacturaLegal facturaActualizada = super.save(facturaLegal);

    //         com.franco.dev.domain.financiero.DocumentoElectronico docElectronico = documentoElectronicoService.createFromFacturaLegal(facturaActualizada);
    //         docElectronico.setCdc(infoDocumento.getCdc());
    //         docElectronico.setUrlQr(infoDocumento.getUrlQr());
    //         docElectronico.setXmlFirmado(infoDocumento.getXmlFirmado());
    //         docElectronico.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.valueOf(infoDocumento.getEstadoDocumento()));
    //         docElectronico.setCodigoRespuestaSifen(infoDocumento.getCodigoRespuesta());
    //         docElectronico.setMensajeRespuestaSifen(infoDocumento.getMensajeRespuesta());
    //         docElectronico.setFechaRecepcionSifen(LocalDateTime.now());
    //         documentoElectronicoService.save(docElectronico);

    //         log.info("Documento electrónico generado y guardado para factura ID: {} con CDC: {}", facturaActualizada.getId(), infoDocumento.getCdc());

    //         return facturaActualizada;

    //     } catch (Exception e) {
    //         log.error("Fallo total al generar documento electrónico para factura ID: {}", facturaLegal.getId(), e);
    //         throw new RuntimeException("Fallo al generar el documento electrónico: " + e.getMessage(), e);
    //     }
    // }

    // /**
    //  * Genera un documento electrónico completo para una factura legal existente.
    //  * Este método es útil para regenerar documentos electrónicos cuando sea necesario.
    //  *
    //  * @param facturaLegalId El ID de la factura legal
    //  * @return La factura legal actualizada con la información del documento electrónico
    //  */
    // @Transactional(isolation = Isolation.SERIALIZABLE)
    // public FacturaLegal generarDocumentoElectronico(Long facturaLegalId) {
    //     FacturaLegal facturaLegal = findById(facturaLegalId)
    //             .orElseThrow(() -> new RuntimeException("Factura legal no encontrada con ID: " + facturaLegalId));
        
    //     return generarDocumentoElectronico(facturaLegal);
    // }

}