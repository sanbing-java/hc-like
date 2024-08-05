/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.adapter.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryCmd implements WsCmd {

    @JsonProperty("cmdId")
    private int cmdId;

    @JsonProperty("ts")
    private long ts;

    @JsonProperty("vId")
    private UUID vId;

    @Override
    public WsCmdType getType() {
        return WsCmdType.QUERY;
    }
}