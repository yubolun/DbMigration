package com.dbmigration.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.dbmigration.common.AesUtils;
import com.dbmigration.common.DbType;
import com.dbmigration.datasource.entity.DataSourceConfig;
import com.dbmigration.datasource.mapper.DataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源管理器
 * 每个注册的数据源使用独立的 DruidDataSource 连接池
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicDataSourceManager {

    private final Map<Long, DruidDataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final DataSourceMapper dataSourceMapper;

    /**
     * 注册数据源（创建 Druid 连接池）
     */
    public void register(DataSourceConfig config) {
        // 如果已存在，先彻底关闭
        unregister(config.getId());

        try {
            DbType dbType = DbType.valueOf(config.getDbType());
            String password = AesUtils.decrypt(config.getPassword());
            String jdbcUrl = dbType.buildUrl(config.getHost(), config.getPort(), config.getDbName(), config.getExtraParams());

            DruidDataSource ds = new DruidDataSource();
            ds.setName("ds-" + config.getId() + "-" + config.getName());
            ds.setDriverClassName(dbType.getDriverClassName());
            ds.setUrl(jdbcUrl);
            ds.setUsername(config.getUsername());
            ds.setPassword(password);

            // 连接池配置
            ds.setInitialSize(0);  // 不立即创建连接，避免错误配置时启动报错
            ds.setMinIdle(2);
            ds.setMaxActive(10);
            ds.setMaxWait(10000);
            ds.setTimeBetweenEvictionRunsMillis(60000);
            ds.setMinEvictableIdleTimeMillis(300000);
            ds.setValidationQuery(dbType.getValidationQuery());
            ds.setTestWhileIdle(true);
            ds.setTestOnBorrow(true);
            ds.setTestOnReturn(false);

            // 连接失败后不再无限重试，避免错误日志刷屏
            ds.setBreakAfterAcquireFailure(true);
            ds.setConnectionErrorRetryAttempts(3);

            // 关闭 keepAlive 避免关闭后仍有心跳线程
            ds.setKeepAlive(false);

            // 仅启用监控统计，不启用 wall 防火墙
            // 原因：迁移场景需要执行跨库 DDL（含 DBMS_METADATA、EDITIONABLE 等特殊语法），
            //       Druid wall 无法正确解析这些语句，且迁移 SQL 均为系统生成，无注入风险
            ds.setFilters("stat");

            ds.init();
            dataSourceMap.put(config.getId(), ds);
            log.info("数据源注册成功: id={}, name={}, type={}, url={}", config.getId(), config.getName(), config.getDbType(), jdbcUrl);
        } catch (Exception e) {
            log.error("数据源注册失败: id={}, name={}", config.getId(), config.getName(), e);
            throw new RuntimeException("数据源注册失败: " + e.getMessage(), e);
        }
    }

    /**
     * 注销数据源（彻底关闭连接池）
     */
    public void unregister(Long dsId) {
        DruidDataSource ds = dataSourceMap.remove(dsId);
        if (ds != null) {
            try {
                // 先清除所有连接，再关闭池
                ds.setMinIdle(0);
                ds.shrink(true, true);
                ds.close();
                log.info("数据源已注销: id={}", dsId);
            } catch (Exception e) {
                log.warn("关闭数据源连接池异常: id={}", dsId, e);
            }
        }
    }

    /**
     * 获取连接
     */
    public Connection getConnection(Long dsId) throws SQLException {
        DruidDataSource ds = dataSourceMap.get(dsId);
        if (ds == null) {
            throw new IllegalArgumentException("数据源未注册: id=" + dsId);
        }
        return ds.getConnection();
    }

    /**
     * 获取连接并切换到指定 Schema
     *
     * 重要：schema 参数的含义取决于数据库类型和用户配置：
     * - 对于 Oracle：schema 是用户/命名空间，使用配置中的 dbName（服务名）连接，然后 setSchema()
     * - 对于 MySQL：schema 就是数据库名，用 schema 替换 dbName
     * - 对于 PostgreSQL/GaussDB：
     *   - 如果 schema 为 null/空，使用配置中的 dbName
     *   - 否则，使用配置中的 dbName 连接，然后 setSchema() 切换到指定 schema
     */
    public Connection getConnection(Long dsId, String schema) throws SQLException {
        // 如果没有指定 schema，或者 schema 为空，使用默认连接池
        if (schema == null || schema.isBlank()) {
            return getConnection(dsId);
        }

        DataSourceConfig config = dataSourceMapper.selectById(dsId);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: id=" + dsId);
        }

        try {
            DbType dbType = DbType.valueOf(config.getDbType());
            String password = AesUtils.decrypt(config.getPassword());

            // 根据数据库类型决定如何使用 schema 参数
            String dbNameToUse;
            boolean needSetSchema = false;

            switch (dbType) {
                case ORACLE -> {
                    // Oracle: schema 是用户命名空间，使用配置的服务名连接
                    dbNameToUse = config.getDbName();
                    needSetSchema = true;
                }
                case MYSQL, OCEANBASE -> {
                    // MySQL/OceanBase: schema 就是数据库名
                    dbNameToUse = schema;
                    needSetSchema = false;
                }
                case POSTGRESQL, GAUSSDB, DM -> {
                    // PostgreSQL/GaussDB/DM: 使用配置的数据库名连接，然后切换 schema
                    dbNameToUse = config.getDbName();
                    needSetSchema = true;
                }
                default -> {
                    dbNameToUse = config.getDbName();
                    needSetSchema = true;
                }
            }

            String jdbcUrl = dbType.buildUrl(config.getHost(), config.getPort(), dbNameToUse, config.getExtraParams());

            log.info("【连接调试】dsId={}, dbType={}, schema参数={}, config.dbName={}, 实际使用dbName={}, needSetSchema={}, url={}",
                    dsId, dbType, schema, config.getDbName(), dbNameToUse, needSetSchema, jdbcUrl);

            // 创建直连（不使用连接池）
            Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), password);

            // 如果需要，显式设置 schema
            if (needSetSchema) {
                try {
                    conn.setSchema(schema);
                    log.info("【连接调试】已切换到 schema: {}", schema);
                } catch (Exception e) {
                    log.warn("【连接调试】setSchema 失败: {}", e.getMessage());
                }
            }

            return conn;
        } catch (Exception e) {
            log.error("创建指定 schema 的连接失败: dsId={}, schema={}", dsId, schema, e);
            throw new SQLException("创建连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * Ping 检测数据源连通性
     */
    public boolean ping(Long dsId) {
        DruidDataSource ds = dataSourceMap.get(dsId);
        if (ds == null) {
            return false;
        }
        try (Connection conn = ds.getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.warn("数据源 Ping 失败: id={}", dsId, e);
            return false;
        }
    }

    /**
     * 获取所有已注册的数据源 ID
     */
    public Set<Long> getRegisteredIds() {
        return dataSourceMap.keySet();
    }

    /**
     * 判断数据源是否已注册
     */
    public boolean isRegistered(Long dsId) {
        return dataSourceMap.containsKey(dsId);
    }
}
