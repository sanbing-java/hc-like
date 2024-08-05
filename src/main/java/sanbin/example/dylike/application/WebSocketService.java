/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.application;

import sanbin.example.dylike.adapter.websocket.SessionEvent;
import sanbin.example.dylike.adapter.websocket.WsCommandsWrapper;


public interface WebSocketService {

    void handleSessionEvent(WebSocketSessionRef sessionRef, SessionEvent sessionEvent);

    void handleCommands(WebSocketSessionRef sessionRef, WsCommandsWrapper commandsWrapper);

    void sendError(WebSocketSessionRef sessionRef, int subId, WebsocketErrorCode errorCode, String errorMsg);

}
