/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.infrastructure.sql;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import sanbin.example.dylike.util.MessagesStats;
import sanbin.example.dylike.util.StatsFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Data
public class SqlBlockingQueueWrapper<E> {
    private final CopyOnWriteArrayList<SqlBlockingQueue<E>> queues = new CopyOnWriteArrayList<>();
    private final SqlBlockingQueueParams params;
    private ScheduledLogExecutorComponent logExecutor;
    private final Function<E, Integer> hashCodeFunction;
    private final int maxThreads;
    private final StatsFactory statsFactory;

    public void init(ScheduledLogExecutorComponent logExecutor, Consumer<List<E>> saveFunction) {
        for (int i = 0; i < maxThreads; i++) {
            MessagesStats stats = statsFactory.createMessagesStats(params.getStatsNamePrefix(), "queue", String.valueOf(i));
            SqlBlockingQueue<E> queue = new SqlBlockingQueue<>(params, stats);
            queues.add(queue);
            queue.init(logExecutor, saveFunction, i);
        }
    }

    public ListenableFuture<Void> add(E element) {
        int queueIndex = element != null ? (hashCodeFunction.apply(element) & 0x7FFFFFFF) % maxThreads : 0;
        return queues.get(queueIndex).add(element);
    }

    public void destroy() {
        queues.forEach(SqlBlockingQueue::destroy);
    }
}
