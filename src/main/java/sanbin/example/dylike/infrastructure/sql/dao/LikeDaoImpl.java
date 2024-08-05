/**
 * 抖音关注：程序员三丙
 * 知识星球：https://t.zsxq.com/j9b21
 */
package sanbin.example.dylike.infrastructure.sql.dao;

import com.google.common.util.concurrent.ListenableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import sanbin.example.dylike.infrastructure.dataobject.VLikeDO;
import sanbin.example.dylike.infrastructure.sql.ScheduledLogExecutorComponent;
import sanbin.example.dylike.infrastructure.sql.SqlBlockingQueueParams;
import sanbin.example.dylike.infrastructure.sql.SqlBlockingQueueWrapper;
import sanbin.example.dylike.util.StatsFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


@Repository
@Slf4j
public class LikeDaoImpl implements LikeDao {

    private SqlBlockingQueueWrapper<VLikeDO> sqlBlockingQueueWrapper;

    @Value("${sql.queue.batch_size:10000}")
    private int sqlQueueBatchSize;

    @Value("${sql.queue.batch_max_delay:5}")
    private long sqlQueueMaxDelay;

    @Value("${sql.stats_print_interval_ms:1000}")
    private long sqlStatsPrintIntervalMs;

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private int hikariMaxPoolSize;

    @Resource
    protected ScheduledLogExecutorComponent logExecutor;

    @Resource
    private StatsFactory statsFactory;

    @Resource
    protected TransactionTemplate transactionTemplate;

    @Resource
    protected JdbcTemplate jdbcTemplate;


    public static final String BATCH_UPDATE = "UPDATE v_like SET like_num = like_num + ? WHERE v_id = ? and service_id = ?";

    public static final String INSERT_OR_UPDATE = "INSERT INTO v_like (v_id, service_id, shard_key, like_num) VALUES(?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE" +
            " like_num = like_num + ?";

    private String hostName;

    @PostConstruct
    protected void init() throws UnknownHostException {

        hostName = InetAddress.getLocalHost().getHostName();

        SqlBlockingQueueParams sqlQueueParams =
                SqlBlockingQueueParams.builder().logName("like_log")
                        .batchSize(sqlQueueBatchSize)
                        .maxDelay(sqlQueueMaxDelay)
                        .statsPrintIntervalMs(sqlStatsPrintIntervalMs)
                        .statsNamePrefix("v.like.batch.sql")
                        .build();

        Function<VLikeDO, Integer> hashcodeFunction = entity -> entity.getVId().hashCode();
        sqlBlockingQueueWrapper = new SqlBlockingQueueWrapper<>(sqlQueueParams, hashcodeFunction,
                Math.max(Runtime.getRuntime().availableProcessors() * 4, hikariMaxPoolSize),
                statsFactory);

        sqlBlockingQueueWrapper.init(logExecutor, vs ->
                saveOrUpdate(vs
                        .stream()
                        .collect(Collectors.groupingBy(VLikeDO::getVId, Collectors.summingInt(VLikeDO::getLikeNum)))
                        .entrySet()
                        .stream()
                        .map(entry -> new VLikeDO(entry.getKey(), entry.getValue()))
                        .toList())
        );
    }

    private void saveOrUpdate(List<VLikeDO> vLikeDOS) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {

                int[] result = jdbcTemplate.batchUpdate(BATCH_UPDATE, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        VLikeDO vLikeDO = vLikeDOS.get(i);
                        ps.setInt(1, vLikeDO.getLikeNum());
                        ps.setString(2, vLikeDO.getVId().toString());
                        ps.setString(3, hostName);
                    }

                    @Override
                    public int getBatchSize() {
                        return vLikeDOS.size();
                    }
                });

                int updatedCount = 0;
                for (int i = 0; i < result.length; i++) {
                    if (result[i] == 0) {
                        updatedCount++;
                    }
                }

                List<VLikeDO> insertEntities = new ArrayList<>(updatedCount);
                for (int i = 0; i < result.length; i++) {
                    if (result[i] == 0) {
                        insertEntities.add(vLikeDOS.get(i));
                    }
                }

                jdbcTemplate.batchUpdate(INSERT_OR_UPDATE, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        VLikeDO vLikeDO = insertEntities.get(i);
                        ps.setString(1, vLikeDO.getVId().toString());
                        ps.setString(2, hostName);
                        ps.setInt(3, Math.abs(vLikeDO.getVId().hashCode() & 0xFF));
                        ps.setInt(4, vLikeDO.getLikeNum());
                        ps.setInt(5, vLikeDO.getLikeNum());
                    }

                    @Override
                    public int getBatchSize() {
                        return insertEntities.size();
                    }
                });
            }
        });
    }

    @PreDestroy
    protected void destroy() {
        if (sqlBlockingQueueWrapper != null) {
            sqlBlockingQueueWrapper.destroy();
        }
    }


    @Override
    public ListenableFuture<Void> increaseLikeNum(UUID videoId, Integer likeTimes) {
        VLikeDO vLikeDO = new VLikeDO();
        vLikeDO.setVId(videoId);
        vLikeDO.setLikeNum(likeTimes);

        return sqlBlockingQueueWrapper.add(vLikeDO);
    }

    @Override
    public ListenableFuture<Void> increaseLikeNum(UUID vId) {
        return increaseLikeNum(vId, 1);
    }

    @Override
    public List<VLikeDO> findTop(int sharedNum, int shardKey, LocalDateTime startTime, int limit) {
        final String sql =
                "SELECT v_id,SUM(like_num) as like_num FROM v_like WHERE shard_key % ? = ? AND update_time > ? GROUP BY v_id ORDER BY update_time " +
                        "DESC, like_num DESC LIMIT ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
                    VLikeDO vLike = new VLikeDO();
                    vLike.setVId(UUID.fromString(rs.getString("v_id")));
                    vLike.setLikeNum(rs.getInt("like_num"));
                    return vLike;
                },
                sharedNum,
                shardKey,
                startTime,
                limit);
    }

    @Override
    public VLikeDO get(UUID vId) {
        final String sql = "SELECT * FROM v_like WHERE v_id = ?";

        List<VLikeDO> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            VLikeDO vLike = new VLikeDO();
            vLike.setVId(UUID.fromString(rs.getString("v_id")));
            vLike.setLikeNum(rs.getInt("like_num"));
            vLike.setServiceId(rs.getString("service_id"));
            vLike.setShardKey(rs.getInt("shard_key"));
            vLike.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
            vLike.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());

            return vLike;
        }, vId.toString());

        return list.isEmpty() ? null : list.get(0);
    }

}