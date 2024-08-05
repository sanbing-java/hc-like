/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.adapter.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import sanbin.example.dylike.application.WebSocketMsgEndpoint;
import sanbin.example.dylike.application.WebSocketService;
import sanbin.example.dylike.application.WebSocketSessionRef;
import sanbin.example.dylike.application.WebsocketErrorCode;
import sanbin.example.dylike.util.ConcurrentQueueUtils;
import sanbin.example.dylike.util.DefaultCounter;
import sanbin.example.dylike.util.JacksonUtil;
import sanbin.example.dylike.util.StatsFactory;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static sanbin.example.dylike.application.DefaultWebSocketService.NUMBER_OF_PING_ATTEMPTS;


@Service
@Slf4j
@RequiredArgsConstructor
public class SanbinWebSocketHandler extends TextWebSocketHandler implements WebSocketMsgEndpoint {

    private final ConcurrentMap<String, SessionMetaData> internalSessionMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> externalSessionMap = new ConcurrentHashMap<>();

    @Resource
    @Lazy
    private WebSocketService webSocketService;

    @Resource
    private StatsFactory statsFactory;

    @Value("${server.ws.send_timeout:5000}")
    private long sendTimeout;
    @Value("${server.ws.ping_timeout:30000}")
    private long pingTimeout;
    @Value("${server.ws.max_queue_messages_per_session:3000}")
    private int wsMaxQueueMessagesPerSession;
    @Value("${server.ws.auth_timeout_ms:10000}")
    private int authTimeoutMs;

    private Cache<String, SessionMetaData> pendingSessions;

    private DefaultCounter websocketDownlink;

    @PostConstruct
    private void init() {
        pendingSessions = Caffeine.newBuilder()
                .expireAfterWrite(authTimeoutMs, TimeUnit.MILLISECONDS)
                .<String, SessionMetaData>removalListener((sessionId, sessionMd, removalCause) -> {
                    if (removalCause == RemovalCause.EXPIRED && sessionMd != null) {
                        try {
                            close(sessionMd.sessionRef, CloseStatus.POLICY_VIOLATION);
                        } catch (IOException e) {
                            log.warn("IO error", e);
                        }
                    }
                })
                .build();

        websocketDownlink = statsFactory.createDefaultCounter("websocket_downlink");
    }

    @PreDestroy
    private void stop() {
        internalSessionMap.clear();
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            SessionMetaData sessionMd = getSessionMd(session.getId());
            if (sessionMd == null) {
                log.info("[{}] Failed to find session", session.getId());
                session.close(CloseStatus.SERVER_ERROR.withReason("Session not found!"));
                return;
            }
            sessionMd.onMsg(message.getPayload());
        } catch (IOException e) {
            log.warn("IO error", e);
        }
    }

    void processMsg(SessionMetaData sessionMd, String msg) throws IOException {
        WebSocketSessionRef sessionRef = sessionMd.sessionRef;
        WsCommandsWrapper cmdsWrapper;
        try {
            cmdsWrapper = JacksonUtil.fromString(msg, WsCommandsWrapper.class);
        } catch (Exception e) {
            log.error("{} Failed to decode cmd: {}", sessionRef, e.getMessage(), e);
            if (sessionRef.getSecurityCtx() != null) {
                webSocketService.sendError(sessionRef, 1, WebsocketErrorCode.BAD_REQUEST, "Failed to parse the payload");
            } else {
                close(sessionRef, CloseStatus.BAD_DATA.withReason(e.getMessage()));
            }
            return;
        }

        if (sessionRef.getSecurityCtx() != null) {
            webSocketService.handleCommands(sessionRef, cmdsWrapper);
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        try {
            SessionMetaData sessionMd = getSessionMd(session.getId());
            if (sessionMd != null) {
                log.trace("{} Processing pong response {}", sessionMd.sessionRef, message.getPayload());
                sessionMd.processPongMessage(System.currentTimeMillis());
            } else {
                log.warn("[{}] Failed to find session", session.getId());
                session.close(CloseStatus.SERVER_ERROR.withReason("Session not found!"));
            }
        } catch (IOException e) {
            log.error("IO error", e);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        try {
            if (session instanceof NativeWebSocketSession) {
                Session nativeSession = ((NativeWebSocketSession) session).getNativeSession(Session.class);
                if (nativeSession != null) {
                    nativeSession.getAsyncRemote().setSendTimeout(sendTimeout);
                }
            }
            WebSocketSessionRef sessionRef = toRef(session);
            log.info("[{}][{}] Session opened from address: {}", sessionRef.getSessionId(), session.getId(), session.getRemoteAddress());
            establishSession(session, sessionRef, null);
        } catch (InvalidParameterException e) {
            log.warn("[{}] Failed to start session", session.getId(), e);
            session.close(CloseStatus.BAD_DATA.withReason(e.getMessage()));
        } catch (Exception e) {
            log.warn("[{}] Failed to start session", session.getId(), e);
            session.close(CloseStatus.SERVER_ERROR.withReason(e.getMessage()));
        }
    }

    private void establishSession(WebSocketSession session, WebSocketSessionRef sessionRef, SessionMetaData sessionMd) throws IOException {
        if (sessionRef.getSecurityCtx() != null) {
            if (sessionMd == null) {
                sessionMd = new SessionMetaData(session, sessionRef);
            }
            sessionMd.setMaxMsgQueueSize(wsMaxQueueMessagesPerSession);

            internalSessionMap.put(session.getId(), sessionMd);
            externalSessionMap.put(sessionRef.getSessionId(), session.getId());
            processInWebSocketService(sessionRef, SessionEvent.onEstablished());
            log.info("[{}][{}][{}] Session established from address: {}", sessionRef.getSecurityCtx().getName(),
                    sessionRef.getSessionId(), session.getId(), session.getRemoteAddress());
        } else {
            sessionMd = new SessionMetaData(session, sessionRef);
            pendingSessions.put(session.getId(), sessionMd);
            externalSessionMap.put(sessionRef.getSessionId(), session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable tError) throws Exception {
        super.handleTransportError(session, tError);
        SessionMetaData sessionMd = getSessionMd(session.getId());
        if (sessionMd != null) {
            processInWebSocketService(sessionMd.sessionRef, SessionEvent.onError(tError));
        } else {
            log.info("[{}] Failed to find session", session.getId());
        }
        log.info("[{}] Session transport error", session.getId(), tError);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        super.afterConnectionClosed(session, closeStatus);
        SessionMetaData sessionMd = internalSessionMap.remove(session.getId());
        if (sessionMd == null) {
            sessionMd = pendingSessions.asMap().remove(session.getId());
        }
        if (sessionMd != null) {
            externalSessionMap.remove(sessionMd.sessionRef.getSessionId());
            if (sessionMd.sessionRef.getSecurityCtx() != null) {
                processInWebSocketService(sessionMd.sessionRef, SessionEvent.onClosed());
            }
            log.info("{} Session is closed", sessionMd.sessionRef);
        } else {
            log.info("[{}] Session is closed", session.getId());
        }
    }

    private void processInWebSocketService(WebSocketSessionRef sessionRef, SessionEvent event) {
        if (sessionRef.getSecurityCtx() == null) {
            return;
        }
        try {
            webSocketService.handleSessionEvent(sessionRef, event);
        } catch (BeanCreationNotAllowedException e) {
            log.warn("{} Failed to close session due to possible shutdown state", sessionRef);
        }
    }

    private WebSocketSessionRef toRef(WebSocketSession session) {
        // todo 非核心业务，伪造个用户鉴权
        SecurityProperties.User securityCtx = new SecurityProperties.User();
        securityCtx.setName(RandomStringUtils.randomAlphabetic(8));
        return WebSocketSessionRef.builder()
                .sessionId(UUID.randomUUID().toString())
                .securityCtx(securityCtx)
                .localAddress(session.getLocalAddress())
                .remoteAddress(session.getRemoteAddress())
                .build();
    }

    private SessionMetaData getSessionMd(String internalSessionId) {
        SessionMetaData sessionMd = internalSessionMap.get(internalSessionId);
        if (sessionMd == null) {
            sessionMd = pendingSessions.getIfPresent(internalSessionId);
        }
        return sessionMd;
    }

    class SessionMetaData implements SendHandler {
        private final WebSocketSession session;
        private final RemoteEndpoint.Async asyncRemote;
        private final WebSocketSessionRef sessionRef;

        final AtomicBoolean isSending = new AtomicBoolean(false);
        private final ConcurrentLinkedQueue<SanbinWebSocketMsg<?>> outboundMsgQueue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger outboundMsgQueueSize = new AtomicInteger();
        @Setter
        private int maxMsgQueueSize = wsMaxQueueMessagesPerSession;

        private final Queue<String> inboundMsgQueue = new ConcurrentLinkedQueue<>();
        private final Lock inboundMsgQueueProcessorLock = new ReentrantLock();

        private volatile long lastActivityTime;

        SessionMetaData(WebSocketSession session, WebSocketSessionRef sessionRef) {
            super();
            this.session = session;
            Session nativeSession = ((NativeWebSocketSession) session).getNativeSession(Session.class);
            this.asyncRemote = nativeSession.getAsyncRemote();
            this.sessionRef = sessionRef;
            this.lastActivityTime = System.currentTimeMillis();
        }

        void sendPing(long currentTime) {
            try {
                long timeSinceLastActivity = currentTime - lastActivityTime;
                if (timeSinceLastActivity >= pingTimeout) {
                    log.warn("{} Closing session due to ping timeout", sessionRef);
                    closeSession(CloseStatus.SESSION_NOT_RELIABLE);
                } else if (timeSinceLastActivity >= pingTimeout / NUMBER_OF_PING_ATTEMPTS) {
                    sendMsg(SanbinWebSocketPingMsg.INSTANCE);
                }
            } catch (Exception e) {
                log.info("{} Failed to send ping msg", sessionRef, e);
                closeSession(CloseStatus.SESSION_NOT_RELIABLE);
            }
        }

        void closeSession(CloseStatus reason) {
            try {
                close(this.sessionRef, reason);
            } catch (IOException ioe) {
                log.info("{} Session transport error", sessionRef, ioe);
            } finally {
                outboundMsgQueue.clear();
            }
        }

        void processPongMessage(long currentTime) {
            lastActivityTime = currentTime;
        }

        void sendMsg(String msg) {
            sendMsg(new SanbinWebSocketTextMsg(msg));
        }

        void sendMsg(SanbinWebSocketMsg<?> msg) {
            if (outboundMsgQueueSize.get() < maxMsgQueueSize) {
                outboundMsgQueue.add(msg);
                outboundMsgQueueSize.incrementAndGet();
                processNextMsg();
            } else {
                log.warn("{} Session closed due to updates queue size exceeded", sessionRef);
                closeSession(CloseStatus.POLICY_VIOLATION.withReason("Max pending updates limit reached!"));
            }
        }

        private void sendMsgInternal(SanbinWebSocketMsg<?> msg) {
            try {
                if (SanbinWebSocketMsgType.TEXT.equals(msg.getType())) {
                    SanbinWebSocketTextMsg textMsg = (SanbinWebSocketTextMsg) msg;
                    this.asyncRemote.sendText(textMsg.getMsg(), this);
                } else {
                    SanbinWebSocketPingMsg pingMsg = (SanbinWebSocketPingMsg) msg;
                    this.asyncRemote.sendPing(pingMsg.getMsg()); // blocking call
                }
                isSending.set(false);
                processNextMsg();
            } catch (Exception e) {
                log.info("{} Failed to send msg", sessionRef, e);
                closeSession(CloseStatus.SESSION_NOT_RELIABLE);
            }
        }

        @Override
        public void onResult(SendResult result) {
            if (!result.isOK()) {
                log.info("{} Failed to send msg", sessionRef, result.getException());
                closeSession(CloseStatus.SESSION_NOT_RELIABLE);
            }
        }

        @SneakyThrows
        private void processNextMsg() {
            if (outboundMsgQueue.isEmpty() || !isSending.compareAndSet(false, true)) {
                return;
            }

            List<SanbinWebSocketMsg<?>> buffer = new ArrayList<>();
            ConcurrentQueueUtils.drain(outboundMsgQueue, buffer, 128, 5, TimeUnit.MILLISECONDS);
            if (!buffer.isEmpty()) {
                List<JsonNode> msgs = new ArrayList<>();
                for (SanbinWebSocketMsg<?> msg : buffer) {
                    websocketDownlink.increment();
                    outboundMsgQueueSize.decrementAndGet();

                    if (msg.getMsg() instanceof String txt) {
                        msgs.add(JacksonUtil.OBJECT_MAPPER.readTree(txt));
                    } else if (msg instanceof SanbinWebSocketPingMsg ping) {
                        sendMsgInternal(ping);
                    }
                }
                if (!msgs.isEmpty()) {
                    sendMsgInternal(new SanbinWebSocketTextMsg(JacksonUtil.OBJECT_MAPPER.writeValueAsString(msgs)));
                }
            } else {
                isSending.set(false);
            }
        }

        public void onMsg(String msg) throws IOException {
            inboundMsgQueue.add(msg);
            tryProcessInboundMsgs();
        }

        void tryProcessInboundMsgs() throws IOException {
            while (!inboundMsgQueue.isEmpty()) {
                if (inboundMsgQueueProcessorLock.tryLock()) {
                    try {
                        String msg;
                        while ((msg = inboundMsgQueue.poll()) != null) {
                            processMsg(this, msg);
                        }
                    } finally {
                        inboundMsgQueueProcessorLock.unlock();
                    }
                } else {
                    return;
                }
            }
        }
    }

    @Override
    public void send(WebSocketSessionRef sessionRef, String msg) {
        log.trace("{} Sending {}", sessionRef, msg);
        String externalId = sessionRef.getSessionId();
        String internalId = externalSessionMap.get(externalId);
        if (internalId != null) {
            SessionMetaData sessionMd = internalSessionMap.get(internalId);
            if (sessionMd != null) {
                sessionMd.sendMsg(msg);
            } else {
                log.warn("[{}][{}] Failed to find session by internal id", externalId, internalId);
            }
        } else {
            log.warn("[{}] Failed to find session by external id", externalId);
        }
    }

    @Override
    public void sendPing(WebSocketSessionRef sessionRef, long currentTime) throws IOException {
        String externalId = sessionRef.getSessionId();
        String internalId = externalSessionMap.get(externalId);
        if (internalId != null) {
            SessionMetaData sessionMd = internalSessionMap.get(internalId);
            if (sessionMd != null) {
                sessionMd.sendPing(currentTime);
            } else {
                log.warn("[{}][{}] Failed to find session by internal id", externalId, internalId);
            }
        } else {
            log.warn("[{}] Failed to find session by external id", externalId);
        }
    }

    @Override
    public void close(WebSocketSessionRef sessionRef, CloseStatus reason) throws IOException {
        String externalId = sessionRef.getSessionId();
        log.info("{} Processing close request, reason {}", sessionRef, reason);
        String internalId = externalSessionMap.get(externalId);
        if (internalId != null) {
            SessionMetaData sessionMd = getSessionMd(internalId);
            if (sessionMd != null) {
                sessionMd.session.close(reason);
            } else {
                log.warn("[{}][{}] Failed to find session by internal id", externalId, internalId);
            }
        } else {
            log.warn("[{}] Failed to find session by external id", externalId);
        }
    }

}
