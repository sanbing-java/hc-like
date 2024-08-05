/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.adapter.websocket;

import com.fasterxml.jackson.annotation.JsonIgnore;


public interface WsCmd {

    int getCmdId();

    long getTs();

    @JsonIgnore
    WsCmdType getType();

}
