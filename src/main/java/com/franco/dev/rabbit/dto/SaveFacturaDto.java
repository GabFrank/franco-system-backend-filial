package com.franco.dev.rabbit.dto;

import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class SaveFacturaDto implements Serializable {
    private FacturaLegalInput facturaLegalInput;
    private List<FacturaLegalItemInput> facturaLegalItemInputList;
}
