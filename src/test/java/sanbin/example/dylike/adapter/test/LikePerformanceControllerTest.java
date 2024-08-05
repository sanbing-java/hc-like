/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.adapter.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LikePerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Environment environment;

    // 300视频数 √
    @Test
    public void video300() throws Exception {
        this.mockMvc.perform(get("/test-like")
                        .param("url", getUrl())
                        .param("connections", "300") // 300个用户连接
                        .param("messageRate", "100")  // 每秒批量上报100条点赞信息
                        .param("videoNum", "300"))   // 300条视频
                .andExpect(status().isOk());
    }

    // 3000视频数 √
    @Test
    public void video3000() throws Exception {
        this.mockMvc.perform(get("/test-like")
                        .param("url", getUrl())
                        .param("connections", "1000")
                        .param("messageRate", "10")
                        .param("videoNum", "3000"))
                .andExpect(status().isOk());
    }

    // 5000视频数 √ 延迟 500ms 左右
    @Test
    public void video5000() throws Exception {
        this.mockMvc.perform(get("/test-like")
                        .param("url", getUrl())
                        .param("connections", "2000")
                        .param("messageRate", "6")
                        .param("videoNum", "5000"))
                .andExpect(status().isOk());
    }

    // 10000视频数 X 延迟2秒,MySql CPU > 80%
    @Test
    public void video10000() throws Exception {
        this.mockMvc.perform(get("/test-like")
                        .param("url", getUrl())
                        .param("connections", "3000")
                        .param("messageRate", "3")
                        .param("videoNum", "10000"))
                .andExpect(status().isOk());
    }

    private String getUrl() {
        return "ws://localhost:" + environment.getProperty("local.server.port") + "/api/ws";
    }
}