package com.franco.dev.rabbit.receiver;

import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.service.rabbitmq.PropagacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

@Component
public class Receiver {

    @Autowired
    private RabbitTemplate template;

    @Autowired
    private PropagacionService propagacionService;


    private Logger log = LoggerFactory.getLogger(Receiver.class);

    @RabbitListener(queues = "${queue}")
    public void receive(RabbitDto dto) {
        log.info("recibiendo normal " + dto.getTipoEntidad());
        switch (dto.getTipoAccion()) {
            case SOLICITAR_DB:
                propagacionService.guardarEntidades(dto);
                break;
            case GUARDAR:
            case DELETE:
                propagacionService.crudEntidad(dto);
                break;
            case GUARDAR_IMAGEN:
                propagacionService.guardarImagen(dto, dto.getTipoEntidad());
                break;
            case GUARDAR_ARCHIVO:
                propagacionService.guardarArchivo(dto);
                break;
            case ACTUALIZAR:
                propagacionService.actualizar(dto);
                break;
            case SOLICITAR_ENTIDAD:
                break;
            default:
                break;
        }
    }

    @RabbitListener(queues = "${queue-reply-to}", containerFactory = "servidor")
    public Object receiveAndReply(RabbitDto dto) {
        log.info("recibiendo reply " + dto.getTipoEntidad());
        Object result = null;
        try {
            switch (dto.getTipoAccion()) {
                case SOLICITAR_STOCK_PRODUCTO:
                    Float stock = propagacionService.stockByProductoId((Long) dto.getEntidad());
                    result = stock;
                    break;
                case SOLICITAR_CAJA_ABIERTA:
                    result = propagacionService.cajaAbiertaPorUsuario((Long) dto.getEntidad());
                    break;
                case SOLICITAR_MALETIN:
                    result = propagacionService.maletinPorDescripcion((String) dto.getEntidad());
                    break;
                case FINALIZAR_INVENTARIO:
                    result = propagacionService.finalizarInventario(dto);
                    break;
                case SOLICITAR_ENTIDAD:
                case GUARDAR:
                case DELETE:
                    result = propagacionService.crudEntidad(dto);
                    break;
                default:
                    result = null;
                    break;
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
