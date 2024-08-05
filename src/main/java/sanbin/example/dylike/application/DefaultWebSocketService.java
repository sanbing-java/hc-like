/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import sanbin.example.dylike.adapter.websocket.*;
import sanbin.example.dylike.domain.LikeCacheGateway;
import sanbin.example.dylike.infrastructure.sql.dao.LikeDao;
import sanbin.example.dylike.util.DefaultCounter;
import sanbin.example.dylike.util.JacksonUtil;
import sanbin.example.dylike.util.SanbinExecutors;
import sanbin.example.dylike.util.StatsFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static sanbin.example.dylike.util.MoreThread.toListenableFuture;


@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultWebSocketService implements WebSocketService {

    public static final int NUMBER_OF_PING_ATTEMPTS = 3;

    private static final int UNKNOWN_SUBSCRIPTION_ID = 0;
    private static final String PROCESSING_MSG = "[{}] Processing: {}";
    private static final String SESSION_META_DATA_NOT_FOUND = "Session meta-data not found!";

    private final ConcurrentMap<String, WsSessionMetaData> wsSessionsMap = new ConcurrentHashMap<>();

    @Resource
    private WebSocketMsgEndpoint msgEndpoint;

    @Resource
    private LikeDao likeDao;

    @Resource
    private LikeCacheGateway likeCacheGateway;

    @Resource
    private StatsFactory statsFactory;

    @Value("${server.ws.ping_timeout:30000}")
    private long pingTimeout;

    @Value("${server.undertow.threads.worker}")
    private int worker;

    private final ConcurrentMap<String, Map<Integer, Integer>> sessionCmdMap = new ConcurrentHashMap<>();

    private ExecutorService executor;
    private ScheduledExecutorService pingExecutor;

    private Map<WsCmdType, WsCmdHandler<? extends WsCmd>> cmdsHandlers;

    private DefaultCounter websocketUplink;
    private Timer likeRtt;
    private Timer uplinkRtt;

    @PostConstruct
    public void init() {
        executor = SanbinExecutors.newWorkStealingPool(worker, "websocket-biz-handler");

        pingExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                .setNameFormat("ws-ping-thread-%d")
                .setDaemon(true)
                .setPriority(Thread.MAX_PRIORITY)
                .build());

        pingExecutor.scheduleWithFixedDelay(this::sendPing, pingTimeout / NUMBER_OF_PING_ATTEMPTS, pingTimeout / NUMBER_OF_PING_ATTEMPTS,
                TimeUnit.MILLISECONDS);

        cmdsHandlers = new EnumMap<>(WsCmdType.class);
        cmdsHandlers.put(WsCmdType.LIKE, newCmdHandler(this::handleWsLikeCmd));
        cmdsHandlers.put(WsCmdType.QUERY, newCmdHandler(this::handleWsQueryCmd));

        websocketUplink = statsFactory.createDefaultCounter("websocket_uplink");
        likeRtt = statsFactory.createTimer("websocket_like_rtt");
        uplinkRtt = statsFactory.createTimer("websocket_receive_rtt");
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (pingExecutor != null) {
            pingExecutor.shutdownNow();
        }

        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public void handleSessionEvent(WebSocketSessionRef sessionRef, SessionEvent event) {
        String sessionId = sessionRef.getSessionId();
        log.debug(PROCESSING_MSG, sessionId, event);
        switch (event.getEventType()) {
            case ESTABLISHED:
                wsSessionsMap.put(sessionId, new WsSessionMetaData(sessionRef));
                break;
            case ERROR:
                log.debug("[{}] Unknown websocket session error: ", sessionId,
                        event.getError().orElse(new RuntimeException("No error specified")));
                break;
            case CLOSED:
                cleanupSessionById(sessionId);
                break;
        }
    }

    @Override
    public void handleCommands(WebSocketSessionRef sessionRef, WsCommandsWrapper commandsWrapper) {
        if (commandsWrapper == null || CollectionUtils.isEmpty(commandsWrapper.getCmds())) {
            return;
        }
        String sessionId = sessionRef.getSessionId();
        if (!validateSessionMetadata(sessionRef, UNKNOWN_SUBSCRIPTION_ID, sessionId)) {
            return;
        }


        for (WsCmd cmd : commandsWrapper.getCmds()) {
            log.debug("[{}][{}][{}] Processing cmd: {}", sessionId, cmd.getType(), cmd.getCmdId(), cmd);
            try {
                Optional.ofNullable(cmdsHandlers.get(cmd.getType()))
                        .ifPresent(cmdHandler -> cmdHandler.handle(sessionRef, cmd));
            } catch (Exception e) {
                log.error("[sessionId: {}, name: {}] Failed to handle WS cmd: {}", sessionId,
                        sessionRef.getSecurityCtx().getName(), cmd, e);
            } finally {
                websocketUplink.increment();
                uplinkRtt.record(Duration.ofMillis(System.currentTimeMillis() - cmd.getTs()));
            }
        }
    }


    @Override
    public void sendError(WebSocketSessionRef sessionRef, int subId, WebsocketErrorCode errorCode, String errorMsg) {
        sendMessage(sessionRef, new SanbinWebsocketMessage(subId, errorCode, errorMsg));
    }

    /**
     * 点赞
     * @param sessionRef
     * @param cmd
     */
    private void handleWsLikeCmd(WebSocketSessionRef sessionRef, LikeCmd cmd) {
        if (!validateCmd(sessionRef, cmd)) {
            return;
        }

        ListenableFuture<Void> future = likeDao.increaseLikeNum(cmd.getVId());

        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(Void result) {

                sendMessage(sessionRef, new SanbinWebsocketMessage(cmd.getCmdId(), cmd.getType(), cmd.getTs(), WebsocketErrorCode.NO_ERROR));

                likeRtt.record(Duration.ofMillis(System.currentTimeMillis() - cmd.getTs()));
            }

            @Override
            public void onFailure(Throwable t) {
                sendMessage(sessionRef, new SanbinWebsocketMessage(cmd.getCmdId(), cmd.getType(), cmd.getTs(), WebsocketErrorCode.INTERNAL_ERROR));
            }
        }, executor);
    }

    /**
     * 查询点赞
     *
     * @param sessionRef
     * @param cmd
     */
    private void handleWsQueryCmd(WebSocketSessionRef sessionRef, QueryCmd cmd) {
        if (!validateCmd(sessionRef, cmd)) {
            return;
        }

        Futures.addCallback(toListenableFuture(likeCacheGateway.read(cmd.getVId())), new FutureCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                sendMessage(sessionRef, new SanbinWebsocketMessage(cmd.getCmdId(), cmd.getType(), cmd.getTs(), result));

                likeRtt.record(Duration.ofMillis(System.currentTimeMillis() - cmd.getTs()));
            }

            @Override
            public void onFailure(Throwable t) {
                sendMessage(sessionRef, new SanbinWebsocketMessage(cmd.getCmdId(), cmd.getType(), cmd.getTs(), WebsocketErrorCode.INTERNAL_ERROR));
            }
        }, executor);
    }

    private void cleanupSessionById(String sessionId) {
        wsSessionsMap.remove(sessionId);
        sessionCmdMap.remove(sessionId);
    }

    private boolean validateSessionMetadata(WebSocketSessionRef sessionRef, int cmdId, String sessionId) {
        WsSessionMetaData sessionMD = wsSessionsMap.get(sessionId);
        if (sessionMD == null) {
            log.warn("[{}] Session meta data not found. ", sessionId);
            sendError(sessionRef, cmdId, WebsocketErrorCode.INTERNAL_ERROR, SESSION_META_DATA_NOT_FOUND);
            return false;
        } else {
            return true;
        }
    }

    private boolean validateCmd(WebSocketSessionRef sessionRef, WsCmd cmd) {
        return validateCmd(sessionRef, cmd, null);
    }

    private <C extends WsCmd> boolean validateCmd(WebSocketSessionRef sessionRef, C cmd, Runnable validator) {
        if (cmd.getCmdId() < 0) {
            sendError(sessionRef, cmd.getCmdId(), WebsocketErrorCode.BAD_REQUEST, "Cmd id is negative value!");
            return false;
        }
        try {
            if (validator != null) {
                validator.run();
            }
        } catch (Exception e) {
            sendError(sessionRef, cmd.getCmdId(), WebsocketErrorCode.BAD_REQUEST, e.getMessage());
            return false;
        }
        return true;
    }

    private void sendMessage(WebSocketSessionRef sessionRef, Object message) {
        try {
            String msg = JacksonUtil.OBJECT_MAPPER.writeValueAsString(message);
            executor.submit(() -> {
                try {
                    msgEndpoint.send(sessionRef, msg);
                } catch (IOException e) {
                    log.warn("[{}] Failed to send reply: {}", sessionRef.getSessionId(), message, e);
                }
            });
        } catch (JsonProcessingException e) {
            log.warn("[{}] Failed to encode reply: {}", sessionRef.getSessionId(), message, e);
        }
    }

    private void sendPing() {
        long currentTime = System.currentTimeMillis();
        wsSessionsMap.values().forEach(md ->
                executor.submit(() -> {
                    try {
                        msgEndpoint.sendPing(md.getSessionRef(), currentTime);
                    } catch (IOException e) {
                        log.warn("[{}] Failed to send ping:", md.getSessionRef().getSessionId(), e);
                    }
                }));
    }

    public static <C extends WsCmd> WsCmdHandler<C> newCmdHandler(BiConsumer<WebSocketSessionRef, C> handler) {
        return new WsCmdHandler<>(handler);
    }

    public record WsCmdHandler<C extends WsCmd>(BiConsumer<WebSocketSessionRef, C> handler) {
        public void handle(WebSocketSessionRef sessionRef, WsCmd cmd) {
            handler.accept(sessionRef, (C) cmd);
        }
    }

}
