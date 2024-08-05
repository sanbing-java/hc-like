/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.adapter.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import sanbin.example.dylike.util.JacksonUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Controller
@Slf4j
public class LikePerformanceController {
    int threads = 200;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService nioThreads = Executors.newFixedThreadPool(threads);
    private final ExecutorService executorService = Executors.newFixedThreadPool(threads);

    private final AtomicInteger sentCount = new AtomicInteger();
    private final AtomicInteger receivedCount = new AtomicInteger();
    private Duration totalElapsed = Duration.ZERO;
    private final EventLoopGroup group = new NioEventLoopGroup(threads, nioThreads);

    @GetMapping("/test-like")
    public void testLike(@RequestParam(defaultValue = "ws://example:8080/api/ws") String url,
                         @RequestParam(defaultValue = "3000") Integer connections,
                         @RequestParam(defaultValue = "10000") Integer videoNum,
                         @RequestParam(defaultValue = "3") Integer messageRate) throws Exception {
        final URI uri = URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort();

        try {
            List<Channel> channels = new ArrayList<>();
            for (int i = 0; i < connections; i++) {
                channels.add(createConnect(uri, host, port));
            }

            // 生成视频UUID
            List<String> videoIds = new ArrayList<>(videoNum);
            for (int j = 0; j < videoNum; j++) {
                videoIds.add(UUID.randomUUID().toString());
            }

            long startTime = System.currentTimeMillis();

            // 定时每秒发送消息
            scheduler.scheduleAtFixedRate(() -> {

                var completableFutures = videoIds.stream().map(videoId -> CompletableFuture.runAsync(() -> {
                    ObjectNode payload = JacksonUtil.OBJECT_MAPPER.createObjectNode();
                    ArrayNode cmds = JacksonUtil.OBJECT_MAPPER.createArrayNode();
                    IntStream.range(0, messageRate).mapToObj(l -> JacksonUtil.OBJECT_MAPPER.createObjectNode()).forEach(cmd -> {
                        cmd.put("cmdId", sentCount.incrementAndGet());
                        cmd.put("type", "LIKE");
                        cmd.put("count", 1);
                        cmd.put("vId", videoId);
                        cmd.put("ts", System.currentTimeMillis());
                        cmds.add(cmd);
                    });
                    try {
                        payload.set("cmds", cmds);
                        channels.get(videoIds.indexOf(videoId) % connections)
                                .writeAndFlush(new TextWebSocketFrame(JacksonUtil.OBJECT_MAPPER.writeValueAsString(payload)));
                    } catch (Exception e) {
                        log.error("批量发送报错 {}", e.getMessage());
                    }
                }, executorService)).toArray(CompletableFuture[]::new);

                try {
                    CompletableFuture.allOf(completableFutures).get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }, 0, 1, TimeUnit.SECONDS);

            // 定时每秒打印统计信息
            scheduler.scheduleAtFixedRate(() -> printMetrics(startTime, "TPS"), 1, 1, TimeUnit.SECONDS);

            // 运行测试一段时间后结束
            Thread.sleep(6000000);
        } finally {
            group.shutdownGracefully();
            scheduler.shutdown();
        }
    }

    @PostMapping("/test-query")
    public void testQuery(@RequestParam(defaultValue = "ws://example:8080/api/ws") String url,
                          @RequestParam(defaultValue = "3000") Integer connections,
                          @RequestParam(defaultValue = "3") Integer messageRate,
                          @RequestBody String uuidList) throws Exception {
        final URI uri = URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort();

        // 将输入的UUID字符串拆分成列表
        List<String> uuids = Stream.of(uuidList.split("\\r?\\n"))
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.toList());

        try {
            List<Channel> channels = new ArrayList<>();
            for (int i = 0; i < connections; i++) {
                channels.add(createConnect(uri, host, port));
            }

            long startTime = System.currentTimeMillis();

            // 定时每秒发送消息
            scheduler.scheduleAtFixedRate(() -> {
                var completableFutures = uuids.stream().map(videoId -> CompletableFuture.runAsync(() -> {
                    ObjectNode payload = JacksonUtil.OBJECT_MAPPER.createObjectNode();
                    ArrayNode cmds = JacksonUtil.OBJECT_MAPPER.createArrayNode();
                    IntStream.range(0, messageRate).mapToObj(l -> JacksonUtil.OBJECT_MAPPER.createObjectNode()).forEach(cmd -> {
                        cmd.put("cmdId", sentCount.incrementAndGet());
                        cmd.put("type", "QUERY");
                        cmd.put("vId", videoId);
                        cmd.put("ts", System.currentTimeMillis());
                        cmds.add(cmd);
                    });
                    try {
                        payload.set("cmds", cmds);
                        channels.get(uuids.indexOf(videoId) % connections)
                                .writeAndFlush(new TextWebSocketFrame(JacksonUtil.OBJECT_MAPPER.writeValueAsString(payload)));
                    } catch (Exception e) {
                        log.error("批量发送报错 {}", e.getMessage());
                    }
                }, executorService)).toArray(CompletableFuture[]::new);

                try {
                    CompletableFuture.allOf(completableFutures).get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }, 0, 1, TimeUnit.SECONDS);

            // 定时每秒打印统计信息
            scheduler.scheduleAtFixedRate(() -> printMetrics(startTime, "QPS"), 1, 1, TimeUnit.SECONDS);

            // 运行测试一段时间后结束
            Thread.sleep(6000000);
        } finally {
            group.shutdownGracefully();
            scheduler.shutdown();
        }
    }

    private Channel createConnect(URI uri, String host, int port) throws InterruptedException {
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(8192));
                        pipeline.addLast(new WebSocketFrameAggregator(65536));
                        pipeline.addLast(new WebSocketClientCompressionHandler());
                        pipeline.addLast(new WebSocketClientHandler(WebSocketClientHandshakerFactory
                                .newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders())
                        ));
                    }
                });

        return b.connect(host, port).sync().channel();
    }


    private void printMetrics(long startTime, String metricName) {
        long totalLatency = totalElapsed.toMillis();
        int avgLatency = (int) ((double) totalLatency / (double) receivedCount.get());

        log.info("发送量: {},接收量: {},平均延迟: {} (ms),当前 {}: {}",
                sentCount.get(),
                receivedCount.get(),
                avgLatency,
                metricName,
                new BigDecimal(String.valueOf(sentCount.get() / ((System.currentTimeMillis() - startTime) / 1000.0)))
                        .setScale(2, RoundingMode.HALF_UP));
    }


    private class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private ChannelPromise handshakeFuture;

        public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.handshakeFuture = ctx.newPromise();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            this.handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("Channel inactive, connection closed.");
            super.channelInactive(ctx);
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                log.info("WebSocket Client connected!");
                handshakeFuture.setSuccess();
                return;
            }

            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                throw new IllegalStateException(
                        "Unexpected FullHttpResponse (getStatus=" + response.status());
            }

            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {
                TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                // log.info("Received text: " + textFrame.text());
                handleServerMessage(textFrame.text());
            } else if (frame instanceof PingWebSocketFrame) {
                // log.info("Received ping");
                ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            } else if (frame instanceof CloseWebSocketFrame) {
                log.info("Received closing");
                ch.close();
            }
        }

        private void handleServerMessage(String message) {
            try {
                JsonNode jsonNode = JacksonUtil.OBJECT_MAPPER.readTree(message);

                if (jsonNode.isArray()) {
                    // 处理 JSON 数组
                    ArrayNode arrayNode = (ArrayNode) jsonNode;
                    for (JsonNode node : arrayNode) {
                        processMessageNode(node);
                    }
                } else if (jsonNode.isObject()) {
                    // 处理单个 JSON 对象
                    processMessageNode(jsonNode);
                } else {
                    System.err.println("Unexpected JSON format: " + message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void processMessageNode(JsonNode node) {
            long timestamp = node.get("ts").asLong();
            receivedCount.incrementAndGet();
            long latency = System.currentTimeMillis() - timestamp;
            totalElapsed = totalElapsed.plus(Duration.ofMillis(latency));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }


        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }
    }
}
