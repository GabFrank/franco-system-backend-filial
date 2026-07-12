package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.service.empresarial.ImpresoraService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolver de impresoras en la filial: SOLO LECTURA. El registro se administra en el
 * servidor central y se replica hacia la filial. Aca la filial expone el registro
 * replicado y, sobre todo, sus colas de impresion locales (CUPS) para descubrimiento.
 */
@Component
public class ImpresoraGraphQL implements GraphQLQueryResolver {

    @Autowired
    private ImpresoraService service;

    public Optional<Impresora> impresora(Long id) {
        return service.findById(id);
    }

    public List<Impresora> impresoras(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Impresora> impresorasPorSucursalId(Long id) {
        return service.findBySucursalId(id);
    }

    public CustomPage<Impresora> impresoraSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 10 : size;
        Pageable pageable = PageRequest.of(p, s);
        Page<Impresora> pageResult = service.buscarConPagina(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public List<Impresora> impresorasActivas() {
        return service.findActivas();
    }

    /**
     * Colas de impresion visibles localmente en esta filial (en Linux, las colas CUPS).
     */
    public List<String> impresorasDelSistema() {
        List<String> nombres = new ArrayList<>();
        for (PrintService p : PrintServiceLookup.lookupPrintServices(null, null)) {
            nombres.add(p.getName());
        }
        return nombres;
    }

    public Long countImpresora() {
        return service.count();
    }
}
