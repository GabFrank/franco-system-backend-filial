package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioSimilitudResult {
    private Usuario usuario;
    private Double similitud;
}
