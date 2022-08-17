package com.franco.dev.rabbit.dto;

import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
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
public class SaveConteoDto implements Serializable {

    private ConteoInput conteoInput;
    private List<ConteoMonedaInput> conteoMonedaInputList;
    private Long cajaId;
    private Boolean apertura;
    private Long sucId;

}
