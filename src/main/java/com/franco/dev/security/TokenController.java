package com.franco.dev.security;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.security.jwt.JwtGenerator;
import com.franco.dev.service.configuracion.LocalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequestMapping("/login")
@CrossOrigin
public class TokenController {

    @Autowired
    private final UsuarioService service;

    @Autowired
    private LocalService localService;

    private JwtGenerator jwtGenerator;

    @Autowired
    private RestTemplate restTemplate;


    public TokenController(UsuarioService service, JwtGenerator jwtGenerator) {
        this.service = service;
        this.jwtGenerator = jwtGenerator;
    }

    @PostMapping(path = "/new")
    @ResponseBody
    public ResponseEntity<LoginResponse> newUser(@RequestBody final Usuario usuario) {
        if (!service.existsByNickname(usuario.getNickname())) {
            if (!service.existsByEmail(usuario.getEmail())) {
                if (usuario.getNickname() != null && usuario.getPassword() != null) {
                    Usuario newUser = service.save(usuario);
                    JwtUser jwtUser = new JwtUser();
                    jwtUser.setId(newUser.getId());
                    jwtUser.setNickname(newUser.getNickname().toUpperCase());
                    jwtUser.setPassword(usuario.getPassword().toUpperCase());
                    return generate(jwtUser);
                } else {
                    throw new GraphQLException("Datos incompletos");
                }
            } else {
                throw new GraphQLException("Ya existe un usuario con utilizando este email.");
            }
        } else {
            throw new GraphQLException("Nombre de usuario ya existe, intente con otro");
        }
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<LoginResponse> generate(@RequestBody final JwtUser jwtUser) {
        Usuario usuario = service.findByNickname(jwtUser.getNickname()).orElse(null);
        List<Local> localList = localService.findAll();
        if (usuario != null) {
            Boolean matches = jwtUser.getPassword().toUpperCase().equals(usuario.getPassword().toUpperCase());
            if (matches) {
                if (localList.size() == 0) {
                    throw new GraphQLException("Servidor no configurado");
                } else {
                    jwtUser.setRoles(service.getRoles(usuario.getId()));
                }
            } else {
                throw new GraphQLException("Ups!! Contraseña inválida.");
            }
        } else if (localList.size() == 0) {
            throw new GraphQLException("Servidor no configurado");
        } else {
            throw new GraphQLException("Ups!! El usuario no existe");
        }
        LoginResponse response = new LoginResponse(usuario.getId(), jwtGenerator.generate(jwtUser));
        return ResponseEntity.ok(response);
    }

}


@Data
@NoArgsConstructor
@AllArgsConstructor
class LoginResponse {
    Long usuarioId;
    String token;
}

