/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.infrastructure.sql;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import sanbin.example.dylike.util.MessagesStats;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SqlBlockingQueue<E> implements SqlQueue<E> {

    private final BlockingQueue<SqlQueueElement<E>> queue = new LinkedBlockingQueue<>();
    private final SqlBlockingQueueParams params;

    private ExecutorService executor;
    private final MessagesStats stats;

    public SqlBlockingQueue(SqlBlockingQueueParams params, MessagesStats stats) {
        this.params = params;
        this.stats = stats;
    }

    @Override
    public void init(ScheduledLogExecutorComponent logExecutor, Consumer<List<E>> saveFunction, int index) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("sql-queue-" + index + "-" + params.getLogName().toLowerCase())
                .setDaemon(true)
                .setPriority(Thread.MAX_PRIORITY)
                .build());
        executor.submit(() -> {
            String logName = params.getLogName();
            int batchSize = params.getBatchSize();
            long maxDelay = params.getMaxDelay();
            final List<SqlQueueElement<E>> entities = new ArrayList<>(batchSize);
            while (!Thread.interrupted()) {
                try {
                    long currentTs = System.currentTimeMillis();
                    SqlQueueElement<E> attr = queue.poll(maxDelay, TimeUnit.MILLISECONDS);
                    if (attr == null) {
                        continue;
                    } else {
                        entities.add(attr);
                    }
                    queue.drainTo(entities, batchSize - 1);
                    boolean fullPack = entities.size() == batchSize;
                    if (log.isDebugEnabled()) {
                        log.debug("[{}] Going to save {} entities", logName, entities.size());
                        log.trace("[{}] Going to save entities: {}", logName, entities);
                    }
                    Stream<E> entitiesStream = entities.stream().map(SqlQueueElement::getEntity);
                    saveFunction.accept(
                            entitiesStream.collect(Collectors.toList())
                    );
                    entities.forEach(v -> v.getFuture().set(null));
                    long elapsed = System.currentTimeMillis() - currentTs;
                    if (elapsed > 300) {
                        log.info("[{}] Saving entities: {}, time elapsed: {} ms", logName, entities.size(), elapsed);
                    }
                    stats.incrementSuccessful(entities.size());
                    if (!fullPack) {
                        long remainingDelay = maxDelay - (System.currentTimeMillis() - currentTs);
                        if (remainingDelay > 0) {
                            Thread.sleep(remainingDelay);
                        }
                    }
                } catch (Throwable t) {
                    if (t instanceof InterruptedException) {
                        log.info("[{}] Queue polling was interrupted", logName);
                        break;
                    } else {
                        log.error("[{}] Failed to save {} entities", logName, entities.size(), t);
                        try {
                            stats.incrementFailed(entities.size());
                            entities.forEach(entityFutureWrapper -> entityFutureWrapper.getFuture().setException(t));
                        } catch (Throwable th) {
                            log.error("[{}] Failed to set future exception", logName, th);
                        }
                    }
                } finally {
                    entities.clear();
                }
            }
            log.info("[{}] Queue polling completed", logName);
        });

        logExecutor.scheduleAtFixedRate(() -> {
            if (!queue.isEmpty() || stats.getTotal() > 0 || stats.getSuccessful() > 0 || stats.getFailed() > 0) {
                log.info("Queue-{} [{}] queueSize [{}] totalAdded [{}] totalSaved [{}] totalFailed [{}]", index,
                        params.getLogName(), queue.size(), stats.getTotal(), stats.getSuccessful(), stats.getFailed());
                stats.reset();
            }
        }, params.getStatsPrintIntervalMs(), params.getStatsPrintIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public ListenableFuture<Void> add(E element) {
        SettableFuture<Void> future = SettableFuture.create();
        queue.add(new SqlQueueElement<>(future, element));
        stats.incrementTotal();
        return future;
    }
}
