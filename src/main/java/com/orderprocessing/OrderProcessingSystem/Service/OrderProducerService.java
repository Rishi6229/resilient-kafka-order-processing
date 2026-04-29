package com.orderprocessing.OrderProcessingSystem.Service;


import com.orderprocessing.OrderProcessingSystem.Model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderProducerService {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;
    private Set<String> processedOrders = ConcurrentHashMap.newKeySet();
    public void sendOrder(Order event) {
        try {
            String message = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(
                    "orders",
                    event.getUserId(),
                    message
            ).whenComplete((result, ex) -> {
                if (ex != null) {
                    System.out.println("Failed to send: " + ex.getMessage());
                } else {
                    System.out.println("Sent | partition="
                            + result.getRecordMetadata().partition()
                            + " offset="
                            + result.getRecordMetadata().offset());
                }
            });

        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
    private void processOrder(Order order) {

        if (processedOrders.contains(order.getOrderId())) {
            System.out.println("Duplicate skipped: " + order.getOrderId());
            return;
        }
        if (Math.random() < 0.5) {
            throw new RuntimeException("Random failure");
        }
        processedOrders.add(order.getOrderId());

        System.out.println("Order processed: " + order.getOrderId());
    }

    @KafkaListener(topics = "orders", groupId = "order-group")
    public void consume(String message, Acknowledgment ack) throws Exception {
        Order order = objectMapper.readValue(message, Order.class);

        try {
            processOrder(order);
            ack.acknowledge();

        } catch (Exception e) {
            order.setRetryCount(1);
            kafkaTemplate.send("orders-retry-1",
                    order.getUserId(), message);
        }
    }
    @KafkaListener(topics="orders-retry-1" , groupId = "order-group")
    public void retryLevelOneConsumer(String message , Acknowledgment ack){
        Order order = objectMapper.readValue(message , Order.class);
        try{
            processOrder(order);
            ack.acknowledge();
        }catch (Exception e){
            order.setRetryCount(2);
            kafkaTemplate.send("orderes-retry-2" , order.getUserId() ,
                    objectMapper.writeValueAsString(order));
            System.out.println("send to be retried for second time");
            ack.acknowledge();
        }
    }

    @KafkaListener(topics="orders-retry-2" , groupId = "order-group")
    public void retryLevelTwoConsumer(String message , Acknowledgment ack){
        Order order = objectMapper.readValue(message , Order.class);
        try{
            processOrder(order);
            ack.acknowledge();
        }catch (Exception e){
            order.setRetryCount(3);
            kafkaTemplate.send("orderes-dlq" , order.getUserId() ,
                    objectMapper.writeValueAsString(order));
            System.out.println("order sent to dlq");
            ack.acknowledge();
        }
    }


}
