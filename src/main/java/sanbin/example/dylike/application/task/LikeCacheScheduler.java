/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.application.task;

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sanbin.example.dylike.domain.LikeCacheGateway;
import sanbin.example.dylike.infrastructure.sql.dao.LikeDao;
import sanbin.example.dylike.util.StatsFactory;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Component
@Slf4j
@Profile("!test")
public class LikeCacheScheduler {

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private int hikariMaxPoolSize;

    @Resource
    LikeCacheGateway likeCacheGateway;

    @Resource
    StatsFactory statsFactory;

    @Resource
    LikeDao likeDao;

    private ExecutorService executorService;
    private int batchQuerySize;
    private Timer timer;

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(hikariMaxPoolSize);

        batchQuerySize = 100_000 / hikariMaxPoolSize;

        timer = statsFactory.createTimer("cache.fetch.task");
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void refreshCache() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {

            var futures =
                    IntStream.range(0, hikariMaxPoolSize).mapToObj(shardKey ->
                                    CompletableFuture
                                            .supplyAsync(() ->
                                                            likeDao.findTop(hikariMaxPoolSize, shardKey, LocalDateTime.now().minusDays(2), batchQuerySize),
                                                    executorService)
                                            .whenCompleteAsync((list, t) ->
                                                            list.parallelStream().forEach(vLike ->
                                                                    likeCacheGateway.write(vLike.getVId(), vLike.getLikeNum())),
                                                    executorService)
                            )
                            .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).get(1, TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("refreshCache error {}", e.getMessage(), e);
        }

        log.info("refreshCache elapsed {} ms, total cache keys: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS), likeCacheGateway.total());
        timer.record(stopwatch.elapsed());
    }
}