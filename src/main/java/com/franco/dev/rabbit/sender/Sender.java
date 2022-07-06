package com.franco.dev.rabbit.sender;

import com.franco.dev.rabbit.RabbitEntity;
import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.config.MessagingConfig;
import com.franco.dev.rabbit.dto.RabbitDto;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Sender<T> {

    @Autowired
    private RabbitTemplate template;

//    public void send(RabbitDto<T> p, String key) {
//        template.convertAndSend(MessagingConfig.EXCHANGE, key, p);
//    }

    public void enviar(String key, RabbitDto<T> p){
        template.convertAndSend(RabbitMQConection.NOME_EXCHANGE, key, p);
    }

    public Object enviarAndRecibir(String key, RabbitDto<T> p){
        return template.convertSendAndReceive(RabbitMQConection.NOME_EXCHANGE_DIRECT, key, p);
    }

}
