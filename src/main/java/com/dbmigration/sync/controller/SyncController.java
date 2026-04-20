package com.dbmigration.sync.controller;

import com.dbmigration.common.Result;
import com.dbmigration.sync.entity.FieldMapping;
import com.dbmigration.sync.entity.SyncTask;
import com.dbmigration.sync.service.SyncTaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 同步任务 REST API
 */
@RestController
@RequestMapping("/api/sync/tasks")
@RequiredArgsConstructor
public class SyncController {

    private final SyncTaskService syncTaskService;

    @GetMapping
    public Result<List<SyncTask>> list() {
        return Result.ok(syncTaskService.listAll());
    }

    @GetMapping("/page")
    public Result<?> listPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String taskType) {
        return Result.ok(syncTaskService.listPage(page, size, taskName, taskType));
    }

    @GetMapping("/{id}")
    public Result<SyncTask> getById(@PathVariable Long id) {
        return Result.ok(syncTaskService.getById(id));
    }

    @GetMapping("/{id}/mappings")
    public Result<List<FieldMapping>> getMappings(@PathVariable Long id) {
        return Result.ok(syncTaskService.getFieldMappings(id));
    }

    @PostMapping
    public Result<SyncTask> create(@RequestBody TaskCreateRequest request) {
        SyncTask task = syncTaskService.create(request.getTask(), request.getMappings());
        return Result.ok(task);
    }

    @PutMapping("/{id}")
    public Result<SyncTask> update(@PathVariable Long id, @RequestBody TaskCreateRequest request) {
        SyncTask task = syncTaskService.update(id, request.getTask(), request.getMappings());
        return Result.ok(task);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        syncTaskService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id) {
        syncTaskService.executeAsync(id);
        return Result.ok("任务已提交执行", null);
    }

    @PostMapping("/{id}/stop")
    public Result<Void> stop(@PathVariable Long id) {
        syncTaskService.stop(id);
        return Result.ok("已发送停止请求", null);
    }

    /**
     * 批量创建并执行任务(全库/按需模式)
     */
    @PostMapping("/batch")
    public Result<Object> createBatch(@RequestBody BatchTaskRequest request) {
        return syncTaskService.createBatchTask(request);
    }

    /**
     * 更新批量任务配置
     */
    @PutMapping("/batch/{id}")
    public Result<SyncTask> updateBatch(@PathVariable Long id, @RequestBody BatchTaskRequest request) {
        return syncTaskService.updateBatchTask(id, request);
    }

    @Data
    public static class TaskCreateRequest {
        private SyncTask task;
        private List<FieldMapping> mappings;
    }

    @Data
    public static class BatchTaskRequest {
        /** 任务类型: FULL_DATA / FULL_SCHEMA / SELECTIVE */
        private String taskType;
        /** 任务名称 */
        private String taskName;
        /** 源数据源ID */
        private Long sourceDsId;
        /** 目标数据源ID */
        private Long targetDsId;
        /** 要同步的表名列表 (null=全部) */
        private List<String> tables;
        /** 要同步的函数名列表 (null=不同步, []=全部) */
        private List<String> functions;
        /** 要同步的存储过程名列表 (null=不同步, []=全部) */
        private List<String> procedures;
        /** 要同步的视图名列表 (null=不同步, []=全部) */
        private List<String> views;
        /** 结构策略: CREATE_IF_NOT_EXISTS / DROP_AND_CREATE */
        private String schemaStrategy;
        /** 批量大小 */
        private Integer batchSize;
        /** 同步模式: FULL / INCREMENTAL */
        private String syncMode;
        /** 源 Schema/数据库名 */
        private String sourceSchema;
        /** 目标 Schema/数据库名 */
        private String targetSchema;
    }
}
