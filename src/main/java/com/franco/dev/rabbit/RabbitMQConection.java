package com.franco.dev.rabbit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.rabbit.dto.RabbitmqMsg;
import com.franco.dev.rabbit.enums.TipoAccion;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.configuracion.RabbitmqMsgService;
import com.rabbitmq.client.ShutdownSignalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.ConnectException;
import java.util.Collections;
import java.util.List;

@Component
public class RabbitMQConection {

    @Autowired
    private Environment env;

    private final Logger logger = LoggerFactory.getLogger(RabbitMQConection.class);

    public static final String NOME_EXCHANGE = "amq.topic";
    public static final String NOME_EXCHANGE_DIRECT = "amq.direct";
    public static final String FILIAL_KEY = "filial";
    public static final String SERVIDOR_KEY = "servidor";
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate template;

    @Autowired
    private RabbitmqMsgService rabbitmqMsgService;

    @Autowired
    private CachingConnectionFactory cachingConnectionFactory;

    public RabbitMQConection(AmqpAdmin amqpAdmin){
        this.amqpAdmin = amqpAdmin;
    }

    private Queue fila(String name){
        return new Queue(name, true, false, false);
    }

    private TopicExchange topicExchange(){
        return new TopicExchange(NOME_EXCHANGE);
    }

    private DirectExchange directExchange() { return  new DirectExchange(NOME_EXCHANGE_DIRECT); }

    private Binding binding(Queue fila, TopicExchange exchange, String key){
        return new Binding(fila.getName(), Binding.DestinationType.QUEUE, exchange.getName(), key, null);
    }

    private Binding bindingDirect(Queue fila, DirectExchange exchange, String key){
        return new Binding(fila.getName(), Binding.DestinationType.QUEUE, exchange.getName(), key, null);
    }

    @PostConstruct
    private void add(){
        Queue filaProducto = this.fila(FILIAL_KEY+"."+env.getProperty("sucursalId"));
        Queue filaProductoReplyTo = this.fila(FILIAL_KEY+"."+env.getProperty("sucursalId")+".reply.to");
        TopicExchange exchange = this.topicExchange();
        DirectExchange exchangeDirect = this.directExchange();
        Binding binding = this.binding(filaProducto, exchange, FILIAL_KEY);
        Binding binding2 = this.binding(filaProducto, exchange, filaProducto.getName());
        Binding binding3 = this.bindingDirect(filaProductoReplyTo, exchangeDirect, filaProductoReplyTo.getName());
        ConnectionListener connectionListener = new ConnectionListener() {
            @Override
            public void onCreate(Connection connection) {
                logger.info("la conexcion con rabbit fue establecida");

            }

            @Override
            public void onClose(Connection connection) {
                logger.info("la conexcion con rabbit fue perdida");
            }

            @Override
            public void onShutDown(ShutdownSignalException signal) {
                logger.info("la conexcion con rabbit fue interrimpida");
            }
        };

        cachingConnectionFactory.addConnectionListener(connectionListener);
        cachingConnectionFactory.getRabbitConnectionFactory().setRequestedHeartbeat(1);
        try {
            this.amqpAdmin.declareQueue(filaProducto);
            this.amqpAdmin.declareQueue(filaProductoReplyTo);
            this.amqpAdmin.declareExchange(exchange);
            this.amqpAdmin.declareBinding(binding);
            this.amqpAdmin.declareBinding(binding2);
            this.amqpAdmin.declareBinding(binding3);
        } catch (Exception e){
            e.printStackTrace();
        }

    }
}
