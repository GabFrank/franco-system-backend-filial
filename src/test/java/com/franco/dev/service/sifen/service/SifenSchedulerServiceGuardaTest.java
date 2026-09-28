package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Guarda del scheduler: el filial solo envia y consulta los documentos electronicos de SU
 * sucursal que nacen de una factura. Los de nota de credito / remision los emite el central
 * y llegan por replicacion (factura_legal_id NULL); los de otra sucursal tampoco son suyos.
 */
class SifenSchedulerServiceGuardaTest {

    private static final Long SUCURSAL_PROPIA = 24L;

    private SifenService sifenService;
    private DocumentoElectronicoService documentoElectronicoService;
    private LoteDEService loteDEService;
    private SifenSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        sifenService = mock(SifenService.class);
        documentoElectronicoService = mock(DocumentoElectronicoService.class);
        loteDEService = mock(LoteDEService.class);
        scheduler = new SifenSchedulerService(sifenService, documentoElectronicoService, loteDEService, SUCURSAL_PROPIA);
        ReflectionTestUtils.setField(scheduler, "maxDocumentosPorLote", 50);
    }

    @Test
    void crearYEnviarLotes_soloAgrupaLosDEPropiosConFactura() throws Exception {
        DocumentoElectronico propio = de(1L, SUCURSAL_PROPIA, true);
        DocumentoElectronico deNota = de(3L, SUCURSAL_PROPIA, false);
        DocumentoElectronico otraSucursal = de(5L, 7L, true);
        when(documentoElectronicoService.findByEstado(EstadoDE.PENDIENTE))
                .thenReturn(Arrays.asList(propio, deNota, otraSucursal));
        LoteDE lote = lote(10L, EstadoLoteDE.PENDIENTE_ENVIO);
        when(sifenService.crearLote()).thenReturn(lote);
        when(loteDEService.findById(10L)).thenReturn(Optional.of(lote));

        scheduler.crearYEnviarLotes();

        verify(sifenService).vincularDocumentosALote(lote, Collections.singletonList(propio));
        verify(sifenService, times(1)).enviarLote(lote);
    }

    @Test
    void crearYEnviarLotes_sinDEPropiosNoCreaLote() throws Exception {
        when(documentoElectronicoService.findByEstado(EstadoDE.PENDIENTE))
                .thenReturn(Arrays.asList(de(3L, SUCURSAL_PROPIA, false), de(5L, 7L, true)));

        scheduler.crearYEnviarLotes();

        verify(sifenService, never()).crearLote();
        verify(sifenService, never()).vincularDocumentosALote(any(), anyList());
        verify(sifenService, never()).enviarLote(any());
    }

    @Test
    void consultarLotesPendientes_noConsultaLotesConDEAjenos() throws Exception {
        LoteDE loteNota = lote(11L, EstadoLoteDE.EN_PROCESO);
        LoteDE loteMixto = lote(13L, EstadoLoteDE.EN_PROCESO);
        LoteDE lotePropio = lote(15L, EstadoLoteDE.EN_PROCESO);
        when(loteDEService.findByEstado(EstadoLoteDE.EN_PROCESO))
                .thenReturn(Arrays.asList(loteNota, loteMixto, lotePropio));
        when(documentoElectronicoService.findByLoteDe(loteNota))
                .thenReturn(Collections.singletonList(de(21L, SUCURSAL_PROPIA, false)));
        when(documentoElectronicoService.findByLoteDe(loteMixto))
                .thenReturn(Arrays.asList(de(23L, SUCURSAL_PROPIA, true), de(25L, 7L, true)));
        when(documentoElectronicoService.findByLoteDe(lotePropio))
                .thenReturn(Collections.singletonList(de(27L, SUCURSAL_PROPIA, true)));
        when(loteDEService.findById(15L)).thenReturn(Optional.of(lotePropio));

        scheduler.consultarLotesPendientes();

        verify(sifenService, never()).consultarLote(loteNota);
        verify(sifenService, never()).consultarLote(loteMixto);
        verify(sifenService).consultarLote(lotePropio);
    }

    @Test
    void procesarLotesAtrasados_noReenviaNiTocaLotesConDEAjenos() throws Exception {
        LoteDE loteNota = lote(31L, EstadoLoteDE.ERROR_ENVIO);
        LoteDE loteOtraSucursal = lote(33L, EstadoLoteDE.ERROR_RED);
        LoteDE lotePropio = lote(35L, EstadoLoteDE.PENDIENTE_ENVIO);
        when(loteDEService.findByEstados(anyList()))
                .thenReturn(Arrays.asList(loteNota, loteOtraSucursal, lotePropio));
        when(documentoElectronicoService.findByLoteDe(loteNota))
                .thenReturn(Collections.singletonList(de(41L, SUCURSAL_PROPIA, false)));
        when(documentoElectronicoService.findByLoteDe(loteOtraSucursal))
                .thenReturn(Collections.singletonList(de(43L, 7L, true)));
        when(documentoElectronicoService.findByLoteDe(lotePropio))
                .thenReturn(Collections.singletonList(de(45L, SUCURSAL_PROPIA, true)));
        when(loteDEService.findById(35L)).thenReturn(Optional.of(lotePropio));

        scheduler.procesarLotesAtrasados();

        verify(sifenService, never()).enviarLote(loteNota);
        verify(sifenService, never()).enviarLote(loteOtraSucursal);
        verify(loteDEService, never()).save(loteNota);
        verify(loteDEService, never()).save(loteOtraSucursal);
        verify(sifenService).enviarLote(lotePropio);
    }

    @Test
    void procesarLotesAtrasados_loteSinDocumentosSigueComoAntes() throws Exception {
        LoteDE vacio = lote(51L, EstadoLoteDE.PENDIENTE_ENVIO);
        when(loteDEService.findByEstados(anyList())).thenReturn(Collections.singletonList(vacio));
        when(documentoElectronicoService.findByLoteDe(vacio)).thenReturn(Collections.emptyList());

        scheduler.procesarLotesAtrasados();

        verify(loteDEService).save(eq(vacio));
        verify(sifenService, never()).enviarLote(any());
    }

    private static DocumentoElectronico de(Long id, Long sucursalId, boolean conFactura) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(id);
        de.setSucursalId(sucursalId);
        de.setEstado(EstadoDE.PENDIENTE);
        if (conFactura) {
            FacturaLegal factura = new FacturaLegal();
            factura.setId(id + 1000);
            de.setFacturaLegal(factura);
        }
        return de;
    }

    private static LoteDE lote(Long id, EstadoLoteDE estado) {
        LoteDE lote = new LoteDE();
        lote.setId(id);
        lote.setEstado(estado);
        lote.setIntentos(0);
        lote.setCreadoEn(LocalDateTime.now().minusMinutes(10));
        return lote;
    }
}
