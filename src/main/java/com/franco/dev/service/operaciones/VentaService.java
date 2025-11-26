package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.dto.VentaPorPeriodoV1Dto;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.MovimientoCajaService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
@AllArgsConstructor
public class VentaService extends CrudService<Venta, VentaRepository> {
    private final VentaRepository repository;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private CobroDetalleService cobroDetalleService;
    @Autowired
    private Environment env;

    @Autowired
    private VentaItemService ventaItemService;

    @Override
    public VentaRepository getRepository() {
        return repository;
    }


    public Page<Venta> findByCajaId(EmbebedPrimaryKey id, Integer page, Integer size, Boolean asc, Long formaPago,
            VentaEstado estado, Boolean isDelivery, Long monedaId) {
        Pageable pagina = PageRequest.of(page, size);
        return findWithFiltersCriteria(id.getId(), id.getSucursalId(), formaPago, estado, pagina, isDelivery, monedaId,
                asc);
    }

    public List<Venta> findAllByCajaId(Long id) {
        return repository.findByCajaId(id);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Venta save(Venta entity) {
        if (entity.getId() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getSucursalId() == null)
            entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        if (entity.getTotalGs() == 0 && entity.getEstado() == VentaEstado.CONCLUIDA)
            return null;
        Venta e = super.save(entity);
        return e;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Venta saveAndSend(Venta entity, Boolean recibir) {
        if (entity.getId() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getSucursalId() == null)
            entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
            if(entity.getTotalGs() == 0 && entity.getEstado() == VentaEstado.CONCLUIDA) return null;
        Venta e = super.save(entity);
        return e;
    }

    public List<VentaPorPeriodoV1Dto> ventaPorPeriodo(String inicio, String fin) {
        List<VentaPorPeriodoV1Dto> ventaPorPeriodoList = new ArrayList<>();
        LocalDateTime fechaInicio = LocalDateTime.parse(inicio);
        LocalDateTime fechaFin = LocalDateTime.parse(fin); 
        Long cantDias = DAYS.between(fechaInicio, fechaFin);
        for (int i = 0; i < cantDias; i++) {
            VentaPorPeriodoV1Dto ventaPorPeriodoV1Dto = new VentaPorPeriodoV1Dto();
            ventaPorPeriodoV1Dto.setCreadoEn(fechaInicio.plusDays(i));
            ventaPorPeriodoList.add(ventaPorPeriodoV1Dto);
        }
        for (VentaPorPeriodoV1Dto ventaPorPeriodo : ventaPorPeriodoList) {
            List<Venta> ventaList = repository.findByCreadoEnBetween(ventaPorPeriodo.getCreadoEn(),
                    ventaPorPeriodo.getCreadoEn().plusDays(1));
            ventaPorPeriodo.setCantVenta(ventaList.size());
            for (Venta venta : ventaList) {
                if (venta.getEstado() != VentaEstado.CANCELADA || venta.getEstado() != VentaEstado.ABIERTA) {
                    List<CobroDetalle> cobroDetalleList = cobroDetalleService.findByCobroId(venta.getCobro().getId());
                    for (CobroDetalle cobroDetalle : cobroDetalleList) {
                        if (cobroDetalle.getMoneda().getDenominacion().contains("GUARANI")) {
                            if (cobroDetalle.getPago()) {
                                ventaPorPeriodo.addGs(cobroDetalle.getValor());
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor());
                            } else if (cobroDetalle.getDescuento()) {
                                ventaPorPeriodo.addGs(cobroDetalle.getValor() * -1);
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * -1);
                            }
                        }
                        if (cobroDetalle.getMoneda().getDenominacion().contains("REAL")) {
                            if (cobroDetalle.getPago()) {
                                ventaPorPeriodo.addRs(cobroDetalle.getValor());
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * cobroDetalle.getCambio());
                            } else if (cobroDetalle.getDescuento()) {
                                ventaPorPeriodo.addRs(cobroDetalle.getValor() * -1);
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * -1 * cobroDetalle.getCambio());
                            }
                        }
                        if (cobroDetalle.getMoneda().getDenominacion().contains("DOLAR")) {
                            if (cobroDetalle.getPago()) {
                                ventaPorPeriodo.addDs(cobroDetalle.getValor());
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * cobroDetalle.getCambio());
                            } else if (cobroDetalle.getDescuento()) {
                                ventaPorPeriodo.addDs(cobroDetalle.getValor() * -1);
                                ventaPorPeriodo.addTotalGs(cobroDetalle.getValor() * -1 * cobroDetalle.getCambio());
                            }
                        }
                    }
                }
            }
        }
        return ventaPorPeriodoList;
    }

    @Transactional
    public Boolean cancelarVenta(Venta venta) {
        // venta.setEstado(VentaEstado.CANCELADA);
        // saveAndSend(venta, false);
        // List<MovimientoCaja> movimientoCajaList =
        // movimientoCajaService.findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento.VENTA,
        // venta.getCobro().getId());
        // for (MovimientoCaja mov : movimientoCajaList) {
        // mov.setActivo(false);
        // movimientoCajaService.saveAndSend(mov, false);
        // }
        return true;
    }

    public Page<Venta> findWithFiltersCriteria(Long id, Long sucId, Long formaPagoId, VentaEstado estado,
            Pageable pageable, Boolean isDelivery, Long monedaId, Boolean isAsc) {
        Sort sort = isAsc == false ? Sort.by("id").descending() : Sort.by("id").ascending();
        Pageable newPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        return this.repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Venta, PdvCaja> cajaJoin = root.join("caja", JoinType.INNER);

            // Add the predicates
            if (id != null) {
                predicates.add(cb.equal(cajaJoin.get("id"), id));
            }

            if (sucId != null) {
                predicates.add(cb.equal(root.get("sucursalId"), sucId));
            }

            // Join with CobroDetalle manually based on cobro_id and sucursal_id
            if (formaPagoId != null) {
                Subquery<Long> cobroDetalleSubquery = query.subquery(Long.class);
                Root<CobroDetalle> cobroDetalleRoot = cobroDetalleSubquery.from(CobroDetalle.class);

                // Reference cobro_id and sucursal_id directly, instead of cobro.id
                cobroDetalleSubquery.select(cobroDetalleRoot.get("id"))
                        .where(
                                cb.equal(cobroDetalleRoot.get("cobro"), root.get("cobro")), // Literal cobro_id
                                cb.equal(cobroDetalleRoot.get("sucursalId"), root.get("sucursalId")),
                                cb.equal(cobroDetalleRoot.get("formaPago").get("id"), formaPagoId) // formaPagoId
                                                                                                   // condition
                );

                predicates.add(cb.exists(cobroDetalleSubquery));
            }

            if (estado != null) {
                predicates.add(cb.equal(root.get("estado"), estado));
            }

            if (isDelivery != null && isDelivery == true) {
                Join<Venta, Delivery> deliveryJoin = root.join("delivery", JoinType.INNER);
                predicates.add(cb.isNotNull(deliveryJoin.get("delivery")));
            }

            // Combine predicates with AND
            return cb.and(predicates.toArray(new Predicate[0]));
        }, newPageable);
    }
}