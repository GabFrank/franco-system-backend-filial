package com.franco.dev.service.financiero;

import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.financiero.enums.TipoNaturalezaGasto;
import com.franco.dev.domain.financiero.enums.TipoPadreGastoModulo;
import com.franco.dev.graphql.financiero.dto.ModuloGastoInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reglas de comportamiento según módulo padre y naturaleza del tipo de gasto.
 * Centraliza la lógica usada en validaciones y en la capa de presentación.
 */
@Service
public class TipoGastoModuloReglasService {

    public TipoEnte tipoEnteEsperado(TipoPadreGastoModulo modulo) {
        if (modulo == null) {
            return null;
        }
        switch (modulo) {
            case VEHICULO:
                return TipoEnte.VEHICULO;
            case MUEBLE:
                return TipoEnte.MUEBLE;
            case INMUEBLE:
                return TipoEnte.INMUEBLE;
            case EQUIPOS:
                return TipoEnte.EQUIPO;
            case ANDE:
            case JUNTA_SANEAMIENTO:
            case IMPUESTO:
            case INTERNET:
            case SEGURIDAD:
            case BASURA:
            case SEGURO:
                return TipoEnte.INMUEBLE;
            default:
                return null;
        }
    }

    public boolean requiereEnteActivo(TipoPadreGastoModulo modulo) {
        return tipoEnteEsperado(modulo) != null;
    }

    /**
     * Módulos de activo que se pueden pagar en cuotas (inmueble, vehículo, mueble, equipo).
     */
    public boolean tieneCuotasActivo(TipoPadreGastoModulo modulo) {
        return modulo == TipoPadreGastoModulo.INMUEBLE
                || modulo == TipoPadreGastoModulo.MUEBLE
                || modulo == TipoPadreGastoModulo.VEHICULO
                || modulo == TipoPadreGastoModulo.EQUIPOS;
    }

    /**
     * Cuotas de compra del activo (inmueble, vehículo, mueble, equipo pagando).
     */
    public boolean esPagoCuotaPorDefecto(TipoPadreGastoModulo modulo, TipoNaturalezaGasto naturaleza) {
        if (!esGastoContinuoRecurrente(naturaleza)) {
            return false;
        }
        return tieneCuotasActivo(modulo);
    }

    /**
     * El gasto impacta las finanzas del activo vinculado.
     */
    public boolean afectaFinanzasActivoPorDefecto(TipoPadreGastoModulo modulo, TipoNaturalezaGasto naturaleza) {
        if (modulo == null) {
            return false;
        }
        if (esModuloServicioContinuo(modulo)) {
            return true;
        }
        if (esGastoContinuoRecurrente(naturaleza)) {
            return tieneCuotasActivo(modulo);
        }
        return false;
    }

    public boolean requiereDiaVencimiento(TipoPadreGastoModulo modulo, TipoNaturalezaGasto naturaleza) {
        if (!esGastoContinuoRecurrente(naturaleza)) {
            return false;
        }
        return esModuloServicioContinuo(modulo)
                || modulo == TipoPadreGastoModulo.INMUEBLE;
    }

    public boolean requiereLecturaMedidor(TipoPadreGastoModulo modulo, TipoNaturalezaGasto naturaleza) {
        if (!esGastoContinuoRecurrente(naturaleza)) {
            return false;
        }
        return modulo == TipoPadreGastoModulo.ANDE
                || modulo == TipoPadreGastoModulo.JUNTA_SANEAMIENTO;
    }

    public boolean requiereNis(TipoPadreGastoModulo modulo, TipoNaturalezaGasto naturaleza) {
        return esGastoContinuoRecurrente(naturaleza) && modulo == TipoPadreGastoModulo.ANDE;
    }

    public boolean montoVariableEnContinuo(TipoPadreGastoModulo modulo, TipoNaturalezaGasto naturaleza) {
        if (!esGastoContinuoRecurrente(naturaleza)) {
            return false;
        }
        return esModuloServicioContinuo(modulo);
    }

    public String etiquetaModulo(TipoPadreGastoModulo modulo) {
        if (modulo == null) {
            return "Otro";
        }
        switch (modulo) {
            case VEHICULO:
                return "Vehículo";
            case MUEBLE:
                return "Mueble";
            case INMUEBLE:
                return "Inmueble";
            case EQUIPOS:
                return "Equipo";
            case PERSONAS:
                return "Persona";
            case ANDE:
                return "ANDE (energía eléctrica)";
            case JUNTA_SANEAMIENTO:
                return "Junta de Saneamiento (agua)";
            case IMPUESTO:
                return "Impuesto";
            case INTERNET:
                return "Internet";
            case SEGURIDAD:
                return "Seguridad privada";
            case BASURA:
                return "Recolección de basura";
            case SEGURO:
                return "Seguro";
            default:
                return "Otro";
        }
    }

    private boolean esModuloServicioContinuo(TipoPadreGastoModulo modulo) {
        return modulo == TipoPadreGastoModulo.ANDE
                || modulo == TipoPadreGastoModulo.JUNTA_SANEAMIENTO
                || modulo == TipoPadreGastoModulo.IMPUESTO
                || modulo == TipoPadreGastoModulo.INTERNET
                || modulo == TipoPadreGastoModulo.SEGURIDAD
                || modulo == TipoPadreGastoModulo.BASURA
                || modulo == TipoPadreGastoModulo.SEGURO;
    }

    private boolean esGastoContinuoRecurrente(TipoNaturalezaGasto naturaleza) {
        return naturaleza == TipoNaturalezaGasto.CONTINUO
                || naturaleza == TipoNaturalezaGasto.RECURRENTE;
    }

    /**
     * Grupo de presentación del módulo: ACTIVO, SERVICIO u OTRO.
     */
    public String grupoModulo(TipoPadreGastoModulo modulo) {
        if (modulo == null) {
            return "OTRO";
        }
        if (esModuloServicioContinuo(modulo)) {
            return "SERVICIO";
        }
        switch (modulo) {
            case INMUEBLE:
            case VEHICULO:
            case MUEBLE:
            case EQUIPOS:
            case PERSONAS:
                return "ACTIVO";
            default:
                return "OTRO";
        }
    }

    /**
     * Orden de presentación de los módulos en la UI.
     */
    private static final List<TipoPadreGastoModulo> ORDEN_MODULOS = Arrays.asList(
            TipoPadreGastoModulo.INMUEBLE,
            TipoPadreGastoModulo.VEHICULO,
            TipoPadreGastoModulo.MUEBLE,
            TipoPadreGastoModulo.EQUIPOS,
            TipoPadreGastoModulo.PERSONAS,
            TipoPadreGastoModulo.ANDE,
            TipoPadreGastoModulo.JUNTA_SANEAMIENTO,
            TipoPadreGastoModulo.INTERNET,
            TipoPadreGastoModulo.SEGURIDAD,
            TipoPadreGastoModulo.BASURA,
            TipoPadreGastoModulo.SEGURO,
            TipoPadreGastoModulo.IMPUESTO,
            TipoPadreGastoModulo.OTRO
    );

    /**
     * Catálogo de módulos padre con sus banderas de comportamiento.
     * Única fuente de verdad consumida por el frontend.
     */
    public List<ModuloGastoInfo> listarModulos() {
        List<ModuloGastoInfo> modulos = new ArrayList<>();
        for (TipoPadreGastoModulo modulo : ORDEN_MODULOS) {
            TipoEnte tipoEnte = tipoEnteEsperado(modulo);
            modulos.add(new ModuloGastoInfo(
                    modulo,
                    etiquetaModulo(modulo),
                    grupoModulo(modulo),
                    esModuloServicioContinuo(modulo),
                    tieneCuotasActivo(modulo),
                    requiereEnteActivo(modulo),
                    tipoEnte != null ? tipoEnte.name() : null,
                    esModuloServicioContinuo(modulo) || modulo == TipoPadreGastoModulo.INMUEBLE,
                    modulo == TipoPadreGastoModulo.ANDE || modulo == TipoPadreGastoModulo.JUNTA_SANEAMIENTO,
                    modulo == TipoPadreGastoModulo.ANDE
            ));
        }
        return modulos;
    }
}
