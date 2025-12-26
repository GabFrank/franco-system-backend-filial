package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Servicio de cache en memoria para prevenir ventas duplicadas.
 * Almacena las últimas ventas recientes por usuario para validación rápida
 * sin necesidad de consultar la base de datos.
 */
@Service
public class VentaDuplicadaCacheService {

    private static final Logger log = LoggerFactory.getLogger(VentaDuplicadaCacheService.class);
    
    // Cache: Map<usuarioId, List<VentaCacheEntry>>
    // Cada entrada contiene la venta y su timestamp
    private final Map<Long, List<VentaCacheEntry>> ventasCache = new ConcurrentHashMap<>();
    
    // Tiempo mínimo entre ventas (5 segundos)
    private static final int TIEMPO_MINIMO_SEGUNDOS = 5;
    
    // Tamaño máximo de cache por usuario (últimas 10 ventas)
    private static final int MAX_CACHE_SIZE = 10;
    
    /**
     * Clase interna para almacenar información de venta en cache
     */
    private static class VentaCacheEntry {
        private final Long ventaId;
        private final LocalDateTime creadoEn;
        private final List<ItemInfo> items;
        
        public VentaCacheEntry(Long ventaId, LocalDateTime creadoEn, List<ItemInfo> items) {
            this.ventaId = ventaId;
            this.creadoEn = creadoEn;
            this.items = items;
        }
        
        public Long getVentaId() {
            return ventaId;
        }
        
        public LocalDateTime getCreadoEn() {
            return creadoEn;
        }
        
        public List<ItemInfo> getItems() {
            return items;
        }
    }
    
    /**
     * Clase para almacenar información simplificada de items
     */
    private static class ItemInfo {
        private final Long productoId;
        private final Long presentacionId;
        private final Double cantidad;
        
        public ItemInfo(Long productoId, Long presentacionId, Double cantidad) {
            this.productoId = productoId;
            this.presentacionId = presentacionId;
            this.cantidad = cantidad;
        }
        
        public Long getProductoId() {
            return productoId;
        }
        
        public Long getPresentacionId() {
            return presentacionId;
        }
        
        public Double getCantidad() {
            return cantidad;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInfo itemInfo = (ItemInfo) o;
            return Objects.equals(productoId, itemInfo.productoId) &&
                   Objects.equals(presentacionId, itemInfo.presentacionId) &&
                   Objects.equals(cantidad, itemInfo.cantidad);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(productoId, presentacionId, cantidad);
        }
    }
    
    /**
     * Verifica si existe una venta duplicada reciente para el usuario.
     * 
     * @param usuarioId ID del usuario
     * @param ventaItemList Lista de items de la venta a validar
     * @return ID de la venta duplicada si existe, null si no hay duplicado
     */
    public Long verificarVentaDuplicada(Long usuarioId, List<VentaItemInput> ventaItemList) {
        if (usuarioId == null || ventaItemList == null || ventaItemList.isEmpty()) {
            return null;
        }
        
        List<VentaCacheEntry> ventasUsuario = ventasCache.get(usuarioId);
        if (ventasUsuario == null || ventasUsuario.isEmpty()) {
            log.debug("🔍 Validación venta duplicada: Usuario {} no tiene ventas recientes en cache", usuarioId);
            return null;
        }
        
        log.debug("🔍 Validación venta duplicada: Usuario {} tiene {} ventas recientes en cache", usuarioId, ventasUsuario.size());
        
        // Convertir items de input a ItemInfo para comparación
        List<ItemInfo> itemsNuevaVenta = ventaItemList.stream()
            .map(item -> new ItemInfo(
                item.getProductoId(),
                item.getPresentacionId(),
                item.getCantidad()
            ))
            .sorted(Comparator.comparing(ItemInfo::getProductoId)
                .thenComparing(ItemInfo::getPresentacionId)
                .thenComparing(ItemInfo::getCantidad))
            .collect(Collectors.toList());
        
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime tiempoLimite = ahora.minusSeconds(TIEMPO_MINIMO_SEGUNDOS);
        
        // Buscar ventas recientes (últimos 5 segundos)
        int ventasRecientesRevisadas = 0;
        for (VentaCacheEntry entrada : ventasUsuario) {
            if (entrada.getCreadoEn().isAfter(tiempoLimite)) {
                ventasRecientesRevisadas++;
                // Comparar items
                if (sonItemsIguales(entrada.getItems(), itemsNuevaVenta)) {
                    long segundosDesdeCreacion = java.time.Duration.between(entrada.getCreadoEn(), ahora).getSeconds();
                    log.warn("⚠️ VENTA DUPLICADA DETECTADA: Usuario {} intentó crear una venta idéntica a la venta {} creada hace {} segundos",
                        usuarioId, entrada.getVentaId(), segundosDesdeCreacion);
                    return entrada.getVentaId();
                }
            }
        }
        
        if (ventasRecientesRevisadas > 0) {
            log.debug("✅ Validación venta duplicada: Usuario {} - Se revisaron {} ventas recientes, ninguna duplicada", usuarioId, ventasRecientesRevisadas);
        }
        
        return null;
    }
    
    /**
     * Compara dos listas de items para verificar si son iguales
     */
    private boolean sonItemsIguales(List<ItemInfo> items1, List<ItemInfo> items2) {
        if (items1.size() != items2.size()) {
            return false;
        }
        
        // Ordenar ambas listas para comparación
        List<ItemInfo> sorted1 = new ArrayList<>(items1);
        List<ItemInfo> sorted2 = new ArrayList<>(items2);
        
        sorted1.sort(Comparator.comparing(ItemInfo::getProductoId)
            .thenComparing(ItemInfo::getPresentacionId)
            .thenComparing(ItemInfo::getCantidad));
        
        sorted2.sort(Comparator.comparing(ItemInfo::getProductoId)
            .thenComparing(ItemInfo::getPresentacionId)
            .thenComparing(ItemInfo::getCantidad));
        
        return sorted1.equals(sorted2);
    }
    
    /**
     * Agrega una venta al cache después de ser creada exitosamente
     */
    public void agregarVentaAlCache(Long usuarioId, Long ventaId, LocalDateTime creadoEn, List<VentaItem> ventaItems) {
        if (usuarioId == null || ventaId == null || creadoEn == null || ventaItems == null) {
            return;
        }
        
        // Convertir VentaItem a ItemInfo
        List<ItemInfo> items = ventaItems.stream()
            .map(item -> new ItemInfo(
                item.getProducto() != null ? item.getProducto().getId() : null,
                item.getPresentacion() != null ? item.getPresentacion().getId() : null,
                item.getCantidad()
            ))
            .collect(Collectors.toList());
        
        VentaCacheEntry nuevaEntrada = new VentaCacheEntry(ventaId, creadoEn, items);
        
        ventasCache.compute(usuarioId, (key, ventas) -> {
            if (ventas == null) {
                ventas = new ArrayList<>();
            }
            
            // Agregar al inicio
            ventas.add(0, nuevaEntrada);
            
            // Mantener solo las últimas MAX_CACHE_SIZE ventas
            if (ventas.size() > MAX_CACHE_SIZE) {
                ventas = ventas.subList(0, MAX_CACHE_SIZE);
            }
            
            return ventas;
        });
        
        log.debug("✅ Venta {} agregada al cache para usuario {}", ventaId, usuarioId);
    }
    
    /**
     * Limpia el cache periódicamente, removiendo entradas antiguas (más de 1 minuto)
     */
    @Scheduled(fixedRate = 60000) // Cada minuto
    public void limpiarCache() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime tiempoLimite = ahora.minusMinutes(1);
        
        int totalEliminadas = 0;
        
        for (Map.Entry<Long, List<VentaCacheEntry>> entry : ventasCache.entrySet()) {
            List<VentaCacheEntry> ventas = entry.getValue();
            if (ventas == null) {
                continue;
            }
            
            // Filtrar solo las entradas recientes (último minuto)
            List<VentaCacheEntry> ventasRecientes = ventas.stream()
                .filter(v -> v.getCreadoEn().isAfter(tiempoLimite))
                .collect(Collectors.toList());
            
            int eliminadas = ventas.size() - ventasRecientes.size();
            totalEliminadas += eliminadas;
            
            if (ventasRecientes.isEmpty()) {
                // Si no hay ventas recientes, remover el usuario del cache
                ventasCache.remove(entry.getKey());
            } else {
                entry.setValue(ventasRecientes);
            }
        }
        
        if (totalEliminadas > 0) {
            log.debug("🧹 Cache limpiado: {} entradas antiguas removidas", totalEliminadas);
        }
    }
    
    /**
     * Obtiene el tamaño actual del cache (para monitoreo)
     */
    public int getTamanioCache() {
        return ventasCache.values().stream()
            .mapToInt(List::size)
            .sum();
    }
}

