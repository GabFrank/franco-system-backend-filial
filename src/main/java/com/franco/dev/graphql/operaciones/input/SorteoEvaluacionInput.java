package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.util.List;

@Data
public class SorteoEvaluacionInput {
    private Double totalGs;
    private List<CobroDetalleInput> cobroDetalleList;
}
