package com.dbmigration.datasource.controller;

import com.dbmigration.common.Result;
import com.dbmigration.datasource.entity.DataSourceConfig;
import com.dbmigration.datasource.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理 REST API
 */
@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;

    /**
     * 查询所有数据源
     */
    @GetMapping
    public Result<List<DataSourceConfig>> list() {
        return Result.ok(dataSourceService.listAll());
    }

    /**
     * 分页查询数据源
     */
    @GetMapping("/page")
    public Result<?> listPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dbType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String dbName) {
        return Result.ok(dataSourceService.listPage(page, size, name, dbType, username, dbName));
    }

    /**
     * 查询单个数据源
     */
    @GetMapping("/{id}")
    public Result<DataSourceConfig> getById(@PathVariable Long id) {
        DataSourceConfig config = dataSourceService.getById(id);
        if (config == null) {
            return Result.fail(404, "数据源不存在");
        }
        return Result.ok(config);
    }

    /**
     * 新增数据源
     */
    @PostMapping
    public Result<DataSourceConfig> create(@RequestBody DataSourceConfig config) {
        return Result.ok(dataSourceService.create(config));
    }

    /**
     * 更新数据源
     */
    @PutMapping("/{id}")
    public Result<DataSourceConfig> update(@PathVariable Long id, @RequestBody DataSourceConfig config) {
        return Result.ok(dataSourceService.update(id, config));
    }

    /**
     * 删除数据源
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return Result.ok();
    }

    /**
     * Ping 测试连接
     */
    @PostMapping("/{id}/ping")
    public Result<Boolean> ping(@PathVariable Long id) {
        boolean online = dataSourceService.ping(id);
        return Result.ok(online ? "连接成功" : "连接失败", online);
    }

    /**
     * 测试连接（不需要保存，直接用传入的配置测试）
     */
    @PostMapping("/test")
    public Result<Boolean> testConnection(@RequestBody DataSourceConfig config) {
        try {
            boolean ok = dataSourceService.testConnection(config);
            return Result.ok(ok ? "连接成功" : "连接失败", ok);
        } catch (Exception e) {
            return Result.ok("连接失败: " + e.getMessage(), false);
        }
    }
}
