/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.infrastructure.sql;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.function.Consumer;

public interface SqlQueue<E> {

    void init(ScheduledLogExecutorComponent logExecutor, Consumer<List<E>> saveFunction, int queueIndex);

    void destroy();

    ListenableFuture<Void> add(E element);
}
