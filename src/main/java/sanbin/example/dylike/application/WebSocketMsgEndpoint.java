/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.application;

import org.springframework.web.socket.CloseStatus;

import java.io.IOException;


public interface WebSocketMsgEndpoint {

    void send(WebSocketSessionRef sessionRef, String msg) throws IOException;

    void sendPing(WebSocketSessionRef sessionRef, long currentTime) throws IOException;

    void close(WebSocketSessionRef sessionRef, CloseStatus withReason) throws IOException;

}
