/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.adapter.websocket;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SanbinWebSocketTextMsg implements SanbinWebSocketMsg<String> {

    private final String value;

    @Override
    public SanbinWebSocketMsgType getType() {
        return SanbinWebSocketMsgType.TEXT;
    }

    @Override
    public String getMsg() {
        return value;
    }
}
