package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.dto.FacturaSimilarDto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import com.franco.dev.utilitarios.FacturaNumeroUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Detecta si el cajero está por emitir una factura prácticamente idéntica a otra que
 * ya emitió hoy, para poder avisarle ANTES de emitirla (la emisión imprime y consume
 * un número de timbrado, así que no hay vuelta atrás).
 * <p>
 * Criterio de coincidencia: mismo cliente + mismo total + mismas líneas
 * (descripción, cantidad y precio unitario), del mismo usuario y la misma sucursal,
 * dentro del día en curso.
 * <p>
 * Es solo un aviso: no bloquea nada. La decisión final la toma el cajero desde el
 * diálogo de confirmación del desktop.
 */
@Service
public class FacturaSimilarService {

    private static final Logger log = LoggerFactory.getLogger(FacturaSimilarService.class);

    /**
     * Tolerancia al comparar montos: los totales viajan como Double desde el frontend,
     * así que no se pueden comparar con equals.
     */
    private static final double TOLERANCIA_MONTO = 0.5;

    /**
     * Tolerancia al comparar cantidades. En la entidad son Float y en el input Double,
     * y hay presentaciones que se venden fraccionadas.
     */
    private static final double TOLERANCIA_CANTIDAD = 0.0001;

    /**
     * Cliente 0 = consumidor final. Dos consumidores finales que compran lo mismo no son
     * un duplicado, así que para ellos no se verifica nada.
     */
    private static final long CLIENTE_CONSUMIDOR_FINAL = 0L;

    @Autowired
    private FacturaLegalRepository facturaLegalRepository;

    @Autowired
    private FacturaLegalItemService facturaLegalItemService;

    @Autowired
    private Environment env;

    /**
     * @return la factura similar más reciente del turno, o null si no hay ninguna
     */
    @Transactional(readOnly = true)
    public FacturaSimilarDto buscarFacturaSimilarReciente(Long usuarioId, Long clienteId,
                                                          Double totalFinal, List<FacturaLegalItemInput> items,
                                                          Long sucursalId) {
        if (usuarioId == null || totalFinal == null || items == null || items.isEmpty()) {
            return null;
        }

        // Solo aplica a facturas con cliente identificado
        if (clienteId == null || clienteId <= CLIENTE_CONSUMIDOR_FINAL) {
            return null;
        }

        // El desktop manda la sucursal cuando factura contra una sucursal elegida a mano;
        // en el POS no la manda y vale la del propio servidor filial.
        if (sucursalId == null) {
            sucursalId = Long.valueOf(env.getProperty("sucursalId"));
        }
        LocalDateTime desde = LocalDate.now().atStartOfDay();

        List<FacturaLegal> candidatas = facturaLegalRepository
                .findByUsuarioIdAndClienteIdAndSucursalIdAndCreadoEnGreaterThanEqualOrderByIdDesc(
                        usuarioId, clienteId, sucursalId, desde);

        if (candidatas == null || candidatas.isEmpty()) {
            return null;
        }

        List<ItemInfo> itemsNuevaFactura = fromInputs(items);

        for (FacturaLegal candidata : candidatas) {
            if (Boolean.FALSE.equals(candidata.getActivo())) {
                continue;
            }
            // Filtro barato primero: el monto. Solo si coincide vale la pena cargar los items.
            if (candidata.getTotalFinal() == null
                    || Math.abs(candidata.getTotalFinal() - totalFinal) > TOLERANCIA_MONTO) {
                continue;
            }

            List<FacturaLegalItem> itemsCandidata =
                    facturaLegalItemService.findByFacturaLegalId(candidata.getId());
            if (!sonIguales(fromEntities(itemsCandidata), itemsNuevaFactura)) {
                continue;
            }

            log.info("Posible factura duplicada: usuario {} está por repetir la factura {} (cliente {})",
                    usuarioId, candidata.getId(), clienteId);
            return toDto(candidata);
        }

        return null;
    }

    private FacturaSimilarDto toDto(FacturaLegal facturaLegal) {
        FacturaSimilarDto dto = new FacturaSimilarDto();
        dto.setFacturaLegalId(facturaLegal.getId());
        dto.setFecha(facturaLegal.getFecha() != null ? facturaLegal.getFecha() : facturaLegal.getCreadoEn());
        dto.setTotalFinal(facturaLegal.getTotalFinal());
        dto.setClienteNombre(nombreCliente(facturaLegal));
        dto.setNumeroFactura(numeroFactura(facturaLegal));
        return dto;
    }

    /**
     * El nombre impreso en la factura es la fuente más fiel de a quién se le facturó;
     * si no está, se cae al nombre de la persona del cliente.
     */
    private String nombreCliente(FacturaLegal facturaLegal) {
        if (facturaLegal.getNombre() != null && !facturaLegal.getNombre().trim().isEmpty()) {
            return facturaLegal.getNombre();
        }
        Cliente cliente = facturaLegal.getCliente();
        if (cliente == null || cliente.getPersona() == null) return null;
        return cliente.getPersona().getNombre();
    }

    private String numeroFactura(FacturaLegal facturaLegal) {
        TimbradoDetalle timbradoDetalle = facturaLegal.getTimbradoDetalle();
        if (timbradoDetalle == null) return null;

        Sucursal sucursal = timbradoDetalle.getSucursal();
        return FacturaNumeroUtils.format(
                sucursal != null ? sucursal.getCodigoEstablecimientoFactura() : null,
                timbradoDetalle.getPuntoExpedicion(),
                facturaLegal.getNumeroFactura());
    }

    // --- Comparación de items -------------------------------------------------

    /**
     * Representación mínima de una línea de factura a los efectos de la comparación.
     * <p>
     * Se compara por descripción, cantidad y precio unitario, y NO por producto o
     * presentación: en {@code factura_legal_item} esas dos columnas están casi siempre
     * vacías (el item de factura se arma con la descripción impresa), así que usarlas
     * daría falsos negativos en la mayoría de las facturas.
     */
    private static class ItemInfo {
        private final String descripcion;
        private final Double cantidad;
        private final Double precioUnitario;

        ItemInfo(String descripcion, Double cantidad, Double precioUnitario) {
            this.descripcion = normalizar(descripcion);
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
        }

        private static String normalizar(String texto) {
            return texto == null ? "" : texto.trim().toUpperCase();
        }
    }

    private static final Comparator<ItemInfo> ORDEN = Comparator
            .comparing((ItemInfo i) -> i.descripcion, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(i -> i.cantidad, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(i -> i.precioUnitario, Comparator.nullsFirst(Comparator.naturalOrder()));

    private List<ItemInfo> fromInputs(List<FacturaLegalItemInput> items) {
        return items.stream()
                .map(i -> new ItemInfo(i.getDescripcion(), i.getCantidad(), i.getPrecioUnitario()))
                .collect(Collectors.toList());
    }

    private List<ItemInfo> fromEntities(List<FacturaLegalItem> items) {
        if (items == null) return new ArrayList<>();
        return items.stream()
                .map(i -> new ItemInfo(
                        i.getDescripcion(),
                        i.getCantidad() != null ? i.getCantidad().doubleValue() : null,
                        i.getPrecioUnitario()))
                .collect(Collectors.toList());
    }

    /**
     * @return true si ambas listas contienen exactamente las mismas líneas, ignorando el orden
     */
    private boolean sonIguales(List<ItemInfo> items1, List<ItemInfo> items2) {
        if (items1.size() != items2.size()) return false;

        List<ItemInfo> ordenados1 = new ArrayList<>(items1);
        List<ItemInfo> ordenados2 = new ArrayList<>(items2);
        ordenados1.sort(ORDEN);
        ordenados2.sort(ORDEN);

        for (int i = 0; i < ordenados1.size(); i++) {
            if (!esMismoItem(ordenados1.get(i), ordenados2.get(i))) return false;
        }
        return true;
    }

    private boolean esMismoItem(ItemInfo a, ItemInfo b) {
        if (!Objects.equals(a.descripcion, b.descripcion)) return false;
        if (!sonCasiIguales(a.cantidad, b.cantidad, TOLERANCIA_CANTIDAD)) return false;
        return sonCasiIguales(a.precioUnitario, b.precioUnitario, TOLERANCIA_MONTO);
    }

    private boolean sonCasiIguales(Double a, Double b, double tolerancia) {
        if (a == null || b == null) return Objects.equals(a, b);
        return Math.abs(a - b) <= tolerancia;
    }
}
