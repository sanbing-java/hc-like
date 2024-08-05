/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.adapter.websocket;

import lombok.RequiredArgsConstructor;

import java.nio.ByteBuffer;

@RequiredArgsConstructor
public class SanbinWebSocketPingMsg implements SanbinWebSocketMsg<ByteBuffer> {

    public static SanbinWebSocketPingMsg INSTANCE = new SanbinWebSocketPingMsg();

    private static final ByteBuffer PING_MSG = ByteBuffer.wrap(new byte[]{});

    @Override
    public SanbinWebSocketMsgType getType() {
        return SanbinWebSocketMsgType.PING;
    }

    @Override
    public ByteBuffer getMsg() {
        return PING_MSG;
    }
}
