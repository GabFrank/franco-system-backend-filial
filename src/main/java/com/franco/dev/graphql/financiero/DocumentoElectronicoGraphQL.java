package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.DocumentoElectronicoInput;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class DocumentoElectronicoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private UsuarioService usuarioService;

    public DocumentoElectronico documentoElectronico(Long id, Long sucId) {
        return documentoElectronicoService.findById(id).orElse(null);
    }

    public List<DocumentoElectronico> documentoElectronicos(Integer page, Integer size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return documentoElectronicoService.findBySucursalId(sucId);
    }

    public DocumentoElectronico documentoElectronicoPorFactura(Long facturaLegalId, Long sucId) {
        return documentoElectronicoService.findByFacturaLegalIdAndSucursalId(facturaLegalId, sucId);
    }

    public DocumentoElectronico documentoElectronicoPorCdc(String cdc, Long sucId) {
        return documentoElectronicoService.findByCdc(cdc).orElse(null);
    }

    public List<DocumentoElectronico> documentoElectronicoPorEstado(String estado, Long sucId) {
        return documentoElectronicoService.findByEstado(estado);
    }

    public Integer countDocumentoElectronico() {
        return documentoElectronicoService.findBySucursalId(1L).size(); // TODO: Implementar count real
    }

    public DocumentoElectronico saveDocumentoElectronico(DocumentoElectronicoInput input) {
        try {
            DocumentoElectronico documentoElectronico = new DocumentoElectronico();
            
            if (input.getId() != null) {
                documentoElectronico = documentoElectronicoService.findById(input.getId())
                        .orElse(new DocumentoElectronico());
            }

            // Mapear campos básicos
            documentoElectronico.setSucursalId(input.getSucursalId());
            documentoElectronico.setCdc(input.getCdc());
            documentoElectronico.setUrlQr(input.getUrlQr());
            documentoElectronico.setXmlFirmado(input.getXmlFirmado());
            documentoElectronico.setEstadoDocumentoElectronico(input.getEstadoDocumentoElectronico());
            documentoElectronico.setCodigoRespuestaSifen(input.getCodigoRespuestaSifen());
            documentoElectronico.setMensajeRespuestaSifen(input.getMensajeRespuestaSifen());
            documentoElectronico.setNumeroDocumento(input.getNumeroDocumento());
            documentoElectronico.setTipoDocumento(input.getTipoDocumento());
            documentoElectronico.setActivo(input.getActivo() != null ? input.getActivo() : true);

            // Mapear fechas
            if (input.getFechaEmision() != null) {
                documentoElectronico.setFechaEmision(LocalDateTime.parse(input.getFechaEmision()));
            }
            if (input.getFechaRecepcionSifen() != null) {
                documentoElectronico.setFechaRecepcionSifen(LocalDateTime.parse(input.getFechaRecepcionSifen()));
            }

            // Mapear relaciones
            if (input.getFacturaLegalId() != null) {
                Optional<FacturaLegal> facturaLegal = facturaLegalService.findById(input.getFacturaLegalId());
                facturaLegal.ifPresent(documentoElectronico::setFacturaLegal);
            }

            if (input.getUsuarioId() != null) {
                Optional<Usuario> usuario = usuarioService.findById(input.getUsuarioId());
                usuario.ifPresent(documentoElectronico::setUsuario);
            }

            return documentoElectronicoService.save(documentoElectronico);

        } catch (Exception e) {
            log.error("Error al guardar documento electrónico: {}", e.getMessage(), e);
            throw new RuntimeException("Error al guardar documento electrónico: " + e.getMessage(), e);
        }
    }

    public Boolean deleteDocumentoElectronico(Long id, Long sucId) {
        try {
            Optional<DocumentoElectronico> documentoElectronico = documentoElectronicoService.findById(id);
            if (documentoElectronico.isPresent()) {
                DocumentoElectronico doc = documentoElectronico.get();
                doc.setActivo(false);
                documentoElectronicoService.save(doc);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error al eliminar documento electrónico: {}", e.getMessage(), e);
            throw new RuntimeException("Error al eliminar documento electrónico: " + e.getMessage(), e);
        }
    }
}
