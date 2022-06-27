package com.franco.dev.rabbit.receiver;

import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.service.rabbitmq.PropagacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
public class Receiver {

    @Autowired
    private PropagacionService propagacionService;


    private Logger log = LoggerFactory.getLogger(Receiver.class);

    @RabbitListener(queues = "${queue}")
    public void receive(RabbitDto dto) {
        log.info("recibiendo"   );
        switch (dto.getTipoAccion()) {
            case VERIFICAR:
                propagacionService.initDb();
                break;
            case SOLICITAR_DB:
                propagacionService.guardarEntidades(dto);
                break;
            case GUARDAR:
            case DELETE:
                propagacionService.crudEntidad(dto);
                break;
            default:
                break;
        }
    }

}
