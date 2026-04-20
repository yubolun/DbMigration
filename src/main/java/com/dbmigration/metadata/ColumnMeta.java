package com.dbmigration.metadata;

import lombok.Data;

/**
 * 列元数据
 */
@Data
public class ColumnMeta {
    /** 列名 */
    private String columnName;
    /** 数据类型 (如 VARCHAR, INT, NUMBER 等) */
    private String dataType;
    /** 完整类型名 (如 VARCHAR(255), NUMBER(10,2)) */
    private String fullTypeName;
    /** 长度/精度 */
    private Integer columnSize;
    /** 小数位数 */
    private Integer decimalDigits;
    /** 是否可为空 */
    private Boolean nullable;
    /** 是否为主键 */
    private Boolean primaryKey;
    /** 注释 */
    private String comment;
}
