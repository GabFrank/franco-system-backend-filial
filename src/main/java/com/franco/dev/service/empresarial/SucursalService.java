package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuracion.LocalService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SucursalService extends CrudService<Sucursal, SucursalRepository> {

    @Autowired
    private Environment environment;

    @Autowired
    private SucursalRepository repository;

    @Autowired
    private LocalService localService;

    @Override
    public SucursalRepository getRepository() {
        return repository;
    }

    public List<Sucursal> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    @Override
    public List<Sucursal> findAll(Pageable pageable) {
        return repository.findAllByOrderByIdAsc();
    }

    @Override
    public Sucursal save(Sucursal entity) {
        Sucursal e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public Sucursal sucursalActual() {
        return findById(idSucursalActual()).orElse(null);
    }

    /**
     * El id de la sucursal de ESTE filial, sacado de su propia configuracion.
     * <p>
     * Es la unica fuente valida: un filial atiende una sola sucursal y lo sabe sin preguntarle a
     * nadie.
     */
    public Long idSucursalActual() {
        String v = environment.getProperty("sucursalId");
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("este filial no tiene sucursalId configurado");
        }
        return Long.valueOf(v.trim());
    }

    /**
     * Valida que la sucursal que mando el cliente sea la de este filial, y devuelve la buena.
     *
     * <p><b>Por que existe.</b> Media docena de resolvers de este repo reciben {@code sucId} como
     * parametro y lo usan tal cual. {@code VentaGraphQL} y {@code GastoGraphQL} hacen lo
     * contrario --derivan la sucursal del servidor-- y esa es la convencion correcta: el cliente
     * no tiene autoridad para decir en que sucursal esta.
     *
     * <p>Importa especialmente en {@code venta_tarjeta}, que es {@code BRANCH_TO_MAIN} con PK
     * compuesta {@code (id, sucursal_id)}: una fila escrita con la sucursal de otro filial sube a
     * central con atribucion falsa.
     *
     * <p>Se acepta {@code null} --el cliente no la manda y se usa la propia-- pero no se acepta
     * una distinta: eso es un error del cliente o un intento, y en los dos casos conviene que
     * falle fuerte y temprano.
     */
    public Long exigirSucursalPropia(Long delCliente) {
        Long propia = idSucursalActual();
        if (delCliente == null || propia.equals(delCliente)) return propia;
        throw new IllegalArgumentException(
                "esta operacion es de la sucursal " + propia + ", no de la " + delCliente);
    }
}