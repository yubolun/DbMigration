package com.dbmigration.sync.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务实体
 */
@Data
@TableName("sync_task")
public class SyncTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    private String taskName;

    /** 任务类型: FULL_DATA / FULL_SCHEMA / SELECTIVE */
    private String taskType;

    /** 源数据源ID */
    private Long sourceDsId;

    /** 目标数据源ID */
    private Long targetDsId;

    /** 源 Schema/数据库名 */
    private String sourceSchema;

    /** 目标 Schema/数据库名 */
    private String targetSchema;

    /** 源表名 (SELECTIVE 模式单表) */
    private String sourceTable;

    /** 目标表名 (SELECTIVE 模式单表) */
    private String targetTable;

    /** 表名列表 (JSON数组, 全库模式下可为 null 表示全部) */
    private String tableList;

    /** 结构策略: CREATE_IF_NOT_EXISTS / DROP_AND_CREATE */
    private String schemaStrategy;

    /** 是否包含函数 */
    private Boolean includeFunctions;

    /** 是否包含存储过程 */
    private Boolean includeProcedures;

    /** 是否包含视图 */
    private Boolean includeViews;

    /** 批量大小 */
    private Integer batchSize;

    /** 同步模式: FULL/INCREMENTAL */
    private String syncMode;

    /** Cron 表达式 */
    private String cronExpr;

    /** 状态: IDLE/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 最后同步时间 */
    private LocalDateTime lastSyncTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
