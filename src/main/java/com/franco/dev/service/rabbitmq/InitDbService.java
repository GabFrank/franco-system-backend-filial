package com.franco.dev.service.rabbitmq;

import com.franco.dev.service.rabbitmq.PropagacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
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
        String createSuperAdminQuery = "INSERT INTO personas.usuario (id,\"password\",usuario_id,creado_en,nickname,email,activo) values\n" +
                "(1000,'admin',null,NULL,'admin',NULL,true);";
        jdbcTemplate.query(truncQuery, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                jdbcTemplate.update(createSuperAdminQuery);
                propagacionService.solicitarDB();
            }
        });
        return true;
    }
}
