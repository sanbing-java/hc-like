/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.util;

import io.micrometer.core.instrument.Timer;

public interface StatsFactory {

    StatsCounter createStatsCounter(String key, String statsName, String... otherTags);

    DefaultCounter createDefaultCounter(String key, String... tags);

    MessagesStats createMessagesStats(String key, String... tags);

    Timer createTimer(String key, String... tags);

}
