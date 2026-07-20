package com.franco.dev.service.financiero;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.financiero.enums.TipoPadreGastoModulo;
import com.franco.dev.repository.activos.EnteRepository;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreGastoEnteValidationService {

    private final EnteRepository enteRepository;
    private final TipoGastoModuloReglasService moduloReglasService;

    public Ente validarYResolverEnte(TipoGasto tipoGasto, Long enteId) {
        if (tipoGasto == null) {
            if (enteId != null) {
                throw new GraphQLException("No se puede vincular un activo sin un tipo de gasto válido.");
            }
            return null;
        }

        TipoPadreGastoModulo modulo = tipoGasto.getModuloPadre();
        TipoEnte tipoEnteRequerido = moduloReglasService.tipoEnteEsperado(modulo);

        if (tipoEnteRequerido == null) {
            if (enteId != null) {
                throw new GraphQLException(
                        "El tipo de gasto \"" + tipoGasto.getDescripcion()
                                + "\" no admite vinculación a un activo (inmueble, vehículo, mueble o equipo).");
            }
            return null;
        }

        if (enteId == null) {
            throw new GraphQLException(
                    "Debe seleccionar " + etiquetaActivo(tipoEnteRequerido)
                            + " para el tipo de gasto \"" + tipoGasto.getDescripcion() + "\".");
        }

        Ente ente = enteRepository.findById(enteId)
                .orElseThrow(() -> new GraphQLException("El activo seleccionado no existe."));

        if (Boolean.FALSE.equals(ente.getActivo())) {
            throw new GraphQLException("El activo seleccionado no está activo.");
        }

        if (ente.getTipoEnte() != tipoEnteRequerido) {
            throw new GraphQLException(
                    "El activo seleccionado no corresponde al módulo del tipo de gasto. Se esperaba "
                            + etiquetaActivo(tipoEnteRequerido) + ".");
        }

        return ente;
    }

    private String etiquetaActivo(TipoEnte tipo) {
        switch (tipo) {
            case VEHICULO:
                return "un vehículo";
            case MUEBLE:
                return "un mueble";
            case INMUEBLE:
                return "un inmueble";
            case EQUIPO:
                return "un equipo";
            default:
                return "un activo";
        }
    }
}
