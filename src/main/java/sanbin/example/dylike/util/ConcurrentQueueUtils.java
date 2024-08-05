/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.util;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ConcurrentQueueUtils {

    public static <E> int drain(ConcurrentLinkedQueue<E> queue, Collection<? super E> collection, int maxElements, long timeout, TimeUnit unit)  {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        int drained = 0;

        while (drained < maxElements) {
            E element = queue.poll();
            if (element == null) {
                if (System.nanoTime() >= deadline) {
                    break;
                }
                long remainingTime = deadline - System.nanoTime();
                if (remainingTime > 0) {
                    LockSupport.parkNanos(remainingTime);
                }
            } else {
                collection.add(element);
                drained++;
            }
        }

        return drained;
    }

}