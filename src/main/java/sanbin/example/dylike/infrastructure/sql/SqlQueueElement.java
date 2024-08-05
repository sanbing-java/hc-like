/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.infrastructure.sql;

import com.google.common.util.concurrent.SettableFuture;
import lombok.Getter;
import lombok.ToString;

@ToString(exclude = "future")
public final class SqlQueueElement<E> {
    @Getter
    private final SettableFuture<Void> future;
    @Getter
    private final E entity;

    public SqlQueueElement(SettableFuture<Void> future, E entity) {
        this.future = future;
        this.entity = entity;
    }
}


