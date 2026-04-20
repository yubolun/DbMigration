package com.dbmigration.datasource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dbmigration.common.AesUtils;
import com.dbmigration.common.DbType;
import com.dbmigration.datasource.DynamicDataSourceManager;
import com.dbmigration.datasource.entity.DataSourceConfig;
import com.dbmigration.datasource.mapper.DataSourceMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据源管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final DataSourceMapper dataSourceMapper;
    private final DynamicDataSourceManager dataSourceManager;

    /**
     * 应用启动时，加载所有数据源并注册到连接池
     */
    @PostConstruct
    public void initDataSources() {
        List<DataSourceConfig> configs = dataSourceMapper.selectList(
                new LambdaQueryWrapper<DataSourceConfig>().eq(DataSourceConfig::getDeleted, 0)
        );
        log.info("加载 {} 个数据源配置", configs.size());
        for (DataSourceConfig config : configs) {
            try {
                dataSourceManager.register(config);
            } catch (Exception e) {
                log.error("启动时加载数据源失败: id={}, name={}", config.getId(), config.getName(), e);
            }
        }
    }

    /**
     * 查询所有数据源
     */
    public List<DataSourceConfig> listAll() {
        List<DataSourceConfig> list = dataSourceMapper.selectList(
                new LambdaQueryWrapper<DataSourceConfig>().eq(DataSourceConfig::getDeleted, 0)
        );
        // 隐藏密码明文, 返回 *** 占位
        list.forEach(ds -> ds.setPassword("******"));
        return list;
    }

    /**
     * 分页查询数据源
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<DataSourceConfig> listPage(
            int page, int size, String name, String dbType, String username, String dbName) {
        var pageParam = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<DataSourceConfig>(page, size);
        LambdaQueryWrapper<DataSourceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataSourceConfig::getDeleted, 0);
        if (name != null && !name.isBlank()) {
            wrapper.like(DataSourceConfig::getName, name);
        }
        if (dbType != null && !dbType.isBlank()) {
            wrapper.eq(DataSourceConfig::getDbType, dbType);
        }
        if (username != null && !username.isBlank()) {
            wrapper.like(DataSourceConfig::getUsername, username);
        }
        if (dbName != null && !dbName.isBlank()) {
            wrapper.like(DataSourceConfig::getDbName, dbName);
        }
        wrapper.orderByDesc(DataSourceConfig::getUpdateTime);
        var result = dataSourceMapper.selectPage(pageParam, wrapper);
        result.getRecords().forEach(ds -> ds.setPassword("******"));
        return result;
    }

    /**
     * 查询单个数据源
     */
    public DataSourceConfig getById(Long id) {
        DataSourceConfig config = dataSourceMapper.selectById(id);
        if (config != null) {
            config.setPassword("******");
        }
        return config;
    }

    /**
     * 新增数据源
     */
    @Transactional
    public DataSourceConfig create(DataSourceConfig config) {
        // 密码 AES 加密存储
        config.setPassword(AesUtils.encrypt(config.getPassword()));
        config.setStatus(0);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        config.setDeleted(0);
        dataSourceMapper.insert(config);

        // 注册到连接池
        try {
            dataSourceManager.register(config);
            // Ping 检测
            boolean online = dataSourceManager.ping(config.getId());
            config.setStatus(online ? 1 : 2);
            config.setLastPingTime(LocalDateTime.now());
            dataSourceMapper.updateById(config);
        } catch (Exception e) {
            config.setStatus(2);
            config.setLastPingTime(LocalDateTime.now());
            dataSourceMapper.updateById(config);
            log.warn("数据源创建后注册失败: {}", e.getMessage());
        }

        config.setPassword("******");
        return config;
    }

    /**
     * 更新数据源
     */
    @Transactional
    public DataSourceConfig update(Long id, DataSourceConfig config) {
        DataSourceConfig existing = dataSourceMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("数据源不存在: id=" + id);
        }

        existing.setName(config.getName());
        existing.setDbType(config.getDbType());
        existing.setHost(config.getHost());
        existing.setPort(config.getPort());
        existing.setDbName(config.getDbName());
        existing.setUsername(config.getUsername());
        existing.setExtraParams(config.getExtraParams());

        // 如果密码不是占位符，说明用户修改了密码
        if (config.getPassword() != null && !"******".equals(config.getPassword())) {
            existing.setPassword(AesUtils.encrypt(config.getPassword()));
        }

        existing.setUpdateTime(LocalDateTime.now());
        dataSourceMapper.updateById(existing);

        // 重新注册连接池
        try {
            dataSourceManager.register(existing);
            boolean online = dataSourceManager.ping(existing.getId());
            existing.setStatus(online ? 1 : 2);
            existing.setLastPingTime(LocalDateTime.now());
            dataSourceMapper.updateById(existing);
        } catch (Exception e) {
            existing.setStatus(2);
            dataSourceMapper.updateById(existing);
        }

        existing.setPassword("******");
        return existing;
    }

    /**
     * 删除数据源（逻辑删除）
     */
    @Transactional
    public void delete(Long id) {
        dataSourceMapper.deleteById(id);
        dataSourceManager.unregister(id);
    }

    /**
     * Ping 测试数据源
     */
    public boolean ping(Long id) {
        boolean online = dataSourceManager.ping(id);
        // 更新状态
        DataSourceConfig config = new DataSourceConfig();
        config.setId(id);
        config.setStatus(online ? 1 : 2);
        config.setLastPingTime(LocalDateTime.now());
        dataSourceMapper.updateById(config);
        return online;
    }

    /**
     * 测试连接（不保存到数据库，直接用传入配置尝试连接）
     */
    public boolean testConnection(DataSourceConfig config) {
        DbType dbType = DbType.valueOf(config.getDbType());
        String jdbcUrl = dbType.buildUrl(config.getHost(), config.getPort(), config.getDbName(), config.getExtraParams());
        String password = config.getPassword();
        // 如果是明文密码（非占位符），直接使用；否则解密
        if ("******".equals(password) && config.getId() != null) {
            DataSourceConfig existing = dataSourceMapper.selectById(config.getId());
            if (existing != null) {
                password = AesUtils.decrypt(existing.getPassword());
            }
        }
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, config.getUsername(), password)) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.warn("测试连接失败: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}
