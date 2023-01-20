package com.franco.dev.rabbit.sender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.rabbit.dto.RabbitmqMsg;
import com.franco.dev.rabbit.enums.TipoAccion;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.configuracion.RabbitmqMsgService;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service
public class Sender<T> {

    private static final Logger log = Logger.getLogger(String.valueOf(Sender.class));

    @Autowired
    private RabbitTemplate template;

    @Autowired
    private RabbitmqMsgService rabbitmqMsgService;

//    public void send(RabbitDto<T> p, String key) {
//        template.convertAndSend(MessagingConfig.EXCHANGE, key, p);
//    }
    @Async
    public void enviar(String key, RabbitDto<T> p) {
            try {
                List<RabbitmqMsg> rabbitmqMsgList = rabbitmqMsgService.findAll();
                if (rabbitmqMsgList.size() > 0) {
                    for (RabbitmqMsg r : rabbitmqMsgList) {
                        try {
                            RabbitDto dto = new RabbitDto();
                            if (r.getRecibidoEnFilial() != null) dto.setRecibidoEnFilial(r.getRecibidoEnFilial());
                            if (r.getData() != null) dto.setData(r.getData());
                            if (r.getIdSucursalOrigen() != null) dto.setIdSucursalOrigen(r.getIdSucursalOrigen());
                            if (r.getRecibidoEnServidor() != null) dto.setRecibidoEnServidor(r.getRecibidoEnServidor());
                            if (r.getTipoAccion() != null) dto.setTipoAccion(TipoAccion.valueOf(r.getTipoAccion()));
                            if (r.getTipoEntidad() != null) dto.setTipoEntidad(TipoEntidad.valueOf(r.getTipoEntidad()));
                            //deserializar entidad
                            ObjectMapper mapper = new ObjectMapper()
                                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                    .registerModule(new JavaTimeModule())
                                    .registerModule(new ParameterNamesModule());
                            Object newEntidad = mapper.readValue(r.getEntidad(), r.getClassType());
                            dto.setEntidad(newEntidad);
                            //deserializar data
                            Object newData = mapper.readValue(r.getEntidad(), Object.class);
                            dto.setData(newData);
                            log.info("Propagando entidad con delay: " + dto.getTipoEntidad());
                            template.convertAndSend(r.getExchange(), r.getKey(), dto);
                            rabbitmqMsgService.deleteById(r.getId());
                        } catch (Exception ex1) {
                            ex1.printStackTrace();
                        }
                    }
                }
                template.convertAndSend(RabbitMQConection.NOME_EXCHANGE, key, p);
            } catch (AmqpException e) {
                log.info("La entidad no pudo ser propagada: " + p.getTipoEntidad().toString());
                RabbitmqMsg rabbitmqMsg = new RabbitmqMsg();
                rabbitmqMsg.setExchange(RabbitMQConection.NOME_EXCHANGE);
                rabbitmqMsg.setKey(key);
                rabbitmqMsg.setClassType(p.getEntidad().getClass());
                ObjectMapper mapper = new ObjectMapper()
                        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .registerModule(new JavaTimeModule())
                        .registerModule(new ParameterNamesModule());
                if (p.getTipoEntidad() != null) rabbitmqMsg.setTipoEntidad(p.getTipoEntidad().toString());
                if (p.getTipoAccion() != null) rabbitmqMsg.setTipoAccion(p.getTipoAccion().toString());
                if (p.getEntidad() != null) {
                    try {
                        String jsonResult = mapper.writeValueAsString(p.getEntidad());
                        rabbitmqMsg.setEntidad(jsonResult);
                    } catch (JsonProcessingException jsonProcessingException) {
                        jsonProcessingException.printStackTrace();
                    }
                }
                if (p.getData() != null) {
                    try {
                        String jsonResult = mapper.writeValueAsString(p.getEntidad());
                        System.out.println(jsonResult);
                        rabbitmqMsg.setEntidad(jsonResult);
                    } catch (JsonProcessingException jsonProcessingException) {
                        jsonProcessingException.printStackTrace();
                    }
                }
                if (p.getRecibidoEnFilial() != null) rabbitmqMsg.setRecibidoEnFilial(p.getRecibidoEnFilial());
                if (p.getRecibidoEnServidor() != null) rabbitmqMsg.setRecibidoEnServidor(p.getRecibidoEnServidor());
                if (p.getIdSucursalOrigen() != null) rabbitmqMsg.setIdSucursalOrigen(p.getIdSucursalOrigen());
                log.info("Guardando en base de datos");
                rabbitmqMsgService.save(rabbitmqMsg);
            }
    }

    public Object enviarAndRecibir(String key, RabbitDto<T> p) {
//        return template.convertSendAndReceive(RabbitMQConection.NOME_EXCHANGE_DIRECT, key + ".reply.to", p);
        return null;
    }

}
