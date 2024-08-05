/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.util;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.CompletableFuture;

public class MoreThread {
    public static <T> ListenableFuture<T> toListenableFuture(CompletableFuture<T> completableFuture) {
        SettableFuture<T> settableFuture = SettableFuture.create();
        completableFuture.whenComplete((result, exception) -> {
            if (exception != null) {
                settableFuture.setException(exception);
            } else {
                settableFuture.set(result);
            }
        });
        return settableFuture;
    }
}