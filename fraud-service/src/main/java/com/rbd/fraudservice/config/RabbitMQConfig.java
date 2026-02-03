package com.rbd.fraudservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {
    public static final String FRAUD_QUEUE = "fraud.queue";
    public static final String FRAUD_EXCHANGE  = "fraud.exchange";
    public static final String FRAUD_ROURTING_KEY = "fraud.routingKey";


    @Bean
    Queue fraudQueue() {
        // new Queue(name, durable)
        // 'durable' is set to true, which means the queue will survive a broker restart.
        // Messages in a durable queue will also survive a restart if they are marked as persistent.
        return new Queue(FRAUD_QUEUE, true);
    }
    @Bean
    DirectExchange fraudExchange()
    {
        return new DirectExchange(FRAUD_EXCHANGE);
    }
    @Bean
    Binding fraud() {
        return BindingBuilder.bind(fraudQueue()).to(fraudExchange()).with(FRAUD_ROURTING_KEY);
    }
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        // Create a new RabbitTemplate with the given connection factory.
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // Set the message converter for the template to the JSON converter defined above.
        // This ensures that any objects sent using this template will be automatically
        // converted to JSON.
        template.setMessageConverter(converter());
        return template;
    }

    @Bean
    public Jackson2JsonMessageConverter converter() {
        // This converter uses the Jackson 2 library to convert Java objects to a JSON payload,
        // and vice-versa.
        return new Jackson2JsonMessageConverter();
    }
}
