package com.example.chatApp.config;

import com.example.chatApp.server.JwtServerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Uses your existing JWT server client — no local JwtUtil needed
    private final JwtServerClient jwtServerClient;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enables an in-memory message broker.
        // /topic is typically for broadcast (one-to-many), /queue is for private chats (one-to-one)
        registry.enableSimpleBroker("/topic", "/queue");
        // Messages sent from client targeting the server (e.g., @MessageMapping) must start with /app
        registry.setApplicationDestinationPrefixes("/app");
        // Sets the prefix used to target specific users (essential for private messaging via SimMessagingTemplate)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Defines the URL path ("/ws") where clients will initiate the initial HTTP handshake to upgrade to WebSockets
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Allows cross-origin requests (CORS) from any domain (good for development)
                .withSockJS(); // Enables SockJS fallback options if the browser doesn't natively support WebSockets
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Intercepts messages arriving from clients *before* they reach the application controllers
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                // Extracts STOMP-specific metadata (like headers and commands) out of the generic Spring Message wrapper
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Pulls the custom "jwt" header sent by the frontend client
                    String token = accessor.getFirstNativeHeader("jwt");
                    if (token != null && !token.isBlank()) {
                        try {
                            // Calls your external microservice/auth-server to check if the token is valid
                            JwtServerClient.ValidateResult result = jwtServerClient.validateToken(token);
                            // If the token checks out, and we got a valid user email back...
                            if (result.valid() && result.email() != null) {
                                // Ensures the role string is properly prefixed with "ROLE_" as required by Spring Security
                                String role = result.role().startsWith("ROLE_") ? result.role() : "ROLE_" + result.role();
                                // Creates an authentication object containing the user's principal (email) and their granted authorities
                                var auth = new UsernamePasswordAuthenticationToken(
                                        result.email(), null,
                                        List.of(new SimpleGrantedAuthority(role))
                                );
                                // Attaches the authenticated user to this WebSocket session context
                                // This allows you to use @AuthenticationPrincipal or Principal in your controllers later
                                accessor.setUser(auth);
                                log.info("WebSocket connected: {}", result.email());
                            }
                        } catch (Exception e) {
                            // Catches network errors or validation exceptions so the whole app doesn't crash on a bad token
                            log.warn("WebSocket JWT validation failed: {}", e.getMessage());
                        }
                    }
                }
                // Allows the message to continue down the pipeline (whether authenticated or not)
                return message;
            }
        });
    }
}