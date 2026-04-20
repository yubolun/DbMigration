package com.dbmigration.sync.engine;

import com.dbmigration.common.DbType;
import com.dbmigration.datasource.DynamicDataSourceManager;
import com.dbmigration.datasource.entity.DataSourceConfig;
import com.dbmigration.datasource.mapper.DataSourceMapper;
import com.dbmigration.dialect.DbDialect;
import com.dbmigration.dialect.DialectFactory;
import com.dbmigration.sync.entity.FieldMapping;
import com.dbmigration.sync.entity.SyncLog;
import com.dbmigration.sync.entity.SyncTask;
import com.dbmigration.sync.mapper.FieldMappingMapper;
import com.dbmigration.sync.mapper.SyncLogMapper;
import com.dbmigration.sync.mapper.SyncTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 同步引擎核心
 * 负责流式读取源库数据 -> 批量写入目标库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncEngine {

    private final DataSourceMapper dataSourceMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncLogMapper syncLogMapper;
    private final FieldMappingMapper fieldMappingMapper;
    private final DynamicDataSourceManager dsManager;
    private final DialectFactory dialectFactory;
    private final SimpMessagingTemplate messagingTemplate;

    /** 运行中任务的停止标志 */
    private final Map<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    /**
     * 执行同步任务
     */
    public void execute(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: id=" + taskId);
        }
        if ("RUNNING".equals(task.getStatus())) {
            throw new IllegalStateException("任务正在运行中: id=" + taskId);
        }

        // 设置停止标志
        AtomicBoolean running = new AtomicBoolean(true);
        runningFlags.put(taskId, running);

        // 创建日志记录
        SyncLog syncLog = new SyncLog();
        syncLog.setTaskId(taskId);
        syncLog.setStartTime(LocalDateTime.now());
        syncLog.setStatus("RUNNING");
        syncLog.setTotalRows(0L);
        syncLog.setSuccessRows(0L);
        syncLog.setFailedRows(0L);
        syncLog.setCreateTime(LocalDateTime.now());
        syncLogMapper.insert(syncLog);

        // 更新任务状态
        task.setStatus("RUNNING");
        syncTaskMapper.updateById(task);

        try {
            doSync(task, syncLog, running);

            // 同步完成
            syncLog.setEndTime(LocalDateTime.now());
            syncLog.setStatus("SUCCESS");
            Duration duration = Duration.between(syncLog.getStartTime(), syncLog.getEndTime());
            double seconds = duration.toMillis() / 1000.0;
            syncLog.setQps(seconds > 0 ? syncLog.getSuccessRows() / seconds : 0);
            syncLogMapper.updateById(syncLog);

            task.setStatus("SUCCESS");
            task.setLastSyncTime(LocalDateTime.now());
            syncTaskMapper.updateById(task);

            // 推送最终状态
            pushProgress(taskId, syncLog);
            log.info("同步任务完成: taskId={}, successRows={}, elapsed={}s", taskId, syncLog.getSuccessRows(), seconds);

        } catch (Exception e) {
            log.error("同步任务失败: taskId={}", taskId, e);
            syncLog.setEndTime(LocalDateTime.now());
            syncLog.setStatus("FAILED");
            syncLog.setErrorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 2000)) : "Unknown error");
            syncLogMapper.updateById(syncLog);

            task.setStatus("FAILED");
            syncTaskMapper.updateById(task);

            pushProgress(taskId, syncLog);
        } finally {
            runningFlags.remove(taskId);
        }
    }

    /**
     * 内联执行同步（不持久化任务记录，直接返回结果）
     * 用于全库同步时的子表数据同步，避免在 sync_task 表中产生垃圾数据
     */
    @Data
    public static class InlineResult {
        private long totalRows;
        private long successRows;
        private long failedRows;
        private String errorMsg;
    }

    public InlineResult executeInline(SyncTask task) {
        InlineResult result = new InlineResult();
        AtomicBoolean running = new AtomicBoolean(true);

        // 创建内存中的 SyncLog（不写入数据库）
        SyncLog syncLog = new SyncLog();
        syncLog.setTaskId(0L); // 不关联任何持久化任务
        syncLog.setStartTime(LocalDateTime.now());
        syncLog.setStatus("RUNNING");
        syncLog.setTotalRows(0L);
        syncLog.setSuccessRows(0L);
        syncLog.setFailedRows(0L);

        try {
            doSync(task, syncLog, running);
            result.setTotalRows(syncLog.getTotalRows() != null ? syncLog.getTotalRows() : 0);
            result.setSuccessRows(syncLog.getSuccessRows() != null ? syncLog.getSuccessRows() : 0);
            result.setFailedRows(syncLog.getFailedRows() != null ? syncLog.getFailedRows() : 0);
            // 即使没有外部异常，批量写入可能部分失败
            if (result.getFailedRows() > 0) {
                result.setErrorMsg("部分行写入失败: " + result.getFailedRows() + "/" + result.getTotalRows());
            }
        } catch (Exception e) {
            result.setTotalRows(syncLog.getTotalRows() != null ? syncLog.getTotalRows() : 0);
            result.setSuccessRows(syncLog.getSuccessRows() != null ? syncLog.getSuccessRows() : 0);
            result.setFailedRows(syncLog.getFailedRows() != null ? syncLog.getFailedRows() : 0);
            result.setErrorMsg(e.getMessage());
            log.error("内联数据同步失败: table={}", task.getSourceTable(), e);
        }
        return result;
    }

    /**
     * 停止同步任务
     */
    public void stop(Long taskId) {
        AtomicBoolean running = runningFlags.get(taskId);
        if (running != null) {
            running.set(false);
            log.info("请求停止同步任务: taskId={}", taskId);
        }
    }

    /**
     * 获取所有运行中任务的进度
     */
    public Map<Long, AtomicBoolean> getRunningTasks() {
        return runningFlags;
    }

    /**
     * 核心同步流程
     */
    private void doSync(SyncTask task, SyncLog syncLog, AtomicBoolean running) throws Exception {
        // 获取数据源配置
        DataSourceConfig sourceConfig = dataSourceMapper.selectById(task.getSourceDsId());
        DataSourceConfig targetConfig = dataSourceMapper.selectById(task.getTargetDsId());
        if (sourceConfig == null || targetConfig == null) {
            throw new IllegalStateException("源或目标数据源配置不存在");
        }

        DbDialect sourceDialect = dialectFactory.getDialect(sourceConfig.getDbType());
        DbDialect targetDialect = dialectFactory.getDialect(targetConfig.getDbType());

        // 目标表名做大小写规范化（与建表阶段一致）
        // Oracle 源表名全大写, 但 GaussDB/PostgreSQL 建表时已转为小写
        String targetTable = normalizeTargetTable(task.getTargetTable(), targetConfig.getDbType());
        String sourceSchema = task.getSourceSchema();
        String targetSchema = normalizeTargetTable(task.getTargetSchema(), targetConfig.getDbType());

        // 获取字段映射（inline 模式无 task ID，跳过数据库查询）
        List<FieldMapping> mappings = (task.getId() != null) ?
                fieldMappingMapper.selectList(
                        new LambdaQueryWrapper<FieldMapping>().eq(FieldMapping::getTaskId, task.getId())
                ) : new java.util.ArrayList<>();

        // 如果没有手动配置映射, 则自动按同名匹配生成
        if (mappings.isEmpty()) {
            log.info("任务 {} 未配置字段映射, 自动按同名列匹配...", task.getId());
            try (Connection srcConn = dsManager.getConnection(task.getSourceDsId(), sourceSchema);
                 Connection tgtConn = dsManager.getConnection(task.getTargetDsId(), targetSchema)) {
                var srcCols = sourceDialect.listColumns(srcConn, sourceSchema, task.getSourceTable());
                var tgtCols = targetDialect.listColumns(tgtConn, targetSchema, targetTable);

                for (var sc : srcCols) {
                    var tc = tgtCols.stream()
                            .filter(c -> c.getColumnName().equalsIgnoreCase(sc.getColumnName()))
                            .findFirst().orElse(null);
                    if (tc != null) {
                        FieldMapping fm = new FieldMapping();
                        fm.setTaskId(task.getId());
                        fm.setSourceColumn(sc.getColumnName());
                        fm.setSourceType(sc.getFullTypeName());
                        fm.setTargetColumn(tc.getColumnName());
                        fm.setTargetType(tc.getFullTypeName());
                        fm.setCreateTime(java.time.LocalDateTime.now());
                        if (task.getId() != null) fieldMappingMapper.insert(fm);
                        mappings.add(fm);
                    }
                }
                log.info("自动映射完成: {} 个字段", mappings.size());
            }
            if (mappings.isEmpty()) {
                throw new IllegalStateException("源表和目标表没有同名字段, 无法自动映射");
            }
        }

        List<String> sourceColumns = mappings.stream().map(FieldMapping::getSourceColumn).collect(Collectors.toList());
        // 目标列名也需要规范化大小写（与建表阶段一致）
        List<String> targetColumns = mappings.stream()
                .map(m -> normalizeTargetTable(m.getTargetColumn(), targetConfig.getDbType()))
                .collect(Collectors.toList());

        int batchSize = task.getBatchSize() != null ? task.getBatchSize() : 1000;

        // 先获取总行数
        try (Connection sourceConn = dsManager.getConnection(task.getSourceDsId(), sourceSchema)) {
            String countSql = sourceDialect.buildCountSql(sourceSchema, task.getSourceTable());
            try (PreparedStatement countPs = sourceConn.prepareStatement(countSql);
                 ResultSet countRs = countPs.executeQuery()) {
                if (countRs.next()) {
                    syncLog.setTotalRows(countRs.getLong(1));
                    if (syncLog.getId() != null) syncLogMapper.updateById(syncLog);
                }
            }
        }

        // 流式读取 + 批量写入
        Connection sourceConn = null;
        Connection targetConn = null;

        try {
            sourceConn = dsManager.getConnection(task.getSourceDsId(), sourceSchema);
            targetConn = dsManager.getConnection(task.getTargetDsId(), targetSchema);
            targetConn.setAutoCommit(false);

            // 性能优化: 批量插入时关闭约束检查
            DbType targetDbType = DbType.valueOf(targetConfig.getDbType());
            try (Statement optStmt = targetConn.createStatement()) {
                switch (targetDbType) {
                    case MYSQL, OCEANBASE -> {
                        optStmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                        optStmt.execute("SET UNIQUE_CHECKS = 0");
                    }
                    case POSTGRESQL, GAUSSDB -> {
                        optStmt.execute("SET session_replication_role = 'replica'");
                    }
                    default -> { /* DM/Oracle: 默认即可 */ }
                }
            } catch (Exception e) {
                log.debug("设置优化参数忽略: {}", e.getMessage());
            }

            // 根据同步模式选择写入策略
            String writeSql;
            if ("INCREMENTAL".equalsIgnoreCase(task.getSyncMode())) {
                // 增量模式: 尝试检测主键并用 UPSERT
                List<String> pkColumns = new java.util.ArrayList<>();
                try (Connection metaConn = dsManager.getConnection(task.getTargetDsId(), targetSchema)) {
                    var cols = targetDialect.listColumns(metaConn, targetSchema, targetTable);
                    for (var col : cols) {
                        if (Boolean.TRUE.equals(col.getPrimaryKey())) {
                            for (int i = 0; i < targetColumns.size(); i++) {
                                if (targetColumns.get(i).equalsIgnoreCase(col.getColumnName())) {
                                    pkColumns.add(targetColumns.get(i));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("检测主键失败: {}", e.getMessage());
                }

                if (!pkColumns.isEmpty()) {
                    // 有主键: 使用 UPSERT（存在则更新，不存在则插入）
                    writeSql = targetDialect.buildUpsertSql(targetSchema, targetTable, targetColumns, pkColumns);
                    log.info("增量同步使用 UPSERT, 主键列: {}", pkColumns);
                } else {
                    // 无主键: 先清空目标表再全量插入
                    log.warn("增量同步未找到主键, 使用 DELETE + INSERT 策略: {}", targetTable);
                    try (Statement delStmt = targetConn.createStatement()) {
                        delStmt.execute("DELETE FROM " + (targetSchema != null ? targetDialect.quoteIdentifier(targetSchema) + "." : "") + targetDialect.quoteIdentifier(targetTable));
                        targetConn.commit();
                    }
                    writeSql = targetDialect.buildBatchInsertSql(targetSchema, targetTable, targetColumns);
                }
            } else {
                // FULL 全量模式: 先清空目标表, 避免主键冲突
                String fullTableRef = (targetSchema != null && !targetSchema.isBlank())
                        ? targetDialect.quoteIdentifier(targetSchema) + "." + targetDialect.quoteIdentifier(targetTable)
                        : targetDialect.quoteIdentifier(targetTable);
                try (Statement truncStmt = targetConn.createStatement()) {
                    truncStmt.execute("TRUNCATE TABLE " + fullTableRef);
                    log.info("全量同步: 已清空目标表 {}", fullTableRef);
                } catch (Exception e) {
                    // TRUNCATE 失败时用 DELETE 兜底
                    log.warn("TRUNCATE 失败, 尝试 DELETE: {}", e.getMessage());
                    try (Statement delStmt = targetConn.createStatement()) {
                        delStmt.execute("DELETE FROM " + fullTableRef);
                        targetConn.commit();
                    }
                }
                writeSql = targetDialect.buildBatchInsertSql(targetSchema, targetTable, targetColumns);
            }
            log.info("写入SQL [{}]: {}", targetTable, writeSql);

            try (PreparedStatement readPs = sourceDialect.prepareStreamQuery(sourceConn, sourceSchema, task.getSourceTable(), sourceColumns);
                 ResultSet rs = readPs.executeQuery();
                 PreparedStatement writePs = targetConn.prepareStatement(writeSql)) {

                int count = 0;
                long successRows = 0;
                long failedRows = 0;
                long lastPushTime = System.currentTimeMillis();

                while (rs.next() && running.get()) {
                    // 读取一行数据并设置到写入 PreparedStatement
                    for (int i = 0; i < targetColumns.size(); i++) {
                        // convertValue: 将 Oracle 私有类型转换为标准 JDBC 类型，防止写入 MySQL/GaussDB 时报 Data truncation
                        Object value = convertValue(rs.getObject(i + 1));
                        writePs.setObject(i + 1, value);
                    }
                    writePs.addBatch();
                    count++;

                    // 达到批量大小就执行
                    if (count >= batchSize) {
                        try {
                            int[] results = writePs.executeBatch();
                            targetConn.commit();
                            for (int r : results) {
                                if (r >= 0 || r == Statement.SUCCESS_NO_INFO) successRows++;
                                else failedRows++;
                            }
                        } catch (BatchUpdateException e) {
                            targetConn.rollback();
                            failedRows += count;
                            log.warn("批量写入部分失败: {}", e.getMessage());
                        }
                        writePs.clearBatch();
                        count = 0;

                        // 定时推送进度 (每秒最多一次)
                        long now = System.currentTimeMillis();
                        if (now - lastPushTime >= 1000) {
                            syncLog.setSuccessRows(successRows);
                            syncLog.setFailedRows(failedRows);
                            Duration elapsed = Duration.between(syncLog.getStartTime(), LocalDateTime.now());
                            double secs = elapsed.toMillis() / 1000.0;
                            syncLog.setQps(secs > 0 ? successRows / secs : 0);
                            if (syncLog.getId() != null) syncLogMapper.updateById(syncLog);
                            if (task.getId() != null) pushProgress(task.getId(), syncLog);
                            lastPushTime = now;
                        }
                    }
                }

                // 处理剩余批次
                if (count > 0 && running.get()) {
                    try {
                        int[] results = writePs.executeBatch();
                        targetConn.commit();
                        for (int r : results) {
                            if (r >= 0 || r == Statement.SUCCESS_NO_INFO) successRows++;
                            else failedRows++;
                        }
                    } catch (BatchUpdateException e) {
                        targetConn.rollback();
                        failedRows += count;
                        log.error("剩余批次写入失败 [{}]: {}", targetTable, e.getMessage());
                    }
                }

                syncLog.setSuccessRows(successRows);
                syncLog.setFailedRows(failedRows);
            }
        } finally {
            if (targetConn != null) {
                try {
                    // 恢复约束检查
                    DbType tDbType = DbType.valueOf(targetConfig.getDbType());
                    try (Statement restoreStmt = targetConn.createStatement()) {
                        switch (tDbType) {
                            case MYSQL, OCEANBASE -> {
                                restoreStmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                                restoreStmt.execute("SET UNIQUE_CHECKS = 1");
                            }
                            case POSTGRESQL, GAUSSDB -> {
                                restoreStmt.execute("SET session_replication_role = 'origin'");
                            }
                            default -> {}
                        }
                    } catch (Exception ignored) {}
                    targetConn.setAutoCommit(true);
                    targetConn.close();
                } catch (Exception ignored) {}
            }
            if (sourceConn != null) {
                try { sourceConn.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 通过 WebSocket 推送同步进度
     */
    private void pushProgress(Long taskId, SyncLog syncLog) {
        try {
            ProgressMessage msg = new ProgressMessage();
            msg.setTaskId(taskId);
            msg.setTotalRows(syncLog.getTotalRows());
            msg.setSuccessRows(syncLog.getSuccessRows());
            msg.setFailedRows(syncLog.getFailedRows());
            msg.setStatus(syncLog.getStatus());
            msg.setQps(syncLog.getQps());
            if (syncLog.getTotalRows() != null && syncLog.getTotalRows() > 0) {
                msg.setProgress((double) syncLog.getSuccessRows() / syncLog.getTotalRows() * 100);
            } else {
                msg.setProgress(0.0);
            }
            messagingTemplate.convertAndSend("/topic/sync-progress", msg);
        } catch (Exception e) {
            log.debug("推送进度失败: {}", e.getMessage());
        }
    }

    /**
     * 进度消息 DTO
     */
    @Data
    public static class ProgressMessage {
        private Long taskId;
        private Long totalRows;
        private Long successRows;
        private Long failedRows;
        private String status;
        private Double qps;
        private Double progress;
    }
    // 缓存反射获取的方法，避免在高频数据同步中产生严重性能损耗
    private static final Map<Class<?>, java.lang.reflect.Method> timestampMethodCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, java.lang.reflect.Method> toJdbcMethodCache = new ConcurrentHashMap<>();

    private java.lang.reflect.Method getCachedMethod(Class<?> clazz, String methodName, Map<Class<?>, java.lang.reflect.Method> cache) {
        return cache.computeIfAbsent(clazz, c -> {
            try {
                return c.getMethod(methodName);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * 将 Oracle JDBC 私有类型（oracle.sql.*）转换为标准 Java JDBC 类型。
     * <p>
     * 问题根源：通过 Oracle JDBC 驱动读取数据时，rs.getObject() 对 TIMESTAMP/DATE 等字段
     * 返回的是 oracle.sql.TIMESTAMP / oracle.sql.DATE 对象，而非 java.sql.Timestamp。
     * 将这些对象直接传给 MySQL/GaussDB 的 PreparedStatement.setObject() 时，
     * 目标驱动无法识别 Oracle 私有类型，导致 "Data truncation: Incorrect datetime value" 错误。
     * <p>
     * 使用反射调用，避免在编译期引入 Oracle JDBC 依赖。
     */
    private Object convertValue(Object value) {
        if (value == null) return null;
        Class<?> clazz = value.getClass();
        String className = clazz.getName();

        // oracle.sql.TIMESTAMP / oracle.sql.TIMESTAMPTZ / oracle.sql.TIMESTAMPLTZ
        if (className.startsWith("oracle.sql.TIMESTAMP")) {
            try {
                java.lang.reflect.Method m = getCachedMethod(clazz, "timestampValue", timestampMethodCache);
                if (m != null) return m.invoke(value);
            } catch (Exception e) {
                try {
                    java.lang.reflect.Method m = getCachedMethod(clazz, "toJdbc", toJdbcMethodCache);
                    if (m != null) return m.invoke(value);
                } catch (Exception ex) {
                    return null;
                }
            }
        }

        // oracle.sql.DATE
        if ("oracle.sql.DATE".equals(className)) {
            try {
                java.lang.reflect.Method m = getCachedMethod(clazz, "timestampValue", timestampMethodCache);
                if (m != null) return m.invoke(value);
            } catch (Exception e) {
                try {
                    java.lang.reflect.Method m = getCachedMethod(clazz, "toJdbc", toJdbcMethodCache);
                    if (m != null) return m.invoke(value);
                } catch (Exception ex) {
                    return null;
                }
            }
        }

        // oracle.sql.INTERVALDS / oracle.sql.INTERVALYM → String
        if (className.startsWith("oracle.sql.INTERVAL")) {
            return value.toString();
        }

        // java.sql.Clob / oracle.sql.CLOB
        if (value instanceof java.sql.Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (Exception e) {
                log.warn("CLOB 转换失败: {}", e.getMessage());
                return null;
            }
        }

        // java.sql.Blob / oracle.sql.BLOB
        if (value instanceof java.sql.Blob blob) {
            try {
                return blob.getBytes(1, (int) blob.length());
            } catch (Exception e) {
                log.warn("BLOB 转换失败: {}", e.getMessage());
                return null;
            }
        }

        // 其他 oracle.sql.* 类型（NUMBER、CHAR 等）：尝试 toJdbc() 转为标准类型
        if (className.startsWith("oracle.sql.")) {
            try {
                java.lang.reflect.Method m = getCachedMethod(clazz, "toJdbc", toJdbcMethodCache);
                if (m != null) {
                    Object converted = m.invoke(value);
                    return converted != null ? converted : value;
                }
            } catch (Exception e) {
                return value;
            }
        }

        return value;
    }

    /**
     * 根据目标数据库类型规范化标识符大小写
     * GaussDB/PostgreSQL 建表时已转为小写，数据同步时也需要用小写
     */
    private String normalizeTargetTable(String identifier, String dbTypeStr) {
        if (identifier == null) return null;
        try {
            DbType dbType = DbType.valueOf(dbTypeStr);
            return switch (dbType) {
                case GAUSSDB, POSTGRESQL -> identifier.toLowerCase();
                default -> identifier;
            };
        } catch (Exception e) {
            return identifier;
        }
    }
}
