/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.infrastructure.gateway;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sanbin.example.dylike.domain.LikeCacheGateway;
import sanbin.example.dylike.infrastructure.dataobject.VLikeDO;
import sanbin.example.dylike.infrastructure.sql.dao.LikeDao;
import sanbin.example.dylike.util.SanbinExecutors;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;


@Component
@Slf4j
public class CaffeineLikeCacheGateway implements LikeCacheGateway {

    static final int INIT_CACHE_LIMIT = 100_000;

    static final int PARTITIONS = 5;

    final ExecutorService cacheExecutors = SanbinExecutors.newWorkStealingPool(50, CaffeineLikeCacheGateway.class);

    final AsyncCache<UUID, Integer> cache = buildCache();

    final Map<Integer, AsyncCache<UUID, Integer>> caches = new ConcurrentHashMap<>();

    @Value("${cache.mode:cluster_proxy}")
    CacheMode cacheMode;

    @Resource
    LikeDao likeDao;

    @PreDestroy
    public void destroy() {
        cacheExecutors.shutdownNow();
    }

    @Override
    public void write(UUID vId, Integer likeNum) {

        routeCache(vId).put(vId, CompletableFuture.supplyAsync(() -> likeNum, cacheExecutors));
    }

    @Override
    public CompletableFuture<Integer> read(UUID vId) {
        try {

            return routeCache(vId).get(vId, (uuid, executor) ->
                            CompletableFuture.supplyAsync(() ->
                                    Optional.ofNullable(likeDao.get(uuid)).map(VLikeDO::getLikeNum).orElse(-1), executor)
                    );

        } catch (Exception e) {
            log.error("read cache error {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(-1);
        }
    }



    @Override
    public int total() {
        if (CacheMode.cluster_proxy == cacheMode) {
            return caches.values().stream().mapToInt(cache -> cache.asMap().size()).sum();
        } else {
            return cache.asMap().size();
        }
    }

    private AsyncCache<UUID, Integer> routeCache(UUID vId) {
        if (CacheMode.cluster_proxy == cacheMode) {

            int cacheIndex = (vId.hashCode() & 0x7FFFFFFF) % PARTITIONS;

            return caches.computeIfAbsent(cacheIndex, key -> buildCache());

        } else if (CacheMode.stand_alone == cacheMode) {
            return cache;
        } else {
            throw new IllegalArgumentException("unsupported cache mode");
        }
    }

    private AsyncCache<UUID, Integer> buildCache() {
        return Caffeine.newBuilder()
                .initialCapacity(INIT_CACHE_LIMIT)
                .maximumSize(INIT_CACHE_LIMIT * 10)
                .expireAfterWrite(1, TimeUnit.DAYS)
                .executor(cacheExecutors)
                .buildAsync();
    }

}