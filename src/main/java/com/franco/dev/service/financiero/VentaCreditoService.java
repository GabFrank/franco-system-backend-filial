package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.personas.UsuarioService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaCreditoService extends CrudService<VentaCredito, VentaCreditoRepository> {

    private final VentaCreditoRepository repository;
    @Autowired
    private PushNotificationService pushNotificationService;
    @Autowired
    private NotificationTemplateService notificationTemplateService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private Environment env;

    @Override
    public VentaCreditoRepository getRepository() {
        return repository;
    }

//    public List<VentaCredito> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<VentaCredito> findByClienteAndVencimiento(Long id, LocalDateTime inicio, LocalDateTime fin) {
        return repository.findAllByClienteIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByIdAsc(id, inicio, fin);
    }

    @Override
    public VentaCredito save(VentaCredito entity) {
        VentaCredito saved = super.save(entity);

        // Enviar notificación push
        try {
            Usuario usuario = usuarioService.findByPersonaId(saved.getCliente().getPersona().getId());
            if (usuario != null) {
                DecimalFormat df = new DecimalFormat("#,###");
                PushNotificationRequest request = notificationTemplateService.ventaCreditoRealizada(saved, null, df);
                request.setUsuarioIds(Collections.singletonList(usuario.getId()));
                pushNotificationService.sendPushNotificationToToken(request);
            }
        } catch (Exception ex) {
            // No fallar el guardado si hay error en notificaciones
            ex.printStackTrace();
        }

//        personaPublisher.publish(p);
        return saved;
    }

    @Override
    public VentaCredito saveAndSend(VentaCredito entity, Boolean recibir) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getSucursalId() == null) entity.setSucursalId(Long.valueOf(env.getProperty("sucursalId")));
        VentaCredito e = super.save(entity);
//        propagacionService.propagarEntidad(e, TipoEntidad.VENTA_CREDITO, recibir);
        return e;
    }
}