package com.rbd.customerservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class provides the configuration for RabbitMQ.
 * It sets up the necessary queues, exchanges, and bindings to enable messaging
 * between different services in the application.
 * The @Configuration annotation tells Spring that this is a configuration class
 * that contains bean definitions.
 */
@Configuration
public class RabbitMQConfig {

    // A constant for the name of the queue where customer-related messages will be sent.
    public static final String CUSTOMER_QUEUE = "customer.queue";

    // A constant for the name of the exchange that will route messages to the customer queue.
    public static final String CUSTOMER_EXCHANGE = "customer.exchange";

    // A constant for the routing key used to bind the queue to the exchange.
    public static final String CUSTOMER_ROUTING_KEY = "customer.routingKey";

    /**
     * Defines the primary queue for customer messages.
     * A queue is a buffer that stores messages.
     * The @Bean annotation tells Spring to manage this Queue object as a bean in the application context.
     *
     * @return A new Queue instance.
     */
    @Bean
    Queue queue() {
        // new Queue(name, durable)
        // 'durable' is set to true, which means the queue will survive a broker restart.
        // Messages in a durable queue will also survive a restart if they are marked as persistent.
        return new Queue(CUSTOMER_QUEUE, true);
    }

    /**
     * Defines the exchange that will route messages to our queue.
     * An exchange receives messages from producers and pushes them to queues based on rules
     * defined by the exchange type and routing keys.
     *
     * @return A new DirectExchange instance.
     */
    @Bean
    DirectExchange exchange() {
        // A DirectExchange delivers messages to queues based on the message routing key.
        // The routing key is an attribute of the message. The exchange will send the message
        // to the queue whose binding key exactly matches the message's routing key.
        return new DirectExchange(CUSTOMER_EXCHANGE);
    }

    /**
     * Binds the queue to the exchange using a routing key.
     * A binding is a relationship between an exchange and a queue. It tells the exchange
     * which queues to route messages to.
     *
     * @param queue    The queue bean to be bound.
     * @param exchange The exchange bean to bind the queue to.
     * @return A new Binding instance.
     */
    @Bean
    Binding binding(Queue queue, DirectExchange exchange) {
        // This creates a binding between the 'customer.queue' and the 'customer.exchange'.
        // The 'with(CUSTOMER_ROUTING_KEY)' part specifies that the exchange should send messages
        // to this queue only if the message's routing key matches 'customer.routingKey'.
        return BindingBuilder.bind(queue).to(exchange).with(CUSTOMER_ROUTING_KEY);
    }

    /**
     * Defines a message converter to serialize and deserialize messages to and from JSON.
     * By default, RabbitTemplate uses Java serialization. Using a JSON converter is often
     * preferred for interoperability between different services and languages.
     *
     * @return A new Jackson2JsonMessageConverter instance.
     */
    @Bean
    public Jackson2JsonMessageConverter converter() {
        // This converter uses the Jackson 2 library to convert Java objects to a JSON payload,
        // and vice-versa.
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configures the RabbitTemplate, which is the central component for sending and receiving messages.
     * This template simplifies the process of interacting with RabbitMQ.
     *
     * @param connectionFactory The auto-configured RabbitMQ connection factory.
     * @return A configured RabbitTemplate instance.
     */
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
}
