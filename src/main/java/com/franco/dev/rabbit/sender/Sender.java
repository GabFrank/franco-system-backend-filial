package com.franco.dev.rabbit.sender;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.rabbit.dto.RabbitmqMsg;
import com.franco.dev.rabbit.enums.TipoAccion;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.ServiceFinder;
import com.franco.dev.service.configuracion.RabbitmqMsgService;
import com.franco.dev.utilitarios.CustomSerializerModifier;
import com.franco.dev.utilitarios.JsonIdView;
import com.franco.dev.utilitarios.LocalDateTimeDeserializer;
import com.franco.dev.utilitarios.LocalDateTimeSerializer;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.SimpleResourceHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@Service
public class Sender<T> {

    private static final Logger log = Logger.getLogger(String.valueOf(Sender.class));

    @Autowired
    private RabbitTemplate template;

    @Autowired
    private RabbitmqMsgService rabbitmqMsgService;

    @Autowired
    private ServiceFinder serviceFinder;


    //    public void send(RabbitDto<T> p, String key) {
//        template.convertAndSend(MessagingConfig.EXCHANGE, key, p);
//    }
    @Async
    @Transactional
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
                        if (r.getEntidad() != null) {
                            ObjectMapper mapper = new ObjectMapper()
                                    .registerModule(new SimpleModule()
                                            .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer()));
                            String json = mapper.writeValueAsString(r.getEntidad());
                            Object entity = mapper.readValue(json, r.getClassType());
                            dto.setEntidad(entity);
                        }
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
            if (p.getTipoEntidad() != null) rabbitmqMsg.setTipoEntidad(p.getTipoEntidad().toString());
            if (p.getTipoAccion() != null) rabbitmqMsg.setTipoAccion(p.getTipoAccion().toString());
            if (p.getEntidad() != null) {
                ObjectMapper mapper = new ObjectMapper()
                        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
                mapper.setSerializerFactory(mapper.getSerializerFactory().withSerializerModifier(new CustomSerializerModifier()));
                mapper.registerModule(new SimpleModule().addSerializer(LocalDateTime.class, new LocalDateTimeSerializer()));

                try {
                    String json = mapper.writerWithView(JsonIdView.Id.class).writeValueAsString(p.getEntidad());
                    rabbitmqMsg.setEntidad(json);
                } catch (JsonProcessingException jsonProcessingException) {
                    jsonProcessingException.printStackTrace();
                }
                if (p.getRecibidoEnFilial() != null) rabbitmqMsg.setRecibidoEnFilial(p.getRecibidoEnFilial());
                if (p.getRecibidoEnServidor() != null) rabbitmqMsg.setRecibidoEnServidor(p.getRecibidoEnServidor());
                if (p.getIdSucursalOrigen() != null) rabbitmqMsg.setIdSucursalOrigen(p.getIdSucursalOrigen());
                log.info("Guardando en base de datos");
                rabbitmqMsgService.save(rabbitmqMsg);
            } else {
                log.info("No hay entidad para guardar");
            }

        }
    }

    public Object enviarAndRecibir(String key, RabbitDto<T> p) {
        SimpleResourceHolder.bind(template.getConnectionFactory(), "servidor");
        try {
            log.info("Enviando a servidor");
            template.setReplyTimeout(10000);
            return template.convertSendAndReceive(RabbitMQConection.NOME_EXCHANGE_DIRECT, key + ".reply.to", p);
        } catch (Exception e){
            return null;
        }
        finally {
            SimpleResourceHolder.unbind(template.getConnectionFactory());
        }
    }

}
