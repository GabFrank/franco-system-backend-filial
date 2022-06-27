package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.CodigoInput;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.rabbit.receiver.Receiver;
import com.franco.dev.rabbit.sender.Sender;
import com.franco.dev.repository.productos.CodigoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CodigoService extends CrudService<Codigo, CodigoRepository> {

    @Autowired
    private final CodigoRepository repository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private TipoPrecioService tipoPrecioService;

    @Override
    public CodigoRepository getRepository() {
        return repository;
    }

    @Autowired
    private Environment env;

//    @Autowired
//    private Sender sender;

    public Codigo findByProductoId(Long id){
        return repository.findbyProductoId(id);
    }

    public List<Codigo> findByCodigo(String texto){
        texto = texto.replace(' ', '%');
        return repository.findByCodigo(texto.toUpperCase());
    }

    public Codigo save(CodigoInput input) throws GraphQLException {

        Codigo p = null;
        ModelMapper m = new ModelMapper();
        Codigo e = m.map(input, Codigo.class);
        if(e.getCodigo()!=null) e.setCodigo(e.getCodigo().toUpperCase());
        if(input.getUsuarioId()!=null)e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getPresentacionId()!=null) e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));

//        if(input.getIdCentral()!=null){
//            p = repository.findByIdCentral(input.getIdCentral());
//            log.info("id central no es null");
//        }
//
//        if(p==null) {
//            p = repository.findByCodigo(input.getCodigo());
//        }
//
//        if(p!=null) {
//            e.setId(p.getId());
//        }
        p = repository.save(e);
//        if(!input.getPropagado() && p!=null){
//            input.setIdSucursalOrigen(Long.valueOf(env.getProperty("sucursalId")));
//            propagar(input);
//        }
        return p;
    }

//    @Override
//    public Boolean deleteById(Long id) {
//        Long idCentral = findById(id).get().getIdCentral();
//        if(idCentral!=null) {
//            propagarDelete(idCentral);
//            return super.deleteById(id);
//        }
//        return false;
//    }

//    public String propagar(CodigoInput input){
//        log.info("enviando codigo a central");
//        log.info(input.toString());
//        RabbitDto<CodigoInput> dto = new RabbitDto();
//        dto.setAccion(GUARDAR);
//        dto.setTipo(Receiver.CODIGO);
//        dto.setEntidad(input);
//        sender.send(dto, "central");
//        return "Success";
//    }
//
//    public String propagarDelete(Long idCentral){
//        log.info("enviando delete codigo a central");
//        log.info(idCentral.toString());
//        RabbitDto<CodigoInput> dto = new RabbitDto();
//        dto.setAccion(ELIMINAR);
//        dto.setTipo(Receiver.CODIGO);
//        CodigoInput input = new CodigoInput();
//        input.setIdCentral(idCentral);
//        dto.setEntidad(input);
//        sender.send(dto, "central");
//        return "Success";
//    }

//    public void receive(RabbitDto<CodigoInput> dto) {
//        log.info("recibiendo codigo");
//        log.info("accion: " + dto.getTipoAccion());
//        switch (dto.getTipoAccion()){
//            case GUARDAR:
//                save(dto.getEntidad());
//            case ELIMINAR:
//                deleteById(dto.getEntidad().getIdCentral());
//            case ACTUALIZAR:
//        }
//    }

    public List<Codigo> findByPresentacionId(Long id){
        return repository.findByPresentacionId(id);
    }

    public Codigo findPrincipalByPresentacionId(Long id) { return repository.findPrincipalByPresentacionId(id);}

}
