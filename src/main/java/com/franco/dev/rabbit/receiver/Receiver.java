package com.franco.dev.rabbit.receiver;

import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.service.rabbitmq.PropagacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Receiver {

    @Autowired
    private PropagacionService propagacionService;


    private Logger log = LoggerFactory.getLogger(Receiver.class);

    @RabbitListener(queues = "${queue}")
    public void receive(RabbitDto dto) {
        log.info("recibiendo");
        switch (dto.getTipoAccion()) {
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

    @RabbitListener(queues = "${queue-reply-to}")
    public Object receiveAndReply(RabbitDto dto) {
        switch (dto.getTipoAccion()) {
            case SOLICITAR_STOCK_PRODUCTO:
                return propagacionService.movimientoStockService.stockByProductoId((Long) dto.getEntidad());
            case SOLICITAR_ENTIDAD:
            case GUARDAR:
            case DELETE:
                return propagacionService.crudEntidad(dto);
            default:
                return null;
        }
    }

}
