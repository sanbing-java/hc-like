/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.infrastructure.dataobject;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class VLikeDO {
    private UUID vId;
    private Integer likeNum;
    private String serviceId;
    private int shardKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public VLikeDO(UUID vId, Integer likeNum) {
        this.vId = vId;
        this.likeNum = likeNum;
    }

}