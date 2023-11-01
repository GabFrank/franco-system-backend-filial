package com.franco.dev.rabbit;

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
import org.springframework.boot.autoconfigure.amqp.ConnectionFactoryContextWrapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RabbitMQConection {

    public static final String NOME_EXCHANGE = "amq.topic";
    public static final String NOME_EXCHANGE_DIRECT = "amq.direct";
    public static final String FILIAL_KEY = "filial";
    public static final String SERVIDOR_KEY = "servidor";
    private final Logger logger = LoggerFactory.getLogger(RabbitMQConection.class);
    @Autowired
    private Environment env;
    private AmqpAdmin amqpAdmin;

    @Autowired
    private CachingConnectionFactory cachingConnectionFactory;

    @Autowired
    private ConnectionFactoryContextWrapper contextWrapper;

    public RabbitMQConection(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    private Queue fila(String name) {
        return new Queue(name, true, false, false);
    }

    private TopicExchange topicExchange() {
        return new TopicExchange(NOME_EXCHANGE);
    }

    private DirectExchange directExchange() {
        return new DirectExchange(NOME_EXCHANGE_DIRECT);
    }

    private Binding binding(Queue fila, TopicExchange exchange, String key) {
        return new Binding(fila.getName(), Binding.DestinationType.QUEUE, exchange.getName(), key, null);
    }

    private Binding bindingDirect(Queue fila, DirectExchange exchange, String key) {
        return new Binding(fila.getName(), Binding.DestinationType.QUEUE, exchange.getName(), key, null);
    }

    @PostConstruct
    private void add() {
        Queue filaProducto = this.fila(FILIAL_KEY + "." + env.getProperty("sucursalId"));
        Queue filaProductoReplyTo = this.fila(FILIAL_KEY + "." + env.getProperty("sucursalId") + ".reply.to");
        Queue servidorQueue = this.fila("servidor");
        Queue servidorReplyToQueue = this.fila("servidor.reply.to");
        TopicExchange exchange = this.topicExchange();
        DirectExchange exchangeDirect = this.directExchange();
        Binding binding = this.binding(filaProducto, exchange, FILIAL_KEY);
        Binding binding2 = this.bindingDirect(filaProducto, exchangeDirect, filaProducto.getName());
        Binding binding3 = this.bindingDirect(filaProductoReplyTo, exchangeDirect, filaProductoReplyTo.getName());
        Binding binding4 = this.binding(servidorQueue, exchange, servidorQueue.getName());
        Binding binding6 = this.binding(servidorReplyToQueue, exchange, servidorReplyToQueue.getName());
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
            this.amqpAdmin.declareExchange(exchange);
            contextWrapper.run("servidor",
                    () -> {
                        this.amqpAdmin.declareQueue(filaProductoReplyTo);
                        this.amqpAdmin.declareBinding(binding3);
                        this.amqpAdmin.declareQueue(servidorReplyToQueue);
                        this.amqpAdmin.declareBinding(binding6);
                    });
            this.amqpAdmin.declareQueue(filaProducto);
            this.amqpAdmin.declareQueue(servidorQueue);
            this.amqpAdmin.declareBinding(binding);
            this.amqpAdmin.declareBinding(binding2);
            this.amqpAdmin.declareBinding(binding4);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}


