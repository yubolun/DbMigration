package com.dbmigration.sync.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段映射实体
 */
@Data
@TableName("field_mapping")
public class FieldMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务ID */
    private Long taskId;

    /** 源字段名 */
    private String sourceColumn;

    /** 源字段类型 */
    private String sourceType;

    /** 目标字段名 */
    private String targetColumn;

    /** 目标字段类型 */
    private String targetType;

    /** 类型转换器类名 */
    private String typeConverter;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
