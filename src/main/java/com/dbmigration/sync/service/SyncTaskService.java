package com.dbmigration.sync.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbmigration.common.Result;
import com.dbmigration.datasource.DynamicDataSourceManager;
import com.dbmigration.datasource.entity.DataSourceConfig;
import com.dbmigration.datasource.mapper.DataSourceMapper;
import com.dbmigration.dialect.DbDialect;
import com.dbmigration.dialect.DialectFactory;
import com.dbmigration.sync.controller.SyncController.BatchTaskRequest;
import com.dbmigration.sync.entity.FieldMapping;
import com.dbmigration.sync.entity.SyncLog;
import com.dbmigration.sync.entity.SyncTask;
import com.dbmigration.sync.engine.SchemaSyncEngine;
import com.dbmigration.sync.engine.SyncEngine;
import com.dbmigration.sync.mapper.FieldMappingMapper;
import com.dbmigration.sync.mapper.SyncLogMapper;
import com.dbmigration.sync.mapper.SyncTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 同步任务管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTaskService {

    private final SyncTaskMapper syncTaskMapper;
    private final SyncLogMapper syncLogMapper;
    private final FieldMappingMapper fieldMappingMapper;
    private final SyncEngine syncEngine;
    private final SchemaSyncEngine schemaSyncEngine;
    private final ObjectMapper objectMapper;
    private final DataSourceMapper dataSourceMapper;
    private final DynamicDataSourceManager dsManager;
    private final DialectFactory dialectFactory;

    /**
     * 并行同步表的最大并发度（每个数据源需预留对应数量的连接池容量）。
     * 可通过 application.yml 中的 sync.table-concurrency 调整，默认 8。
     */
    @Value("${sync.table-concurrency:8}")
    private int tableConcurrency;

    @Resource(name = "syncExecutor")
    private ExecutorService syncExecutor;

    /** 批量任务停止标志 */
    private final Set<Long> stoppedTaskIds = ConcurrentHashMap.newKeySet();

    /**
     * 查询所有任务
     */
    public List<SyncTask> listAll() {
        return syncTaskMapper.selectList(
                new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getDeleted, 0).orderByDesc(SyncTask::getCreateTime)
        );
    }

    /**
     * 分页查询任务
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<SyncTask> listPage(
            int page, int size, String taskName, String taskType) {
        var pageParam = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<SyncTask>(page, size);
        LambdaQueryWrapper<SyncTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SyncTask::getDeleted, 0);
        if (taskName != null && !taskName.isBlank()) {
            wrapper.like(SyncTask::getTaskName, taskName);
        }
        if (taskType != null && !taskType.isBlank()) {
            wrapper.eq(SyncTask::getTaskType, taskType);
        }
        wrapper.orderByDesc(SyncTask::getUpdateTime);
        return syncTaskMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 查询单个任务（含字段映射）
     */
    public SyncTask getById(Long id) {
        return syncTaskMapper.selectById(id);
    }

    /**
     * 获取任务的字段映射
     */
    public List<FieldMapping> getFieldMappings(Long taskId) {
        return fieldMappingMapper.selectList(
                new LambdaQueryWrapper<FieldMapping>().eq(FieldMapping::getTaskId, taskId)
        );
    }

    /**
     * 创建任务（含字段映射）
     */
    @Transactional
    public SyncTask create(SyncTask task, List<FieldMapping> mappings) {
        task.setStatus("IDLE");
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setDeleted(0);
        if (task.getBatchSize() == null) {
            task.setBatchSize(1000);
        }
        syncTaskMapper.insert(task);

        // 保存字段映射
        if (mappings != null) {
            for (FieldMapping mapping : mappings) {
                mapping.setTaskId(task.getId());
                mapping.setCreateTime(LocalDateTime.now());
                fieldMappingMapper.insert(mapping);
            }
        }
        return task;
    }

    /**
     * 更新任务
     */
    @Transactional
    public SyncTask update(Long id, SyncTask task, List<FieldMapping> mappings) {
        SyncTask existing = syncTaskMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("任务不存在: id=" + id);
        }

        existing.setTaskName(task.getTaskName());
        existing.setSourceDsId(task.getSourceDsId());
        existing.setTargetDsId(task.getTargetDsId());
        existing.setSourceTable(task.getSourceTable());
        existing.setTargetTable(task.getTargetTable());
        existing.setBatchSize(task.getBatchSize());
        existing.setSyncMode(task.getSyncMode());
        existing.setCronExpr(task.getCronExpr());
        existing.setUpdateTime(LocalDateTime.now());
        syncTaskMapper.updateById(existing);

        // 更新字段映射 (先删后插)
        if (mappings != null) {
            fieldMappingMapper.delete(new LambdaQueryWrapper<FieldMapping>().eq(FieldMapping::getTaskId, id));
            for (FieldMapping mapping : mappings) {
                mapping.setId(null);
                mapping.setTaskId(id);
                mapping.setCreateTime(LocalDateTime.now());
                fieldMappingMapper.insert(mapping);
            }
        }

        return existing;
    }

    /**
     * 删除任务
     */
    @Transactional
    public void delete(Long id) {
        syncTaskMapper.deleteById(id);
        fieldMappingMapper.delete(new LambdaQueryWrapper<FieldMapping>().eq(FieldMapping::getTaskId, id));
    }

    /**
     * 立即执行同步任务（异步）
     */
    public void executeAsync(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) return;

        String taskType = task.getTaskType();
        // 批量类型任务(全库/按需/纯数据)需要走批量流程
        if ("FULL_DATA".equals(taskType) || "FULL_SCHEMA".equals(taskType) || "SELECTIVE".equals(taskType) || "DATA_ONLY".equals(taskType)) {
            BatchTaskRequest request = new BatchTaskRequest();
            request.setTaskType(taskType);
            request.setTaskName(task.getTaskName());
            request.setSourceDsId(task.getSourceDsId());
            request.setTargetDsId(task.getTargetDsId());
            request.setSourceSchema(task.getSourceSchema());
            request.setTargetSchema(task.getTargetSchema());
            request.setSchemaStrategy(task.getSchemaStrategy());
            request.setBatchSize(task.getBatchSize());
            request.setSyncMode(task.getSyncMode());
            request.setFunctions(Boolean.TRUE.equals(task.getIncludeFunctions()) ? List.of() : null);
            request.setProcedures(Boolean.TRUE.equals(task.getIncludeProcedures()) ? List.of() : null);
            request.setViews(Boolean.TRUE.equals(task.getIncludeViews()) ? List.of() : null);
            // 解析表列表
            if (task.getTableList() != null && !task.getTableList().isBlank()) {
                request.setTables(getTableListFromTask(task));
            }

            task.setStatus("RUNNING");
            task.setUpdateTime(LocalDateTime.now());
            syncTaskMapper.updateById(task);

            syncExecutor.submit(() -> {
                try {
                    executeBatchTask(taskId, request);
                } catch (Exception e) {
                    log.error("批量任务重新执行失败: taskId={}", taskId, e);
                    task.setStatus("FAILED");
                    task.setUpdateTime(LocalDateTime.now());
                    syncTaskMapper.updateById(task);
                }
            });
        } else {
            // 常规单表任务
            syncExecutor.submit(() -> {
                try {
                    syncEngine.execute(taskId);
                } catch (Exception e) {
                    log.error("异步执行同步任务失败: taskId={}", taskId, e);
                }
            });
        }
    }

    /**
     * 停止运行中的任务
     */
    public void stop(Long taskId) {
        stoppedTaskIds.add(taskId);
        syncEngine.stop(taskId);
        log.info("请求停止任务: taskId={}", taskId);
    }

    private boolean isStopped(Long taskId) {
        return stoppedTaskIds.contains(taskId);
    }

    /**
     * 查询同步日志（分页）- 支持任务名称模糊查询
     */
    public Page<SyncLog> listLogs(int page, int size, Long taskId, String status, String taskName) {
        Page<SyncLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SyncLog> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(SyncLog::getTaskId, taskId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SyncLog::getStatus, status);
        }
        // 任务名称模糊查询: 先查匹配的taskId列表
        if (taskName != null && !taskName.isBlank()) {
            List<Long> matchTaskIds = syncTaskMapper.selectList(
                    new LambdaQueryWrapper<SyncTask>()
                            .like(SyncTask::getTaskName, taskName)
                            .select(SyncTask::getId)
            ).stream().map(SyncTask::getId).toList();
            if (matchTaskIds.isEmpty()) {
                // 没有匹配的任务，返回空页
                return pageParam;
            }
            wrapper.in(SyncLog::getTaskId, matchTaskIds);
        }
        wrapper.orderByDesc(SyncLog::getCreateTime);
        Page<SyncLog> result = syncLogMapper.selectPage(pageParam, wrapper);

        // 填充 taskName
        if (!result.getRecords().isEmpty()) {
            List<Long> taskIds = result.getRecords().stream()
                    .map(SyncLog::getTaskId).distinct().toList();
            List<SyncTask> tasks = syncTaskMapper.selectBatchIds(taskIds);
            var taskMap = tasks.stream().collect(
                    java.util.stream.Collectors.toMap(SyncTask::getId, SyncTask::getTaskName, (a, b) -> a));
            for (SyncLog logItem : result.getRecords()) {
                logItem.setTaskName(taskMap.getOrDefault(logItem.getTaskId(), ""));
            }
        }
        return result;
    }

    /**
     * 获取看板统计数据
     */
    public DashboardStats getDashboardStats() {
        DashboardStats stats = new DashboardStats();

        // 运行中任务数
        stats.runningTasks = syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getStatus, "RUNNING").eq(SyncTask::getDeleted, 0)
        );

        // 总任务数
        stats.totalTasks = syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getDeleted, 0)
        );

        // 今日同步行数
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        List<SyncLog> todayLogs = syncLogMapper.selectList(
                new LambdaQueryWrapper<SyncLog>()
                        .ge(SyncLog::getCreateTime, today)
                        .eq(SyncLog::getStatus, "SUCCESS")
        );
        stats.todaySyncRows = todayLogs.stream().mapToLong(l -> l.getSuccessRows() != null ? l.getSuccessRows() : 0).sum();

        return stats;
    }

    public static class DashboardStats {
        public Long runningTasks;
        public Long totalTasks;
        public long todaySyncRows;
    }

    /**
     * 创建批量任务 (全库数据同步 / 全库结构同步 / 按需同步)
     */
    public Result<Object> createBatchTask(BatchTaskRequest request) {
        // 1. 创建主任务记录
        SyncTask task = new SyncTask();
        task.setTaskName(request.getTaskName());
        task.setTaskType(request.getTaskType());
        task.setSourceDsId(request.getSourceDsId());
        task.setTargetDsId(request.getTargetDsId());
        task.setSourceSchema(request.getSourceSchema());
        task.setTargetSchema(request.getTargetSchema());
        task.setSchemaStrategy(request.getSchemaStrategy() != null ? request.getSchemaStrategy() : "CREATE_IF_NOT_EXISTS");
        task.setIncludeFunctions(request.getFunctions() != null);
        task.setIncludeProcedures(request.getProcedures() != null);
        task.setIncludeViews(request.getViews() != null);
        task.setBatchSize(request.getBatchSize() != null ? request.getBatchSize() : 1000);
        task.setSyncMode(request.getSyncMode() != null ? request.getSyncMode() : "FULL");
        task.setStatus("RUNNING");
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setDeleted(0);

        // 序列化表列表
        if (request.getTables() != null) {
            try {
                task.setTableList(objectMapper.writeValueAsString(request.getTables()));
            } catch (Exception e) {
                throw new RuntimeException("表列表序列化失败", e);
            }
        }
        syncTaskMapper.insert(task);

        // 2. 异步执行
        final Long taskId = task.getId();
        syncExecutor.submit(() -> {
            try {
                executeBatchTask(taskId, request);
            } catch (Exception e) {
                log.error("批量任务执行失败: taskId={}", taskId, e);
                task.setStatus("FAILED");
                task.setUpdateTime(LocalDateTime.now());
                syncTaskMapper.updateById(task);
            }
        });

        return Result.ok("任务已创建并开始执行", task);
    }

    /**
     * 更新批量任务配置（仅更新，不重新执行）
     */
    public Result<SyncTask> updateBatchTask(Long id, BatchTaskRequest request) {
        SyncTask task = syncTaskMapper.selectById(id);
        if (task == null) {
            return Result.fail("任务不存在");
        }
        if ("RUNNING".equals(task.getStatus())) {
            return Result.fail("任务正在运行中，无法编辑");
        }
        task.setTaskName(request.getTaskName());
        task.setTaskType(request.getTaskType());
        task.setSourceDsId(request.getSourceDsId());
        task.setTargetDsId(request.getTargetDsId());
        task.setSourceSchema(request.getSourceSchema());
        task.setTargetSchema(request.getTargetSchema());
        task.setSchemaStrategy(request.getSchemaStrategy() != null ? request.getSchemaStrategy() : "CREATE_IF_NOT_EXISTS");
        task.setIncludeFunctions(request.getFunctions() != null);
        task.setIncludeProcedures(request.getProcedures() != null);
        task.setIncludeViews(request.getViews() != null);
        task.setBatchSize(request.getBatchSize() != null ? request.getBatchSize() : 5000);
        task.setSyncMode(request.getSyncMode() != null ? request.getSyncMode() : "FULL");
        task.setStatus("IDLE");
        task.setUpdateTime(LocalDateTime.now());
        if (request.getTables() != null) {
            try {
                task.setTableList(objectMapper.writeValueAsString(request.getTables()));
            } catch (Exception e) {
                throw new RuntimeException("表列表序列化失败", e);
            }
        } else {
            task.setTableList(null);
        }
        syncTaskMapper.updateById(task);
        return Result.ok("任务已更新", task);
    }

    private void executeBatchTask(Long taskId, BatchTaskRequest request) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        String taskType = request.getTaskType();
        LocalDateTime syncStartTime = LocalDateTime.now();

        try {
            switch (taskType) {
                case "FULL_SCHEMA" -> {
                    // 仅同步表结构 + 函数 + 存储过程
                    SchemaSyncEngine.SyncResult result = schemaSyncEngine.syncSchema(
                            request.getSourceDsId(), request.getTargetDsId(),
                            request.getTables(), request.getFunctions(), request.getProcedures(),
                            request.getSchemaStrategy(), taskId, request.getSourceSchema(), request.getTargetSchema(),
                            () -> isStopped(taskId), request.getViews());
                    log.info("全库结构同步完成: tables={}/{}, functions={}/{}, procedures={}/{}, errors={}",
                            result.successTables, result.totalTables,
                            result.successFunctions, result.totalFunctions,
                            result.successProcedures, result.totalProcedures,
                            result.errors.size());
                    boolean schemaHasErrors = !result.errors.isEmpty();
                    task.setStatus(schemaHasErrors ? "FAILED" : "SUCCESS");

                    // 写入日志
                    SyncLog schemaLog = new SyncLog();
                    schemaLog.setTaskId(taskId);
                    schemaLog.setStartTime(syncStartTime);
                    schemaLog.setEndTime(LocalDateTime.now());
                    schemaLog.setTotalRows((long) result.totalTables + result.totalFunctions + result.totalProcedures);
                    schemaLog.setSuccessRows((long) result.successTables + result.successFunctions + result.successProcedures);
                    schemaLog.setFailedRows(schemaLog.getTotalRows() - schemaLog.getSuccessRows());
                    schemaLog.setStatus(schemaHasErrors ? "FAILED" : "SUCCESS");
                    if (schemaHasErrors) {
                        String errMsg = String.join("\n", result.errors);
                        schemaLog.setErrorMsg(errMsg.length() > 2000 ? errMsg.substring(0, 2000) : errMsg);
                    }
                    Duration schemaDur = Duration.between(schemaLog.getStartTime(), schemaLog.getEndTime());
                    double schemaSecs = schemaDur.toMillis() / 1000.0;
                    schemaLog.setQps(schemaSecs > 0 ? schemaLog.getSuccessRows() / schemaSecs : 0);
                    schemaLog.setCreateTime(LocalDateTime.now());
                    syncLogMapper.insert(schemaLog);
                }

                case "FULL_DATA" -> {
                    // 先同步结构
                    SchemaSyncEngine.SyncResult schemaResult = schemaSyncEngine.syncSchema(
                            request.getSourceDsId(), request.getTargetDsId(),
                            request.getTables(), request.getFunctions(), request.getProcedures(),
                            request.getSchemaStrategy(), taskId, request.getSourceSchema(), request.getTargetSchema(),
                            () -> isStopped(taskId), request.getViews());
                    log.info("结构同步阶段完成: 成功={}/{}", schemaResult.successTables, schemaResult.totalTables);

                    // 并行同步数据（Virtual Threads）
                    List<String> tablesToSync = resolveTableList(request);
                    log.info("全库数据同步开始: {} 张表, 并发度={}", tablesToSync.size(), tableConcurrency);
                    TableSyncResult dataResult = parallelSyncTables(tablesToSync, request, taskId);
                    log.info("全库数据同步完成: 表 {}/{}, 数据行 {}/{}",
                            dataResult.successTables(), tablesToSync.size(),
                            dataResult.successRows(), dataResult.totalRows());

                    boolean hasErrors = !schemaResult.errors.isEmpty()
                            || dataResult.failedTables() > 0 || dataResult.failedRows() > 0;
                    task.setStatus(hasErrors ? "FAILED" : "SUCCESS");
                    syncLogMapper.insert(buildSyncLog(taskId, syncStartTime, dataResult, schemaResult.errors));
                }

                case "SELECTIVE" -> {
                    // 先同步结构
                    SchemaSyncEngine.SyncResult schemaResult = schemaSyncEngine.syncSchema(
                            request.getSourceDsId(), request.getTargetDsId(),
                            request.getTables(), request.getFunctions(), request.getProcedures(),
                            request.getSchemaStrategy(), taskId, request.getSourceSchema(), request.getTargetSchema(),
                            () -> isStopped(taskId), request.getViews());

                    // 并行同步选中表的数据（Virtual Threads）
                    List<String> tablesToSync = request.getTables() != null ? request.getTables() : List.of();
                    log.info("按需数据同步开始: {} 张表, 并发度={}", tablesToSync.size(), tableConcurrency);
                    TableSyncResult dataResult = parallelSyncTables(tablesToSync, request, taskId);
                    log.info("按需数据同步完成: 表 {}/{}, 数据行 {}/{}",
                            dataResult.successTables(), tablesToSync.size(),
                            dataResult.successRows(), dataResult.totalRows());

                    boolean selHasErrors = !schemaResult.errors.isEmpty()
                            || dataResult.failedTables() > 0 || dataResult.failedRows() > 0;
                    task.setStatus(selHasErrors ? "FAILED" : "SUCCESS");
                    syncLogMapper.insert(buildSyncLog(taskId, syncStartTime, dataResult, schemaResult.errors));
                }

                case "DATA_ONLY" -> {
                    // 纯数据同步：跳过结构同步，直接并行同步数据（Virtual Threads）
                    List<String> tablesToSync = resolveTableList(request);
                    log.info("纯数据同步开始: {} 张表, 模式={}, 并发度={}",
                            tablesToSync.size(), request.getSyncMode(), tableConcurrency);
                    TableSyncResult dataResult = parallelSyncTables(tablesToSync, request, taskId);
                    log.info("纯数据同步完成: 表 {}/{}, 数据行 {}/{}",
                            dataResult.successTables(), tablesToSync.size(),
                            dataResult.successRows(), dataResult.totalRows());

                    boolean hasErrors = dataResult.failedTables() > 0 || dataResult.failedRows() > 0;
                    task.setStatus(hasErrors ? "FAILED" : "SUCCESS");
                    syncLogMapper.insert(buildSyncLog(taskId, syncStartTime, dataResult, List.of()));
                }

                default -> {
                    task.setStatus("FAILED");
                    log.error("未知任务类型: {}", taskType);
                }
            }
        } catch (Exception e) {
            task.setStatus("FAILED");
            log.error("批量任务执行异常", e);
        } finally {
            // 检查是否是用户主动停止
            if (isStopped(taskId)) {
                task.setStatus("STOPPED");
            }
            stoppedTaskIds.remove(taskId);
            task.setLastSyncTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            syncTaskMapper.updateById(task);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Virtual Threads 并行同步辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    /** 并行同步结果封装（Java 16+ Record）*/
    private record TableSyncResult(
            int successTables, int failedTables,
            long totalRows, long successRows, long failedRows,
            String errorSummary) {}

    /**
     * 并行同步多张表 — 基于 JDK 21 Virtual Threads。
     * <p>
     * 每张表分配一个虚拟线程（极轻量，10 万级别无压力），通过 {@link Semaphore}
     * 限制同时持有数据库连接的线程数，避免连接池耗尽。
     * {@link ExecutorService#close()} 是 JDK 21 新增的 AutoCloseable 支持，
     * 会自动等待全部已提交任务完成后再退出，无需手动 awaitTermination。
     */
    private TableSyncResult parallelSyncTables(List<String> tables,
                                               BatchTaskRequest request, Long taskId) {
        if (tables.isEmpty()) {
            return new TableSyncResult(0, 0, 0L, 0L, 0L, "");
        }

        AtomicInteger successCnt  = new AtomicInteger();
        AtomicInteger failedCnt   = new AtomicInteger();
        AtomicLong    totalRows   = new AtomicLong();
        AtomicLong    successRows = new AtomicLong();
        AtomicLong    failedRows  = new AtomicLong();
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        Semaphore semaphore = new Semaphore(tableConcurrency);

        try (ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(tables.size());

            for (String tableName : tables) {
                if (isStopped(taskId)) {
                    log.info("任务已停止，跳过剩余 {} 张表: taskId={}",
                            tables.size() - futures.size(), taskId);
                    break;
                }
                futures.add(vt.submit(() -> {
                    // acquire 保证同时持有连接的线程不超过 tableConcurrency
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        failedCnt.incrementAndGet();
                        errors.add(tableName + ": 线程被中断");
                        return;
                    }
                    try {
                        SyncEngine.InlineResult r = syncEngine.executeInline(buildSubTask(tableName, request));
                        totalRows.addAndGet(r.getTotalRows());
                        successRows.addAndGet(r.getSuccessRows());
                        failedRows.addAndGet(r.getFailedRows());
                        if (r.getErrorMsg() != null) {
                            failedCnt.incrementAndGet();
                            errors.add(tableName + ": " + r.getErrorMsg());
                            log.warn("表 {} 同步失败: {}", tableName, r.getErrorMsg());
                        } else {
                            successCnt.incrementAndGet();
                            log.info("表 {} 同步完成: {}/{} 行",
                                    tableName, r.getSuccessRows(), r.getTotalRows());
                        }
                    } catch (Exception e) {
                        failedCnt.incrementAndGet();
                        errors.add(tableName + ": " + e.getMessage());
                        log.error("表 {} 同步异常", tableName, e);
                    } finally {
                        semaphore.release();
                    }
                }));
            }

            // 等待全部虚拟线程完成（单表失败不阻断其他表）
            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
        }

        return new TableSyncResult(
                successCnt.get(), failedCnt.get(),
                totalRows.get(), successRows.get(), failedRows.get(),
                String.join("\n", errors));
    }

    /**
     * 构建内存子任务（不写入数据库，用于 executeInline 调用）
     */
    private SyncTask buildSubTask(String tableName, BatchTaskRequest request) {
        SyncTask subTask = new SyncTask();
        subTask.setSourceDsId(request.getSourceDsId());
        subTask.setTargetDsId(request.getTargetDsId());
        subTask.setSourceTable(tableName);
        subTask.setTargetTable(tableName);
        subTask.setSourceSchema(request.getSourceSchema());
        subTask.setTargetSchema(request.getTargetSchema());
        subTask.setBatchSize(request.getBatchSize() != null ? request.getBatchSize() : 5000);
        subTask.setSyncMode(request.getSyncMode() != null ? request.getSyncMode() : "FULL");
        return subTask;
    }

    /**
     * 解析本次同步的表列表：请求未指定时从源库查询全部表
     */
    private List<String> resolveTableList(BatchTaskRequest request) {
        if (request.getTables() != null && !request.getTables().isEmpty()) {
            return request.getTables();
        }
        try {
            DataSourceConfig srcConfig = dataSourceMapper.selectById(request.getSourceDsId());
            DbDialect srcDialect = dialectFactory.getDialect(srcConfig.getDbType());
            try (java.sql.Connection srcConn = dsManager.getConnection(request.getSourceDsId(), request.getSourceSchema())) {
                List<String> tables = srcDialect.listTables(srcConn, request.getSourceSchema())
                        .stream().map(t -> t.getTableName()).toList();
                log.info("从源库获取到 {} 张表", tables.size());
                return tables;
            }
        } catch (Exception e) {
            log.error("获取源库表列表失败", e);
            return List.of();
        }
    }

    /**
     * 构建并持久化同步日志记录（抽取公共逻辑，消除三处重复代码）
     */
    private SyncLog buildSyncLog(Long taskId, LocalDateTime startTime,
                                  TableSyncResult dataResult, List<String> schemaErrors) {
        SyncLog syncLog = new SyncLog();
        syncLog.setTaskId(taskId);
        syncLog.setStartTime(startTime);
        syncLog.setEndTime(LocalDateTime.now());
        syncLog.setTotalRows(dataResult.totalRows());
        syncLog.setSuccessRows(dataResult.successRows());
        syncLog.setFailedRows(dataResult.failedRows());

        boolean hasErrors = !schemaErrors.isEmpty() || dataResult.failedTables() > 0 || dataResult.failedRows() > 0;
        syncLog.setStatus(hasErrors ? "FAILED" : "SUCCESS");

        if (hasErrors) {
            StringBuilder errMsg = new StringBuilder();
            if (!schemaErrors.isEmpty())
                errMsg.append("结构错误: ").append(String.join("; ", schemaErrors)).append("\n");
            if (!dataResult.errorSummary().isBlank())
                errMsg.append("数据错误: ").append(dataResult.errorSummary());
            String msg = errMsg.toString();
            syncLog.setErrorMsg(msg.length() > 2000 ? msg.substring(0, 2000) : msg);
        }

        Duration dur = Duration.between(startTime, syncLog.getEndTime());
        double secs = dur.toMillis() / 1000.0;
        syncLog.setQps(secs > 0 ? dataResult.successRows() / secs : 0);
        syncLog.setCreateTime(LocalDateTime.now());
        return syncLog;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<String> getTableListFromTask(SyncTask task) {
        if (task.getTableList() == null || task.getTableList().isBlank()) return List.of();
        try {
            return objectMapper.readValue(task.getTableList(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            // 如果历史数据使用了逗号分隔，做最后兜底读取，避免老数据报错
            if (!task.getTableList().trim().startsWith("[")) {
                return List.of(task.getTableList().split(","));
            }
            throw new RuntimeException("解析表列表失败", e);
        }
    }
}
