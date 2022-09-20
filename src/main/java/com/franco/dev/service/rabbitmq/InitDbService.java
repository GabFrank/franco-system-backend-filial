package com.franco.dev.service.rabbitmq;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class InitDbService {


    private List<String> queryList = new ArrayList<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PropagacionService propagacionService;

    @Transactional
    public Boolean initDb() {
        String superUser = "INSERT INTO personas.usuario (id, password,usuario_id,creado_en,nickname,email,activo) " +
                "values(1000,'admin',null,NULL,'admin',NULL,true);";
        String personaSinNombre = "INSERT INTO personas.persona (id,nombre,apodo,documento,nacimiento,sexo,direccion,ciudad_id,telefono,social_media,imagenes,creado_en,usuario_id,email,id_central) VALUES " +
                "(0,'SIN NOMBRE','','X',NULL,NULL,'',NULL,'',NULL,NULL,NULL,1000,'',NULL);";
        String clienteSinNombre = "INSERT INTO personas.cliente (id,persona_id,usuario_id) " +
                "VALUES (0,0,1000);";
        String truncQuery = "select public.reiniciartablas('franco','administrativo'),\n" +
                " public.reiniciartablas('franco','configuraciones'),\n" +
                " public.reiniciartablas('franco','empresarial'),\n" +
                " public.reiniciartablas('franco','equipos'),\n" +
                " public.reiniciartablas('franco','financiero'),\n" +
                " public.reiniciartablas('franco','operaciones'),\n" +
                " public.reiniciartablas('franco','personas'),\n" +
                " public.reiniciartablas('franco','productos'),\n" +
                " public.reiniciartablas('franco','public'),\n" +
                " public.reiniciartablas('franco','vehiculos'),\n" +
                " public.reiniciartablas('franco','personas'),\n" +
                " public.reiniciartablas('franco','general');";
        try {
            jdbcTemplate.execute(truncQuery);
            jdbcTemplate.execute(superUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
