--
-- 抖音关注：程序员三丙
-- 知识星球：https://t.zsxq.com/j9b21
--

--
-- 抖音关注：程序员三丙
--

create table v_like
(
    v_id        varchar(64)                                   not null comment '视频id',
    service_id  varchar(64)                                   not null comment '服务ID',
    shard_key   tinyint unsigned default '0'                  not null comment '分片KEY',
    like_num    int unsigned     default '0'                  not null comment '点赞数',
    create_time datetime(3)      default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    update_time datetime(3)      default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '更新时间',
    primary key (v_id, service_id)
);

create index idx_top
    on v_like (shard_key asc, update_time desc);