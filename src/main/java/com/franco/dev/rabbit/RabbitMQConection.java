package com.franco.dev.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.ConnectException;

@Component
public class RabbitMQConection {

    @Autowired
    private Environment env;

    public static final String NOME_EXCHANGE = "amq.topic";
    public static final String NOME_EXCHANGE_DIRECT = "amq.direct";
    public static final String FILIAL_KEY = "filial";
    public static final String SERVIDOR_KEY = "servidor";
    private AmqpAdmin amqpAdmin;

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
