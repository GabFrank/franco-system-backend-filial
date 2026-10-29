package com.franco.dev.security;

import com.franco.dev.domain.configuracion.Local;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.security.jwt.JwtGenerator;
import com.franco.dev.service.configuracion.LocalService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    private Environment env;

    @Autowired
    private SucursalService sucursalService;


    public TokenController(UsuarioService service, JwtGenerator jwtGenerator) {
        this.service = service;
        this.jwtGenerator = jwtGenerator;
    }

    /** Los usuarios nacen en el central y llegan por replicacion: el filial no da altas. */
    @PostMapping(path = "/new")
    @ResponseBody
    public ResponseEntity<LoginResponse> newUser(@RequestBody final Usuario usuario) {
        throw new ResponseStatusException(HttpStatus.GONE, "El alta de usuarios se hace desde el central");
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<LoginResponse> generate(@RequestBody final JwtUser jwtUser) {
        String nickname = jwtUser.getNickname() != null ? jwtUser.getNickname().trim() : "";
        Usuario usuario = service.findByNickname(nickname).orElse(null);
        List<Local> localList = localService.findAll();
        if (usuario != null) {
            Boolean matches = jwtUser.getPassword().toUpperCase().equals(usuario.getPassword().toUpperCase());
            if (matches) {
                if (Boolean.FALSE.equals(usuario.getActivo())) {
                    return ResponseEntity.ok(new LoginResponse(null, null, null,
                            "Usuario inactivo, contacte al administrador"));
                }
                if (localList.size() == 0) {
                    throw new GraphQLException("Servidor no configurado");
                } else {
                    jwtUser.setRoles(service.getRoles(usuario.getId()));
                }
            } else {
                return ResponseEntity.ok(new LoginResponse(null, null, null, "Ups!! Contraseña inválida."));
            }
        } else if (localList.size() == 0) {
            throw new GraphQLException("Servidor no configurado");
        } else {
            return ResponseEntity.ok(new LoginResponse(null, null, null, "Ups!! El usuario no existe"));
        }
        LoginResponse response = new LoginResponse(usuario.getId(), jwtGenerator.generate(jwtUser), sucursalService.sucursalActual());
        return ResponseEntity.ok(response);
    }
}

