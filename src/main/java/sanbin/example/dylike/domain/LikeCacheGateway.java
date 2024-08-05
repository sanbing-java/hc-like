/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.domain;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface LikeCacheGateway {

    void write(UUID vId,Integer likeNum);

    CompletableFuture<Integer> read(UUID vId);

    int total();
}