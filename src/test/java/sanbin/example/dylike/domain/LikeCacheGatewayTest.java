/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.domain;

import com.google.common.base.Stopwatch;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeCacheGatewayTest {

    @Resource
    LikeCacheGateway gateway;

    @Test
    void write() {
        int limit = 1_000_000;

        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            uuids.add(UUID.randomUUID());
        }

        Stopwatch qps = Stopwatch.createStarted();

        for (int j = 0; j < 20; j++) {

            Stopwatch stopwatch = Stopwatch.createStarted();

            uuids.parallelStream().forEach(uuid -> gateway.write(uuid, 1));

            System.out.println(stopwatch.elapsed() + "  " + gateway.total());
        }
        System.out.println(qps.elapsed(TimeUnit.MILLISECONDS) / 20);
    }

    @Test
    void read() {
        for (int i = 0; i < 100; i++) {
            System.out.println(gateway.read(UUID.fromString("1899fda2-4fe3-11ef-b979-0fc645608abb")));
        }
    }

}