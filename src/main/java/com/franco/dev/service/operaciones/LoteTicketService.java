package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.dto.LoteVendidoDto;
import com.franco.dev.repository.operaciones.MovimientoStockLoteRepository;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.Style;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Arma las líneas de lote que se imprimen debajo de cada ítem del ticket.
 *
 * El desglose por lote de la venta ya lo escribe {@link VentaLoteService} al cobrar; acá solo se
 * lee y se le da forma de 32 columnas. Devuelve texto ya formateado y no DTOs a propósito: los
 * cinco tickets que existen comparten exactamente el mismo formato, y tenerlo en un solo lugar es
 * lo que evita que se vayan separando con el tiempo.
 *
 * También imprime: ver {@link #escribir}. El estilo vive acá y no en cada ticket para que los
 * cinco salgan iguales.
 *
 * Nunca lanza. Un ticket que no se imprime es una venta que el cajero no puede cerrar, y el lote
 * es información secundaria: si algo falla al resolverlo, el ticket sale sin esas líneas.
 */
@Slf4j
@Service
@AllArgsConstructor
public class LoteTicketService {

    /** Ancho útil de la impresora de 58mm en fuente A, el mismo que usan los separadores. */
    private static final int ANCHO_TICKET = 32;

    private static final String PREFIJO = " Lote: ";
    private static final String PREFIJO_VTO = "Vto ";

    /**
     * Con el día completo y no solo mes/año: el vencimiento del ticket es el que se mira en un
     * recall y en un reclamo de mostrador, y ahí "09/2026" no alcanza para saber si el producto
     * estaba vencido el día que se vendió.
     */
    private static final DateTimeFormatter FECHA_VTO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** ESC G n: doble golpe. Ver {@link #escribir}. */
    private static final byte[] DOBLE_GOLPE_ON = {0x1B, 'G', 1};
    private static final byte[] DOBLE_GOLPE_OFF = {0x1B, 'G', 0};

    private final MovimientoStockLoteRepository repository;

    /**
     * Las líneas de lote de cada ítem, listas para {@code escpos.writeLF}.
     *
     * @param ventaItemIds ids de venta_item de los ítems que se están imprimiendo.
     * @param sucursalId   sucursal del ledger. Sin ella no se consulta: el id de venta_item se
     *                     repite entre sucursales.
     * @return mapa ítem -> líneas. Un ítem sin lote trazado no aparece en el mapa.
     */
    public Map<Long, List<String>> lineasPorItem(List<Long> ventaItemIds, Long sucursalId) {
        if (ventaItemIds == null || ventaItemIds.isEmpty() || sucursalId == null) {
            return Collections.emptyMap();
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : ventaItemIds) {
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return agrupar(repository.lotesVendidosPorItem(ids, sucursalId));
        } catch (Exception e) {
            // Ver el javadoc de la clase: el lote no vale una venta sin ticket.
            log.warn("No se pudieron resolver los lotes para el ticket (sucursal {}): {}",
                    sucursalId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Igual que {@link #lineasPorItem}, resolviendo los ids desde los ítems de venta. La sucursal
     * sale de los propios ítems: es la misma que la del ledger, y así el llamador no tiene que
     * conocer de dónde sacarla.
     */
    public Map<Long, List<String>> lineasDeVentaItems(List<VentaItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = new ArrayList<>();
        Long sucursalId = null;
        for (VentaItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            ids.add(item.getId());
            if (sucursalId == null) {
                sucursalId = item.getSucursalId();
            }
        }
        return lineasPorItem(ids, sucursalId);
    }

    /**
     * Igual que {@link #lineasDeVentaItems}, para los tickets que iteran los ítems de la factura.
     *
     * El desglose por lote cuelga del venta_item y no del ítem de factura, así que las claves del
     * mapa son ids de venta_item: el llamador tiene que buscar por {@code vi.getVentaItem()}. Un
     * ítem de factura sin venta_item detrás (los que se cargan a mano) simplemente no tiene lote.
     */
    public Map<Long, List<String>> lineasDeFacturaItems(List<FacturaLegalItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = new ArrayList<>();
        Long sucursalId = null;
        for (FacturaLegalItem item : items) {
            if (item == null || item.getVentaItem() == null || item.getVentaItem().getId() == null) {
                continue;
            }
            ids.add(item.getVentaItem().getId());
            if (sucursalId == null) {
                sucursalId = item.getSucursalId() != null
                        ? item.getSucursalId()
                        : item.getVentaItem().getSucursalId();
            }
        }
        return lineasPorItem(ids, sucursalId);
    }

    /**
     * Escribe las líneas de un ítem en negrita.
     *
     * Usa las DOS formas de resaltado que define ESC/POS, porque no todas las térmicas rinden las
     * dos: {@code ESC E} (enfatizado) y {@code ESC G} (doble golpe). Con enfatizado solo, en la
     * impresora de las sucursales la línea salía igual que el texto normal.
     *
     * El doble golpe se apaga explícitamente al terminar. El enfatizado no hace falta apagarlo: el
     * siguiente write sin estilo reemite el default de EscPos, que ya lo trae en cero.
     */
    public void escribir(EscPos escpos, List<String> lineas) throws IOException {
        if (escpos == null || lineas == null) {
            return;
        }
        for (String linea : lineas) {
            escpos.write(DOBLE_GOLPE_ON, 0, DOBLE_GOLPE_ON.length);
            escpos.writeLF(new Style().setBold(true), linea);
            escpos.write(DOBLE_GOLPE_OFF, 0, DOBLE_GOLPE_OFF.length);
        }
    }

    /**
     * La cantidad solo se imprime cuando el ítem salió de más de un lote.
     *
     * Con un solo lote la cantidad ya está en la línea de arriba y repetirla es ruido; con dos o
     * más, sin la cantidad no se sabe cuánto salió de cada uno, que es justo el dato que hace
     * falta en un recall.
     */
    private Map<Long, List<String>> agrupar(List<LoteVendidoDto> filas) {
        Map<Long, List<LoteVendidoDto>> porItem = new LinkedHashMap<>();
        for (LoteVendidoDto fila : filas) {
            if (fila == null || fila.getVentaItemId() == null || esSinTrazar(fila.getNumeroLote())) {
                continue;
            }
            List<LoteVendidoDto> delItem = porItem.get(fila.getVentaItemId());
            if (delItem == null) {
                delItem = new ArrayList<>();
                porItem.put(fila.getVentaItemId(), delItem);
            }
            delItem.add(fila);
        }

        Map<Long, List<String>> lineas = new HashMap<>();
        for (Map.Entry<Long, List<LoteVendidoDto>> entrada : porItem.entrySet()) {
            List<LoteVendidoDto> delItem = entrada.getValue();
            boolean conCantidad = delItem.size() > 1;
            List<String> delItemFormateadas = new ArrayList<>();
            for (LoteVendidoDto fila : delItem) {
                delItemFormateadas.add(formatear(fila, conCantidad));
            }
            lineas.put(entrada.getKey(), delItemFormateadas);
        }
        return lineas;
    }

    /**
     * El bucket sin trazar no es un lote real, es el stock que no está atribuido a ninguno. Sale
     * del ticket: al cliente no le dice nada y en la mayoría del catálogo sería la única línea.
     */
    private boolean esSinTrazar(String numeroLote) {
        return numeroLote == null
                || numeroLote.trim().isEmpty()
                || LoteFefoService.NUMERO_LOTE_SIN_TRAZAR.equalsIgnoreCase(numeroLote.trim());
    }

    /**
     * Una línea de 32 columnas: el lote pegado a la izquierda y el vencimiento al borde derecho.
     *
     * Si no entra todo, lo que se recorta es el número de lote y nunca el vencimiento: el número
     * recortado sigue sirviendo para identificar, una fecha a medias no sirve para nada.
     */
    private String formatear(LoteVendidoDto fila, boolean conCantidad) {
        String derecha = fila.getFechaVencimiento() != null
                ? PREFIJO_VTO + fila.getFechaVencimiento().format(FECHA_VTO)
                : "";
        String cantidad = conCantidad ? " x" + formatearCantidad(fila.getCantidad()) : "";

        int separacion = derecha.isEmpty() ? 0 : 1;
        int disponible = ANCHO_TICKET - PREFIJO.length() - cantidad.length()
                - derecha.length() - separacion;
        String numero = fila.getNumeroLote().trim().toUpperCase();
        if (disponible < 1) {
            // No entra ni un caracter de lote: se cae a la línea sin vencimiento antes que
            // devolver algo cortado a la mitad.
            return recortar(PREFIJO + numero + cantidad, ANCHO_TICKET);
        }
        if (numero.length() > disponible) {
            numero = numero.substring(0, disponible);
        }

        String izquierda = PREFIJO + numero + cantidad;
        if (derecha.isEmpty()) {
            return izquierda;
        }
        StringBuilder linea = new StringBuilder(izquierda);
        while (linea.length() < ANCHO_TICKET - derecha.length()) {
            linea.append(' ');
        }
        return linea.append(derecha).toString();
    }

    /**
     * Sin decimales cuando la cantidad es entera, que es el caso de casi todo el catálogo. La coma
     * decimal es la misma que usa el resto del ticket.
     *
     * El valor absoluto es lo que da vuelta el signo del ledger: ahí una venta vive en negativo, y
     * el ticket muestra cuánto se llevó el cliente.
     */
    private String formatearCantidad(Double cantidad) {
        if (cantidad == null) {
            return "0";
        }
        DecimalFormat formato = new DecimalFormat("0.###",
                DecimalFormatSymbols.getInstance(Locale.GERMAN));
        return formato.format(Math.abs(cantidad));
    }

    private String recortar(String texto, int largo) {
        return texto.length() > largo ? texto.substring(0, largo) : texto;
    }
}
