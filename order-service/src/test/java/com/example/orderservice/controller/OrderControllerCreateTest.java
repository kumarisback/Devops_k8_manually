package com.example.orderservice.controller;

import com.example.orderservice.events.OrderEventPublisher;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.security.SecurityConfig;
import com.example.orderservice.web.HttpLoggingConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private OrderEventPublisher publisher;

    @Test
    void aSignedInUserCreatesAnOrderForThemselves() throws Exception {
        when(repository.save(any(Order.class))).thenAnswer(call -> {
            Order order = call.getArgument(0);
            order.setId("o-1");
            return order;
        });

        mvc.perform(post("/").with(jwt().jwt(token -> token.subject("asha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item":"Kafka book","amount":42.5,"userId":"someone-else"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("o-1"))
                .andExpect(jsonPath("$.userId").value("asha"))
                .andExpect(jsonPath("$.status").value("CREATED"));

        ArgumentCaptor<Order> announced = ArgumentCaptor.forClass(Order.class);
        verify(publisher).orderCreated(announced.capture());
        assertThat(announced.getValue().getUserId()).isEqualTo("asha");
    }

    @Test
    void withoutATokenNothingHappens() throws Exception {
        mvc.perform(post("/").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item":"Kafka book","amount":42.5}"""))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repository, publisher);
    }

    @Test
    void anInvalidOrderIsRejected() throws Exception {
        mvc.perform(post("/").with(jwt().jwt(token -> token.subject("asha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item":"Kafka book\\nYour account is locked","amount":-1}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(repository, publisher);
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
