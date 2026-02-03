package com.rbd.customerservice.service.impl;



import com.rbd.clients.fraud.FraudClient;
import com.rbd.clients.notification.NotificationClient;
import com.rbd.clients.notification.NotificationRequest;
import com.rbd.customerservice.config.RabbitMQConfig;
import com.rbd.customerservice.dto.CustomerDTO;
import com.rbd.customerservice.entity.Customer;
import com.rbd.customerservice.exception.CustomerNotFoundException;
import com.rbd.customerservice.repository.CustomerRepository;
import com.rbd.customerservice.service.CustomerService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
//    private final RestTemplate  restTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final FraudClient fraudClient;
    private final NotificationClient notificationClient;

    // Constructor Injection
    public CustomerServiceImpl(CustomerRepository customerRepository, RabbitTemplate rabbitTemplate, FraudClient fraudClient, NotificationClient notificationClient) {
        this.customerRepository = customerRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.fraudClient = fraudClient;
        this.notificationClient = notificationClient;
    }

    @Override
    @Transactional
    public CustomerDTO createCustomer(CustomerDTO customerDTO) {

        // 1. DTO → Entity
        Customer customer = mapToEntity(customerDTO);

        // 2. Save customer (ID needed)
        Customer savedCustomer = customerRepository.save(customer);

        // 3. Call fraud-service
//        Boolean isFraud = restTemplate.getForObject(
//                "http://FRAUD-SERVICE/api/fraud/" + savedCustomer.getId(),
//                Boolean.class
//        );

        Boolean isFraud = (Boolean) rabbitTemplate.convertSendAndReceive(RabbitMQConfig.FRAUD_EXCHANGE,RabbitMQConfig.FRAUD_ROURTING_KEY,savedCustomer.getId());

        //Boolean isFraud = fraudClient.isFraudster(savedCustomer.getId());


        // 4. If fraud → rollback
        if (Boolean.TRUE.equals(isFraud)) {
            throw new IllegalStateException("Customer is fraud");
        }
        NotificationRequest notificationRequest = new NotificationRequest(savedCustomer.getId(),savedCustomer.getEmail(),"rbd.com","Welcome "+savedCustomer.getLastName() +" enjow our services nice to having you here") ;
        // 5 send new customer to notification service using RabbitMQ
        rabbitTemplate.convertAndSend(RabbitMQConfig.CUSTOMER_EXCHANGE, RabbitMQConfig.CUSTOMER_ROUTING_KEY, notificationRequest);

        //6 send new notifications to notification service using Feign
        // notificationClient.sendNotification(notificationRequest);


        // 5. Return DTO
        return mapToDTO(savedCustomer);
    }


    @Override
    public CustomerDTO getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + id));
        return mapToDTO(customer);
    }

    @Override
    public List<CustomerDTO> getAllCustomers() {
        List<Customer> customers = customerRepository.findAll();
        return customers.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CustomerDTO updateCustomer(Long id, CustomerDTO customerDTO) {
        Customer existingCustomer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + id));

        // Update fields
        existingCustomer.setFirstName(customerDTO.getFirstName());
        existingCustomer.setLastName(customerDTO.getLastName());
        existingCustomer.setEmail(customerDTO.getEmail());

        Customer updatedCustomer = customerRepository.save(existingCustomer);
        return mapToDTO(updatedCustomer);
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new CustomerNotFoundException("Cannot delete. Customer not found with id: " + id);
        }
        customerRepository.deleteById(id);
    }

    // --- Helper Mappers (Could be moved to a separate Mapper class) ---

    private CustomerDTO mapToDTO(Customer customer) {
        return CustomerDTO.builder()
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .build();
    }

    private Customer mapToEntity(CustomerDTO dto) {
        return Customer.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .build();
    }
}