package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.productos.PresentacionRepository;
import com.franco.dev.repository.productos.ProductoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PresentacionService extends CrudService<Presentacion, PresentacionRepository> {

    private final PresentacionRepository repository;
    private final ProductoRepository productoRepository;

    @Override
    public PresentacionRepository getRepository() {
        return repository;
    }

    public List<Presentacion> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public List<Presentacion> findByProductoId(Long id) {
        return repository.findByProductoId(id);
    }

//    public Presentacion recibirPresentacion(Presentacion pCentral) {
//        Presentacion foundPresentacion = repository.findByIdCentral(pCentral.getId());
//        Producto producto = productoRepository.findByIdCentral(pCentral.getProducto().getId());
//        if (producto != null) {
//            if (foundPresentacion == null) {
//                foundPresentacion = pCentral;
//                foundPresentacion.setIdCentral(pCentral.getId());
//                foundPresentacion.setId(null);
//                foundPresentacion = save(foundPresentacion);
//            }
//        }
//        return foundPresentacion;
//    }

    @Override
    public Presentacion save(Presentacion entity) {
        Presentacion e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public Presentacion findByPrincipalAndProductoId(Boolean principal, Long id) {
        return repository.findByPrincipalAndProductoId(principal, id);
    }
}
