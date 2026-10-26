package com.example.orderservice.controller;

import com.example.orderservice.model.Order;
import com.example.orderservice.outbox.OrderCreation;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.security.SecurityConfig;
import com.example.orderservice.web.HttpLoggingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The order API with its real security rules: who may create and read which orders. */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, HttpLoggingConfig.class})
class OrderControllerCreateTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderRepository repository;

    @MockitoBean
    private OrderCreation orderCreation;

    @Test
    void aSignedInUserCreatesAnOrderForThemselves() throws Exception {
        when(orderCreation.create("asha", "Kafka book", 42.5)).thenReturn(new Order("o-1", "asha", "Kafka book", 42.5, "CREATED"));

        mvc.perform(post("/").with(jwt().jwt(token -> token.subject("asha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item":"Kafka book","amount":42.5,"userId":"someone-else"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("o-1"))
                .andExpect(jsonPath("$.userId").value("asha"))
                .andExpect(jsonPath("$.status").value("CREATED"));

        // The user comes from the token; the body's userId is ignored.
        verify(orderCreation).create("asha", "Kafka book", 42.5);
    }

    @Test
    void withoutATokenNothingHappens() throws Exception {
        mvc.perform(post("/").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item":"Kafka book","amount":42.5}"""))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repository, orderCreation);
    }

    @Test
    void anInvalidOrderIsRejected() throws Exception {
        mvc.perform(post("/").with(jwt().jwt(token -> token.subject("asha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item":"Kafka book\\nYour account is locked","amount":-1}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(repository, orderCreation);
    }

    @Test
    void nobodyReadsSomeoneElsesOrders() throws Exception {
        mvc.perform(get("/user/ravi").with(jwt().jwt(token -> token.subject("asha"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(repository);
    }

    @Test
    void aUserReadsTheirOwnOrders() throws Exception {
        when(repository.findByUserId("asha")).thenReturn(List.of(new Order("o-1", "asha", "book", 1.0, "CREATED")));

        mvc.perform(get("/user/asha").with(jwt().jwt(token -> token.subject("asha"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1));
    }

    @Test
    void theStatusCheckIsOpen() throws Exception {
        mvc.perform(get("/status")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ONLINE"));
    }
}
