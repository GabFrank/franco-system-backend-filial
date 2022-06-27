package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.input.TransferenciaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TransferenciaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TransferenciaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MovimientoStockService movimientoStockService;

    public Optional<Transferencia> transferencia(Long id) {
        return service.findById(id);
    }

    public List<Transferencia> transferencias(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Transferencia> transferenciaPorSucursalOrigenId(Long id) {
        return service.findBySucursalOrigenId(id);
    }

    public List<Transferencia> transferenciaPorSucursalDesctinoId(Long id) {
        return service.findBySucursalDestinoId(id);
    }

    public List<Transferencia> transferenciaPorFecha(String start, String end) {
        if (end == null) {
            end = start;
        }
        return service.findByDate(start, end);
    }

    public Transferencia saveTransferencia(TransferenciaInput input) {
        ModelMapper m = new ModelMapper();
        Transferencia e = m.map(input, Transferencia.class);
        if (input.getUsuarioPreTransferenciaId() != null)
            e.setUsuarioPreTransferencia(usuarioService.findById(input.getUsuarioPreTransferenciaId()).orElse(null));
        if (input.getUsuarioPreparacionId() != null)
            e.setUsuarioPreparacion(usuarioService.findById(input.getUsuarioPreparacionId()).orElse(null));
        if (input.getUsuarioTransporteId() != null)
            e.setUsuarioTransporte(usuarioService.findById(input.getUsuarioTransporteId()).orElse(null));
        if (input.getUsuarioRecepcionId() != null)
            e.setUsuarioRecepcion(usuarioService.findById(input.getUsuarioRecepcionId()).orElse(null));
        e.setSucursalOrigen(sucursalService.findById(input.getSucursalOrigenId()).orElse(null));
        e.setSucursalDestino(sucursalService.findById(input.getSucursalDestinoId()).orElse(null));
        return service.save(e);
    }

    public Boolean avanzarEtapaTransferencia(Long id, EtapaTransferencia etapa, Long usuarioId) {
        Boolean ok = false;
        Transferencia transferencia = transferencia(id).orElse(null);
        if (transferencia != null) {
            Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
            switch (etapa){
                case PREPARACION_MERCADERIA:
                    transferencia.setUsuarioPreparacion(usuario);
                    break;
                case TRANSPORTE_VERIFICACION:
                    transferencia.setUsuarioTransporte(usuario);
                    break;
                case TRANSPORTE_EN_CAMINO:
                    movimientoStockService.bajaStockPorTransferencia(transferencia.getId());
                    transferencia.setEstado(TransferenciaEstado.EN_TRANSITO);
                    break;
                case RECEPCION_EN_VERIFICACION:
                    transferencia.setUsuarioRecepcion(usuario);
                    transferencia.setEstado(TransferenciaEstado.EN_DESTINO);
                    break;
                case RECEPCION_CONCLUIDA:
                    movimientoStockService.altaStockPorTransferencia(transferencia.getId());
                    transferencia.setEstado(TransferenciaEstado.CONLCUIDA);
                    break;
            }
            transferencia.setEtapa(etapa);
            propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA);
            ok = true;
        }
        return ok;
    }

    public Boolean deleteTransferencia(Long id) {
        return service.deleteById(id);
    }

    public Long countTransferencia() {
        return service.count();
    }

}
