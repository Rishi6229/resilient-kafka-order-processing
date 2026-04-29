package com.orderprocessing.OrderProcessingSystem.Controller;

import com.orderprocessing.OrderProcessingSystem.Model.Order;
import com.orderprocessing.OrderProcessingSystem.Service.OrderProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderProducerService orderProducerService;

    @PostMapping
    public String createOrder(@RequestBody Order order){
        orderProducerService.sendOrder(order);
        return "order sent to kafka";
    }
}
