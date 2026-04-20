-- ============================================================
-- 升级脚本: 为 sync_task 表添加三模式同步扩展字段
-- 执行此脚本后重启后端即可
-- ============================================================

ALTER TABLE sync_task ADD COLUMN task_type VARCHAR(20) DEFAULT 'SELECTIVE' COMMENT '任务类型: FULL_DATA/FULL_SCHEMA/SELECTIVE' AFTER task_name;

ALTER TABLE sync_task MODIFY COLUMN source_table VARCHAR(200) NULL COMMENT '源表名(SELECTIVE模式)';
ALTER TABLE sync_task MODIFY COLUMN target_table VARCHAR(200) NULL COMMENT '目标表名(SELECTIVE模式)';

ALTER TABLE sync_task ADD COLUMN table_list TEXT COMMENT '表名列表(JSON数组)' AFTER target_table;
ALTER TABLE sync_task ADD COLUMN schema_strategy VARCHAR(30) DEFAULT 'CREATE_IF_NOT_EXISTS' COMMENT '结构策略' AFTER table_list;
ALTER TABLE sync_task ADD COLUMN include_functions TINYINT DEFAULT 0 COMMENT '是否包含函数' AFTER schema_strategy;
ALTER TABLE sync_task ADD COLUMN include_procedures TINYINT DEFAULT 0 COMMENT '是否包含存储过程' AFTER include_functions;

-- 添加视图同步字段到 sync_task 表
ALTER TABLE sync_task ADD COLUMN include_views TINYINT(1) DEFAULT 0 COMMENT '是否包含视图';

-- 添加 source_schema 和 target_schema 字段到 sync_task 表
ALTER TABLE sync_task ADD COLUMN source_schema VARCHAR(128) DEFAULT NULL COMMENT '源 Schema/数据库名' AFTER target_ds_id;
ALTER TABLE sync_task ADD COLUMN target_schema VARCHAR(128) DEFAULT NULL COMMENT '目标 Schema/数据库名' AFTER source_schema;
