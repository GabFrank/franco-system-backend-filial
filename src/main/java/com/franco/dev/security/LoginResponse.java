package com.franco.dev.security;

import com.franco.dev.domain.empresarial.Sucursal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long usuarioId;
    private String token;
    private Sucursal sucursal;
    private String aviso;

    public LoginResponse(Long usuarioId, String token, Sucursal sucursal) {
        this.usuarioId = usuarioId;
        this.token = token;
        this.sucursal = sucursal;
    }
}