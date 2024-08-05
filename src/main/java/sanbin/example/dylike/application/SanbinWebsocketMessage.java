/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import sanbin.example.dylike.adapter.websocket.WsCmdType;

@AllArgsConstructor
@Data
@ToString
public class SanbinWebsocketMessage {
    private final int cmdId;
    private WsCmdType type;
    private long ts;
    private int errorCode;
    private String errorMsg;
    private Object body;

    public SanbinWebsocketMessage(int cmdId, WebsocketErrorCode errorCode, String errorMsg) {
        super();
        this.cmdId = cmdId;
        this.errorCode = errorCode.getCode();
        this.errorMsg = errorMsg != null ? errorMsg : errorCode.getDefaultMsg();
    }

    public SanbinWebsocketMessage(int cmdId, WsCmdType type, long ts, Object body) {
        this.cmdId = cmdId;
        this.ts = ts;
        this.body = body;
        this.type = type;
    }

    public SanbinWebsocketMessage(int cmdId, WsCmdType type, long ts, WebsocketErrorCode errorCode) {
        this.cmdId = cmdId;
        this.type = type;
        this.ts = ts;
        this.errorMsg = errorMsg != null ? errorMsg : errorCode.getDefaultMsg();
    }
}
