package com.franco.dev.domain.financiero;

import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.PdvCajaInput;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SolicitudAperturaCaja {
    private PdvCajaInput cajaInput;
    private ConteoInput conteoInput;
    private List<ConteoMoneda> conteoMonedaList;
}



