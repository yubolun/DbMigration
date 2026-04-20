package com.dbmigration.sync.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步日志实体
 */
@Data
@TableName("sync_log")
public class SyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务ID */
    private Long taskId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 总行数 */
    private Long totalRows;

    /** 成功行数 */
    private Long successRows;

    /** 失败行数 */
    private Long failedRows;

    /** 状态: RUNNING/SUCCESS/FAILED */
    private String status;

    /** 错误信息 */
    private String errorMsg;

    /** 每秒处理行数 */
    private Double qps;

    /** 任务名称（非持久化，关联查询填充） */
    @TableField(exist = false)
    private String taskName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
