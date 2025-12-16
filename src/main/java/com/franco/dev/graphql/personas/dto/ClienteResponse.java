package com.franco.dev.graphql.personas.dto;

import com.franco.dev.domain.personas.Cliente;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteResponse {
    private Cliente cliente;
    private ClienteDatosBasicos datosBasicos;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    @Builder.Default
    private List<String> errores = new ArrayList<>();
    private Boolean exito;
}

