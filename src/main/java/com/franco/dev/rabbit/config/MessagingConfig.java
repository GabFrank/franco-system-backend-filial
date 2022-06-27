package com.franco.dev.rabbit.config;

//import com.franco.dev.rabbit.receiver.Receiver;
import com.franco.dev.rabbit.receiver.Receiver;
import com.franco.dev.rabbit.sender.Sender;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

//    public static final String EXCHANGE = "frc-system";
//
//
//    @Bean
//    public Queue queueProducto() {
//        return new Queue("filial.1");
//    }
//
//    @Bean
//    public DirectExchange directExchange() {
//        return new DirectExchange(EXCHANGE);
//    }
//
//    @Bean
//    public Binding bindingProducto(DirectExchange directExchange,
//                                   Queue queueProducto) {
//        return BindingBuilder.bind(queueProducto)
//                .to(directExchange)
//                .with("filial.1");
//    }
//
//    @Bean
//    public Receiver receiver() {
//        return new Receiver();
//    }
//
//    @Bean
//    public Sender sender() {
//        return new Sender();
//    }
//
//    @Bean
//    public MessageConverter converter(){
//        return new Jackson2JsonMessageConverter();
//    }
//
//    @Bean
//    public AmqpTemplate template(ConnectionFactory connectionFactory){
//        final RabbitTemplate rabbitTemplate = new RabbitTemplate((connectionFactory));
//        rabbitTemplate.setMessageConverter(converter());
//        return rabbitTemplate;
//    }
}
