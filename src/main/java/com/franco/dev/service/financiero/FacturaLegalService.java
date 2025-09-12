package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.sifen.service.SifenService;

import lombok.AllArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class FacturaLegalService extends CrudService<FacturaLegal, FacturaLegalRepository> {

    private final FacturaLegalRepository repository;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private SifenService sifenService;

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

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public FacturaLegal save(FacturaLegal entity) {
        entity.setSucursalId(Long.valueOf(super.env.getProperty("sucursalId")));
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            if (entity.getCdc() == null) {
                Sucursal sucursal = sucursalService.findById(entity.getSucursalId()).orElse(null);
                // Use emitter RUC from Timbrado instead of client RUC
                String rucEmisor = entity.getTimbradoDetalle().getTimbrado().getRuc();
                entity.setCdc(sifenService.generarCdc(rucEmisor, sucursal.getCodigoEstablecimientoFactura(), entity.getTimbradoDetalle().getPuntoExpedicion(), entity.getNumeroFactura().toString(), entity.getFecha()));
                System.out.println("CDC: " + entity.getCdc());
            }
        }
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        FacturaLegal e = super.save(entity);
        return e;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public FacturaLegal saveAndSend(FacturaLegal entity, Boolean recibir) {
        entity.setSucursalId(Long.valueOf(super.env.getProperty("sucursalId")));
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setActivo(true);
            entity.setViaTributaria(false);
            if (entity.getCdc() == null) {
                Sucursal sucursal = sucursalService.findById(entity.getSucursalId()).orElse(null);
                // Use emitter RUC from Timbrado instead of client RUC
                String rucEmisor = entity.getTimbradoDetalle().getTimbrado().getRuc();
                entity.setCdc(sifenService.generarCdc(rucEmisor, sucursal.getCodigoEstablecimientoFactura(), entity.getTimbradoDetalle().getPuntoExpedicion(), entity.getNumeroFactura().toString(), entity.getFecha()));
                // print the cdc
                System.out.println("CDC: " + entity.getCdc());
            }
        }
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        
        FacturaLegal e = super.save(entity);
//        super.propagacionService.propagarEntidad(e, TipoEntidad.FACTURA, false);
        return e;
    }

}