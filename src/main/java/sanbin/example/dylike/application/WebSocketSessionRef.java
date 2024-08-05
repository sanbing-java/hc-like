/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.application;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.boot.autoconfigure.security.SecurityProperties;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;


@Builder
@Data
@EqualsAndHashCode(of = "sessionId")
@ToString(of = "sessionId")
public class WebSocketSessionRef {

    private static final long serialVersionUID = 1L;

    private final String sessionId;
    private volatile SecurityProperties.User securityCtx;
    private final InetSocketAddress localAddress;
    private final InetSocketAddress remoteAddress;
    private final AtomicInteger sessionSubIdSeq = new AtomicInteger();

}
