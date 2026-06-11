package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.PreGastoDetalleFinanzas;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
public class PreGastoResolver implements GraphQLResolver<PreGasto> {

    public List<PreGastoDetalleFinanzas> finanzas(PreGasto preGasto) {
        return Collections.emptyList();
    }

    public Float montoPendienteRetiro(PreGasto preGasto) {
        return toFloat(preGasto.getMontoSolicitado());
    }

    public Float montoNoRendido(PreGasto preGasto) {
        return null;
    }

    public Float porcentajeRendicion(PreGasto preGasto) {
        return 0f;
    }

    public Float desvioVsSolicitado(PreGasto preGasto) {
        return null;
    }

    public String estadoEtiqueta(PreGasto preGasto) {
        return preGasto.getEstado() != null ? preGasto.getEstado().name() : null;
    }

    public String estadoIcono(PreGasto preGasto) {
        return null;
    }

    public String estadoColor(PreGasto preGasto) {
        return null;
    }

    public Long solicitudPagoId(PreGasto preGasto) {
        return null;
    }

    public Gasto gasto(PreGasto preGasto) {
        return null;
    }

    public String estadoRendicion(PreGasto preGasto) {
        return null;
    }

    public Boolean rindioGasto(PreGasto preGasto) {
        return false;
    }

    public LocalDateTime fechaRendicion(PreGasto preGasto) {
        return null;
    }

    private Float toFloat(BigDecimal value) {
        return value != null ? value.floatValue() : null;
    }
}
