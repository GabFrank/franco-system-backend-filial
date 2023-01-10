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
import com.franco.dev.service.configuracion.RabbitmqMsgService;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Sender<T> {

    @Autowired
    private RabbitTemplate template;

    @Autowired
    private RabbitmqMsgService rabbitmqMsgService;

//    public void send(RabbitDto<T> p, String key) {
//        template.convertAndSend(MessagingConfig.EXCHANGE, key, p);
//    }

    public void enviar(String key, RabbitDto<T> p) {
        try {
            template.convertAndSend(RabbitMQConection.NOME_EXCHANGE, key, p);
        } catch (AmqpException e) {
            RabbitmqMsg rabbitmqMsg = new RabbitmqMsg();
            rabbitmqMsg.setExchange(RabbitMQConection.NOME_EXCHANGE);
            rabbitmqMsg.setKey(key);
            rabbitmqMsg.setClassType(p.getEntidad().getClass());
            ObjectMapper mapper = new ObjectMapper()
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            if (p.getTipoEntidad() != null) rabbitmqMsg.setTipoEntidad(p.getTipoEntidad().toString());
            if (p.getTipoAccion() != null) rabbitmqMsg.setTipoAccion(p.getTipoAccion().toString());
            if (p.getEntidad() != null) {
                try {
                    String jsonResult = mapper.writeValueAsString(p.getEntidad());
                    System.out.println(jsonResult);
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

            rabbitmqMsgService.save(rabbitmqMsg);
        }
    }

    public Object enviarAndRecibir(String key, RabbitDto<T> p) {
        return template.convertSendAndReceive(RabbitMQConection.NOME_EXCHANGE_DIRECT, key + ".reply.to", p);
    }

}
