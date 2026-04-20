package com.dbmigration.metadata;

import lombok.Data;

/**
 * 表元数据
 */
@Data
public class TableMeta {
    /** 表名 */
    private String tableName;
    /** 注释 */
    private String comment;
    /** 行数估算 */
    private Long estimatedRows;
}
