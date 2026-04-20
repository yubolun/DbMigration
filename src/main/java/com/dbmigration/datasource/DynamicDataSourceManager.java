package com.dbmigration.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.dbmigration.common.AesUtils;
import com.dbmigration.common.DbType;
import com.dbmigration.datasource.entity.DataSourceConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
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
public class DynamicDataSourceManager {

    private final Map<Long, DruidDataSource> dataSourceMap = new ConcurrentHashMap<>();

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
     * 获取连接并切换到指定 Database/Schema
     */
    public Connection getConnection(Long dsId, String schema) throws SQLException {
        Connection conn = getConnection(dsId);
        if (schema != null && !schema.isBlank()) {
            try {
                conn.setCatalog(schema);
            } catch (Exception ignored) {}
            try {
                conn.setSchema(schema);
            } catch (Exception ignored) {}
        }
        return conn;
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
