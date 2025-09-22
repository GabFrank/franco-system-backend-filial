package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.financiero.DocumentoElectronicoRepository;
import graphql.GraphQLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DocumentoElectronicoService {

    @Autowired
    private DocumentoElectronicoRepository repository;

    public DocumentoElectronico save(DocumentoElectronico documentoElectronico) {
        if (documentoElectronico.getFacturaLegal() != null && documentoElectronico.getFacturaLegal().getId() != null) {
            DocumentoElectronico existingDoc = repository.findByFacturaLegalIdAndSucursalId(documentoElectronico.getFacturaLegal().getId(), documentoElectronico.getSucursalId());
            if (existingDoc != null && (documentoElectronico.getId() == null || !existingDoc.getId().equals(documentoElectronico.getId()))) {
                throw new GraphQLException("Ya existe un documento electrónico para la factura legal ID: " + documentoElectronico.getFacturaLegal().getId() + " en la sucursal ID: " + documentoElectronico.getSucursalId());
            }
        }
        return repository.save(documentoElectronico);
    }

    public Optional<DocumentoElectronico> findById(Long id) {
        return repository.findById(id);
    }

    public DocumentoElectronico findByFacturaLegalIdAndSucursalId(Long facturaLegalId, Long sucursalId) {
        return repository.findByFacturaLegalIdAndSucursalId(facturaLegalId, sucursalId);
    }

    public Optional<DocumentoElectronico> findByCdc(String cdc) {
        return repository.findByCdc(cdc);
    }

    public List<DocumentoElectronico> findByEstado(String estado) {
        return repository.findByEstadoDocumentoElectronico(estado);
    }

    public List<DocumentoElectronico> findBySucursalId(Long sucursalId) {
        return repository.findBySucursalIdAndActivoTrue(sucursalId);
    }

    public DocumentoElectronico createFromFacturaLegal(FacturaLegal facturaLegal) {
        DocumentoElectronico documentoElectronico = new DocumentoElectronico();
        documentoElectronico.setFacturaLegal(facturaLegal);
        documentoElectronico.setSucursalId(facturaLegal.getSucursalId());
        documentoElectronico.setNumeroDocumento(facturaLegal.getNumeroFactura().toString());
        documentoElectronico.setTipoDocumento("FACTURA");
        documentoElectronico.setFechaEmision(facturaLegal.getFecha());
        documentoElectronico.setActivo(true);
        documentoElectronico.setUsuario(facturaLegal.getUsuario());
        
        return documentoElectronico;
    }
}
