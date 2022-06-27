package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.*;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ComboItemService;
import com.franco.dev.service.productos.IngredienteService;
import com.franco.dev.service.productos.ProductoIngredienteService;
import com.franco.dev.service.productos.SubFamiliaService;
import graphql.kickstart.tools.GraphQLResolver;
import kotlin.collections.ArrayDeque;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ComboResolver implements GraphQLResolver<Combo> {

    @Autowired
    private ComboItemService comboItemService;

    public List<ComboItem> comboItens(Combo c){
        return comboItemService.findByCombo(c.getId());
    }

}
