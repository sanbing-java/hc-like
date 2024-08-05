/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.infrastructure.sql;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Builder
public class SqlBlockingQueueParams {

    private final String logName;
    private final int batchSize;
    private final long maxDelay;
    private final long statsPrintIntervalMs;
    private final String statsNamePrefix;
}
