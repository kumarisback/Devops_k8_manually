package com.example.orderservice.controller;

import com.example.orderservice.events.OrderEventPublisher;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerCreateTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private OrderEventPublisher publisher;

    @InjectMocks
    private OrderController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void aValidOrderIsSavedAndAnnounced() throws Exception {
        Order saved = new Order("o-1", "u-1", "Kafka book", 42.5, "CREATED");
        when(repository.save(any(Order.class))).thenReturn(saved);

        mvc.perform(post("/").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","item":"Kafka book","amount":42.5}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("o-1"))
                .andExpect(jsonPath("$.status").value("CREATED"));

        verify(publisher).orderCreated(saved);
    }

    @Test
    void anInvalidOrderIsRejectedAndNothingHappens() throws Exception {
        mvc.perform(post("/").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"","item":"Kafka book","amount":-1}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(repository, publisher);
    }
}
