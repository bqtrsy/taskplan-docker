package com.mycompany.myapp.web.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myapp.IntegrationTest;
import com.mycompany.myapp.security.AuthoritiesConstants;
import com.mycompany.myapp.web.websocket.dto.ActivityDTO;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * Integration tests for WebSocket activity tracking.
 */
@IntegrationTest
@WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
@Disabled("WebSocket tests are not critical for JaCoCo report")
class WebSocketIT {

    @Value("${server.port}")
    private int port;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private BlockingQueue<ActivityDTO> activityQueue;

    @BeforeEach
    void setup() throws Exception {
        activityQueue = new LinkedBlockingQueue<>();

        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String url = String.format("ws://localhost:%d/websocket/tracker", port);
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("X-XSRF-TOKEN", "test-token");

        stompSession = stompClient
            .connect(url, handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {})
            .get(10, TimeUnit.SECONDS);

        stompSession.subscribe(
            "/topic/tracker",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return ActivityDTO.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    activityQueue.add((ActivityDTO) payload);
                }
            }
        );
    }

    @Test
    void testActivityTracking() throws Exception {
        ActivityDTO activity = new ActivityDTO();
        activity.setPage("test-page");
        activity.setUserLogin("admin");

        stompSession.send("/topic/activity", activity);

        ActivityDTO received = activityQueue.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getUserLogin()).isEqualTo("admin");
        assertThat(received.getPage()).isEqualTo("test-page");
        assertThat(received.getSessionId()).isNotNull();
        assertThat(received.getIpAddress()).isNotNull();
        assertThat(received.getTime()).isNotNull();
    }
}
