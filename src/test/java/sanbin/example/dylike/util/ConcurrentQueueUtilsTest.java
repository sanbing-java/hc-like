/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.util;

import com.google.common.base.Stopwatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static sanbin.example.dylike.util.ConcurrentQueueUtils.drain;

class ConcurrentQueueUtilsTest {

    @Test
    void drainTest() {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 100000; i++) {
            queue.add("Element " + i);
        }

        Stopwatch started = Stopwatch.createStarted();
        do {
            List<String> drainedElements = new ArrayList<>();
            drain(queue, drainedElements, 10000, 5, TimeUnit.MILLISECONDS);

            System.out.println("All drained elements: " + drainedElements.size());
        } while (!queue.isEmpty());
        System.out.println("耗时：" + started.elapsed(TimeUnit.MILLISECONDS));
    }
}