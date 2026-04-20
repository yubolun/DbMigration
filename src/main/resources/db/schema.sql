-- ============================================================
-- DB Migration 系统库初始化 DDL
-- ============================================================

-- 数据源配置表
CREATE TABLE IF NOT EXISTS ds_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL COMMENT '数据源名称',
    db_type         VARCHAR(20)  NOT NULL COMMENT '数据库类型: MYSQL/ORACLE/POSTGRESQL/DM/GAUSSDB/OCEANBASE',
    host            VARCHAR(255) NOT NULL COMMENT '主机地址',
    port            INT          NOT NULL COMMENT '端口',
    db_name         VARCHAR(100) NOT NULL COMMENT '数据库名/SID/服务名',
    username        VARCHAR(100) NOT NULL COMMENT '用户名',
    password        VARCHAR(512) NOT NULL COMMENT 'AES-GCM 加密后的密码',
    extra_params    VARCHAR(512)          COMMENT '额外连接参数',
    status          TINYINT DEFAULT 0     COMMENT '0-未知 1-在线 2-离线',
    last_ping_time  DATETIME              COMMENT '最后检测时间',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置表';

-- 同步任务表
CREATE TABLE IF NOT EXISTS sync_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name       VARCHAR(200) NOT NULL COMMENT '任务名称',
    task_type       VARCHAR(20)  DEFAULT 'SELECTIVE' COMMENT '任务类型: FULL_DATA/FULL_SCHEMA/SELECTIVE',
    source_ds_id    BIGINT       NOT NULL COMMENT '源数据源ID',
    target_ds_id    BIGINT       NOT NULL COMMENT '目标数据源ID',
    source_table    VARCHAR(200)          COMMENT '源表名(SELECTIVE模式)',
    target_table    VARCHAR(200)          COMMENT '目标表名(SELECTIVE模式)',
    table_list      TEXT                  COMMENT '表名列表(JSON数组), null表示全部',
    schema_strategy VARCHAR(30)  DEFAULT 'CREATE_IF_NOT_EXISTS' COMMENT '结构策略: CREATE_IF_NOT_EXISTS/DROP_AND_CREATE',
    include_functions  TINYINT   DEFAULT 0 COMMENT '是否包含函数',
    include_procedures TINYINT   DEFAULT 0 COMMENT '是否包含存储过程',
    include_views      TINYINT   DEFAULT 0 COMMENT '是否包含视图',
    source_schema   VARCHAR(200)          COMMENT '源Schema/数据库名',
    target_schema   VARCHAR(200)          COMMENT '目标Schema/数据库名',
    batch_size      INT DEFAULT 1000      COMMENT '批量大小',
    sync_mode       VARCHAR(20) DEFAULT 'FULL' COMMENT '同步模式: FULL/INCREMENTAL',
    cron_expr       VARCHAR(100)          COMMENT 'Cron 表达式(定时任务)',
    status          VARCHAR(20) DEFAULT 'IDLE' COMMENT '任务状态: IDLE/RUNNING/SUCCESS/FAILED',
    last_sync_time  DATETIME              COMMENT '最后同步时间',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步任务表';

-- 字段映射表
CREATE TABLE IF NOT EXISTS field_mapping (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT       NOT NULL COMMENT '任务ID',
    source_column   VARCHAR(200) NOT NULL COMMENT '源字段名',
    source_type     VARCHAR(100)          COMMENT '源字段类型',
    target_column   VARCHAR(200) NOT NULL COMMENT '目标字段名',
    target_type     VARCHAR(100)          COMMENT '目标字段类型',
    type_converter  VARCHAR(200)          COMMENT '类型转换器类名',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段映射表';

-- 同步日志表
CREATE TABLE IF NOT EXISTS sync_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT       NOT NULL COMMENT '任务ID',
    start_time      DATETIME     NOT NULL COMMENT '开始时间',
    end_time        DATETIME              COMMENT '结束时间',
    total_rows      BIGINT DEFAULT 0      COMMENT '总行数',
    success_rows    BIGINT DEFAULT 0      COMMENT '成功行数',
    failed_rows     BIGINT DEFAULT 0      COMMENT '失败行数',
    status          VARCHAR(20)  NOT NULL COMMENT '状态: RUNNING/SUCCESS/FAILED',
    error_msg       TEXT                  COMMENT '错误信息',
    qps             DOUBLE DEFAULT 0      COMMENT '每秒处理行数',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步日志表';
