/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.application;


import lombok.Data;
import lombok.ToString;

@ToString
@Data
public class WsSessionMetaData {
    private WebSocketSessionRef sessionRef;
    private long lastActivityTime;

    public WsSessionMetaData(WebSocketSessionRef sessionRef) {
        super();
        this.sessionRef = sessionRef;
        this.lastActivityTime = System.currentTimeMillis();
    }

}
