/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.infrastructure.sql.dao;

import com.google.common.util.concurrent.ListenableFuture;
import sanbin.example.dylike.infrastructure.dataobject.VLikeDO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LikeDao {

    /**
     * 递增点赞
     *
     * @param videoId 视频ID
     * @param likeTimes 点赞次数
     * @return
     */
    ListenableFuture<Void> increaseLikeNum(UUID videoId, Integer likeTimes);

    /**
     * 递增点赞
     *
     * @param vId 视频ID
     * @return
     */
    ListenableFuture<Void> increaseLikeNum(UUID vId);

    /**
     * 查询最新的点赞
     * @param sharedNum
     * @param shardKey
     * @param startTime
     * @param limit
     * @return
     */
    List<VLikeDO> findTop(int sharedNum, int shardKey, LocalDateTime startTime, int limit);

    /**
     * 查询数据
     *
     * @param vId
     * @return
     */
    VLikeDO get(UUID vId);
}