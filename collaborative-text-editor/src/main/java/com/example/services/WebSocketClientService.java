package com.example.services;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import java.util.concurrent.ExecutionException;

public class WebSocketClientService {
    @SuppressWarnings("removal")
    public StompSession createSession() throws InterruptedException, ExecutionException {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient.connect("ws://localhost:8081/ws", new StompSessionHandlerAdapter() {}).get();
    }
}