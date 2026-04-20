package com.dbmigration.datasource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源配置实体
 */
@Data
@TableName("ds_config")
public class DataSourceConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源名称 */
    private String name;

    /** 数据库类型: MYSQL/ORACLE/POSTGRESQL/DM/GAUSSDB/OCEANBASE */
    private String dbType;

    /** 主机地址 */
    private String host;

    /** 端口 */
    private Integer port;

    /** 数据库名/SID/服务名 */
    private String dbName;

    /** 用户名 */
    private String username;

    /** AES-GCM 加密后的密码 */
    private String password;

    /** 额外连接参数 */
    private String extraParams;

    /** 状态: 0-未知 1-在线 2-离线 */
    private Integer status;

    /** 最后检测时间 */
    private LocalDateTime lastPingTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
