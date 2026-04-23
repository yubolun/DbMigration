package com.dbmigration.sync.engine;

import com.dbmigration.common.DbType;
import com.dbmigration.datasource.DynamicDataSourceManager;
import com.dbmigration.datasource.entity.DataSourceConfig;
import com.dbmigration.datasource.mapper.DataSourceMapper;
import com.dbmigration.dialect.DbDialect;
import com.dbmigration.dialect.DialectFactory;
import com.dbmigration.metadata.ColumnMeta;
import com.dbmigration.metadata.TableMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * 结构同步引擎
 * 支持跨库的表结构、函数、存储过程同步
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaSyncEngine {

    private final DataSourceMapper dataSourceMapper;
    private final DynamicDataSourceManager dsManager;
    private final DialectFactory dialectFactory;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 同步结果
     */
    public static class SyncResult {
        public int totalTables;
        public int successTables;
        public int failedTables;
        public int totalFunctions;
        public int successFunctions;
        public int totalProcedures;
        public int successProcedures;
        public int totalViews;
        public int successViews;
        public List<String> errors = new ArrayList<>();
    }

    /**
     * 执行结构同步
     * @param sourceDsId    源数据源ID
     * @param targetDsId    目标数据源ID
     * @param tables        要同步的表列表 (null = 全部)
     * @param functions     要同步的函数列表 (null = 不同步, empty = 全部)
     * @param procedures    要同步的存储过程列表 (null = 不同步, empty = 全部)
     * @param strategy      CREATE_IF_NOT_EXISTS 或 DROP_AND_CREATE
     * @param taskId        任务ID (用于 WebSocket 推送)
     */
    public SyncResult syncSchema(Long sourceDsId, Long targetDsId,
                                  List<String> tables, List<String> functions, List<String> procedures,
                                  String strategy, Long taskId, String sourceSchema, String targetSchema,
                                  java.util.function.Supplier<Boolean> stopChecker, List<String> views) {
        SyncResult result = new SyncResult();

        DataSourceConfig sourceConfig = dataSourceMapper.selectById(sourceDsId);
        DataSourceConfig targetConfig = dataSourceMapper.selectById(targetDsId);
        if (sourceConfig == null || targetConfig == null) {
            result.errors.add("数据源配置不存在");
            return result;
        }

        DbDialect sourceDialect = dialectFactory.getDialect(sourceConfig.getDbType());
        DbDialect targetDialect = dialectFactory.getDialect(targetConfig.getDbType());

        // ====== 1. 同步表结构 ======
        try (Connection sourceConn = dsManager.getConnection(sourceDsId, sourceSchema)) {
            // 获取源表元数据（包含表注释）
            List<TableMeta> allTableMetas = sourceDialect.listTables(sourceConn, sourceSchema);
            Map<String, String> tableCommentMap = new HashMap<>();
            for (TableMeta tm : allTableMetas) {
                tableCommentMap.put(tm.getTableName(), tm.getComment());
            }

            // 如果 tables 为 null，获取全部表
            if (tables == null) {
                tables = allTableMetas.stream().map(TableMeta::getTableName).toList();
            }
            result.totalTables = tables.size();

            try (Connection targetConn = dsManager.getConnection(targetDsId, targetSchema)) {
                for (int i = 0; i < tables.size(); i++) {
                    if (stopChecker != null && Boolean.TRUE.equals(stopChecker.get())) {
                        log.info("结构同步已停止: taskId={}, 已完成 {}/{} 表", taskId, i, tables.size());
                        result.errors.add("用户手动停止");
                        return result;
                    }
                    String tableName = tables.get(i);
                    try {
                        String tableComment = tableCommentMap.get(tableName);
                        syncOneTable(sourceConn, targetConn, sourceDialect, targetDialect,
                                tableName, tableComment, strategy, sourceSchema);
                        result.successTables++;
                        pushProgress(taskId, "TABLE", tableName, i + 1, tables.size(), "SUCCESS");
                    } catch (Exception e) {
                        result.failedTables++;
                        result.errors.add("[表] " + tableName + ": " + e.getMessage());
                        pushProgress(taskId, "TABLE", tableName, i + 1, tables.size(), "FAILED");
                        log.error("同步表结构失败: {}", tableName, e);
                    }
                }
            }
        } catch (Exception e) {
            result.errors.add("获取源表列表失败: " + e.getMessage());
            log.error("获取源表列表失败", e);
        }

        // ====== 2. 同步函数 ======
        if (functions != null) {
            try (Connection sourceConn = dsManager.getConnection(sourceDsId, sourceSchema)) {
                if (functions.isEmpty()) {
                    functions = sourceDialect.listFunctions(sourceConn, sourceSchema);
                }
                result.totalFunctions = functions.size();

                try (Connection targetConn = dsManager.getConnection(targetDsId, targetSchema)) {
                    for (String funcName : functions) {
                        if (stopChecker != null && Boolean.TRUE.equals(stopChecker.get())) break;
                        try {
                            syncOneObject(sourceConn, targetConn, sourceDialect,
                                    funcName, "FUNCTION", strategy, sourceSchema);
                            result.successFunctions++;
                        } catch (Exception e) {
                            result.errors.add("[函数] " + funcName + ": " + e.getMessage());
                            log.error("同步函数失败: {}", funcName, e);
                        }
                    }
                }
            } catch (Exception e) {
                result.errors.add("获取函数列表失败: " + e.getMessage());
            }
        }

        // ====== 3. 同步存储过程 ======
        if (procedures != null) {
            try (Connection sourceConn = dsManager.getConnection(sourceDsId, sourceSchema)) {
                if (procedures.isEmpty()) {
                    procedures = sourceDialect.listProcedures(sourceConn, sourceSchema);
                }
                result.totalProcedures = procedures.size();

                try (Connection targetConn = dsManager.getConnection(targetDsId, targetSchema)) {
                    for (String procName : procedures) {
                        if (stopChecker != null && Boolean.TRUE.equals(stopChecker.get())) break;
                        try {
                            syncOneObject(sourceConn, targetConn, sourceDialect,
                                    procName, "PROCEDURE", strategy, sourceSchema);
                            result.successProcedures++;
                        } catch (Exception e) {
                            result.errors.add("[存储过程] " + procName + ": " + e.getMessage());
                            log.error("同步存储过程失败: {}", procName, e);
                        }
                    }
                }
            } catch (Exception e) {
                result.errors.add("获取存储过程列表失败: " + e.getMessage());
            }
        }

        // ====== 4. 同步视图 ======
        if (views != null) {
            try (Connection sourceConn = dsManager.getConnection(sourceDsId, sourceSchema)) {
                if (views.isEmpty()) {
                    views = sourceDialect.listViews(sourceConn, sourceSchema);
                }
                result.totalViews = views.size();

                try (Connection targetConn = dsManager.getConnection(targetDsId, targetSchema)) {
                    for (String viewName : views) {
                        if (stopChecker != null && Boolean.TRUE.equals(stopChecker.get())) break;
                        try {
                            syncOneView(sourceConn, targetConn, sourceDialect, targetDialect,
                                    viewName, strategy, sourceSchema);
                            result.successViews++;
                        } catch (Exception e) {
                            result.errors.add("[视图] " + viewName + ": " + e.getMessage());
                            log.error("同步视图失败: {}", viewName, e);
                        }
                    }
                }
            } catch (Exception e) {
                result.errors.add("获取视图列表失败: " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * 同步单个表结构（包含表注释和字段注释）
     */
    private void syncOneTable(Connection sourceConn, Connection targetConn,
                               DbDialect sourceDialect, DbDialect targetDialect,
                               String tableName, String tableComment, String strategy, String sourceSchema) throws Exception {
        // 标识符大小写规范化：
        // Oracle 默认全大写，GaussDB/PostgreSQL 默认折叠为小写
        // 统一将目标表名转换为目标库适配的大小写
        String targetTableName = normalizeIdentifier(tableName, targetDialect.getDbType());

        log.info("【表同步调试】原始表名={}, 规范化后={}, 目标库类型={}, 当前schema={}",
                tableName, targetTableName, targetDialect.getDbType(),
                targetConn.getSchema());

        // 检查目标表是否已存在
        boolean exists = targetDialect.tableExists(targetConn, targetTableName);

        log.info("【表同步调试】表 {} 是否存在: {}, 策略: {}", targetTableName, exists, strategy);

        if ("CREATE_IF_NOT_EXISTS".equals(strategy) && exists) {
            log.info("目标表已存在, 跳过: {}", targetTableName);
            return;
        }
        if ("DROP_AND_CREATE".equals(strategy) && exists) {
            // 删除表时需要级联删除外键约束
            String dropSql;
            if (targetDialect.getDbType() == DbType.POSTGRESQL || targetDialect.getDbType() == DbType.GAUSSDB) {
                // PostgreSQL/GaussDB: 使用 CASCADE 级联删除依赖对象
                dropSql = "DROP TABLE IF EXISTS " + targetDialect.quoteIdentifier(targetTableName) + " CASCADE";
            } else {
                dropSql = targetDialect.buildDropTableSql(targetTableName);
            }
            try (Statement stmt = targetConn.createStatement()) {
                stmt.execute(dropSql);
                log.info("已删除目标表: {}", targetTableName);
            } catch (Exception e) {
                log.warn("删除表失败(可能不存在): {} - {}", targetTableName, e.getMessage());
            }
        }

        // 读取源表列信息
        List<ColumnMeta> columns = sourceDialect.listColumns(sourceConn, sourceSchema, tableName);
        if (columns.isEmpty()) {
            throw new RuntimeException("源表没有列信息: " + tableName);
        }

        // 对目标列名也做大小写规范化
        DbType targetDbType = targetDialect.getDbType();
        if (needsLowerCase(targetDbType)) {
            for (ColumnMeta col : columns) {
                col.setColumnName(col.getColumnName().toLowerCase());
            }
        }

        // 生成并执行 DDL
        String ddl = targetDialect.buildCreateTableSql(targetTableName, columns);
        log.info("建表 DDL [{}]:\n{}", targetTableName, ddl);
        try (Statement stmt = targetConn.createStatement()) {
            // MySQL/OceanBase: 临时关闭 innodb_strict_mode, 允许创建宽行表
            // DYNAMIC 行格式在运行时会自动将溢出列存储在页外, 不会真正超限
            boolean isMysql = targetDialect.getDbType() == com.dbmigration.common.DbType.MYSQL
                    || targetDialect.getDbType() == com.dbmigration.common.DbType.OCEANBASE;
            if (isMysql) {
                try { stmt.execute("SET SESSION innodb_strict_mode=OFF"); } catch (Exception ignored) {}
            }
            try {
                stmt.execute(ddl);
            } catch (SQLException e) {
                // 如果是"表已存在"错误，且策略是 CREATE_IF_NOT_EXISTS，则忽略
                String errMsg = e.getMessage().toLowerCase();
                if ("CREATE_IF_NOT_EXISTS".equals(strategy) &&
                    (errMsg.contains("already exists") || errMsg.contains("已存在"))) {
                    log.warn("表 {} 已存在(并发创建或检测失败)，跳过", targetTableName);
                    return;
                }
                throw e;
            } finally {
                if (isMysql) {
                    try { stmt.execute("SET SESSION innodb_strict_mode=ON"); } catch (Exception ignored) {}
                }
            }
        }

        // ====== 同步注释 ======
        try (Statement commentStmt = targetConn.createStatement()) {
            // 表注释
            if (tableComment != null && !tableComment.isBlank()) {
                try {
                    String commentSql = targetDialect.buildTableCommentSql(targetTableName, tableComment);
                    if (commentSql != null) {
                        log.debug("执行表注释 SQL: {}", commentSql);
                        commentStmt.execute(commentSql);
                        log.debug("表注释已同步: {} -> {}", targetTableName, tableComment);
                    }
                } catch (Exception e) {
                    log.warn("表注释同步失败: {} - {} SQL: {}", targetTableName, e.getMessage(),
                            targetDialect.buildTableCommentSql(targetTableName, tableComment));
                }
            }
            // 列注释
            for (ColumnMeta col : columns) {
                if (col.getComment() != null && !col.getComment().isBlank()) {
                    try {
                        String colCommentSql = targetDialect.buildColumnCommentSql(targetTableName, col.getColumnName(), col.getComment());
                        if (colCommentSql != null) {
                            log.debug("执行列注释 SQL: {}", colCommentSql);
                            commentStmt.execute(colCommentSql);
                        }
                    } catch (Exception e) {
                        log.warn("列注释同步失败: {}.{} - {} SQL: {}", targetTableName, col.getColumnName(), e.getMessage(),
                                targetDialect.buildColumnCommentSql(targetTableName, col.getColumnName(), col.getComment()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("同步注释失败(不影响表结构): {} - {}", targetTableName, e.getMessage());
        }
    }

    /**
     * 同步函数或存储过程
     */
    private void syncOneObject(Connection sourceConn, Connection targetConn,
                                DbDialect sourceDialect,
                                String objectName, String objectType, String strategy, String sourceSchema) throws Exception {
        String ddl = sourceDialect.getObjectDdl(sourceConn, sourceSchema, objectName, objectType);
        if (ddl == null || ddl.isBlank()) {
            throw new RuntimeException("无法获取 " + objectType + " DDL: " + objectName);
        }

        log.debug("原始 {} DDL [{}]:\n{}", objectType, objectName, ddl);

        // 清洗 Oracle 特有语法，使 DDL 兼容目标库
        ddl = cleanOracleDdl(ddl);

        log.debug("清洗后 {} DDL [{}]:\n{}", objectType, objectName, ddl);

        // DROP_AND_CREATE 策略先尝试删除
        if ("DROP_AND_CREATE".equals(strategy)) {
            try (Statement stmt = targetConn.createStatement()) {
                stmt.execute("DROP " + objectType + " IF EXISTS " + objectName);
            } catch (Exception e) {
                // 忽略，可能不存在
                log.debug("DROP {} {} 忽略错误: {}", objectType, objectName, e.getMessage());
            }
        }

        try (Statement stmt = targetConn.createStatement()) {
            stmt.execute(ddl);
        }
        log.info("同步 {} 成功: {}", objectType, objectName);
    }

    /**
     * 同步单个视图
     */
    private void syncOneView(Connection sourceConn, Connection targetConn,
                              DbDialect sourceDialect, DbDialect targetDialect,
                              String viewName, String strategy, String sourceSchema) throws Exception {
        String ddl = sourceDialect.getViewDdl(sourceConn, sourceSchema, viewName);
        if (ddl == null || ddl.isBlank()) {
            throw new RuntimeException("无法获取视图 DDL: " + viewName);
        }

        // 规范化视图名大小写
        String targetViewName = normalizeIdentifier(viewName, targetDialect.getDbType());

        // 清洗 Oracle 特有语法
        ddl = cleanOracleDdl(ddl);

        // DROP_AND_CREATE 策略先尝试删除
        if ("DROP_AND_CREATE".equals(strategy)) {
            try (Statement stmt = targetConn.createStatement()) {
                // Oracle 不支持 DROP VIEW IF EXISTS，直接 DROP 并捕获异常
                String dropSql = targetDialect.buildDropViewSql(targetViewName);
                stmt.execute(dropSql);
                log.debug("已删除视图: {}", targetViewName);
            } catch (Exception e) {
                log.debug("DROP VIEW {} 忽略错误: {}", targetViewName, e.getMessage());
            }
        }

        // 为了防止 GaussDB/PG 报错后导致整个事务 abort，使用 Savepoint 机制
        java.sql.Savepoint savepoint = null;
        try {
            if (!targetConn.getAutoCommit()) {
                savepoint = targetConn.setSavepoint();
            }
        } catch (Exception ignored) {}

        try (Statement stmt = targetConn.createStatement()) {
            stmt.execute(ddl);
            log.info("同步视图成功: {}", viewName);
            try {
                if (savepoint != null) targetConn.releaseSavepoint(savepoint);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.warn("同步视图 '{}' 失败，尝试通过兜底机制创建空视图. 原错误: {}", targetViewName, e.getMessage());

            // 出现异常时，回滚到 Savepoint 清除 Aborted 状态，才能继续执行空视图的创建
            try {
                if (savepoint != null) {
                    targetConn.rollback(savepoint);
                }
            } catch (Exception rollbackEx) {
                log.warn("回滚视图 Savepoint 失败: {}", rollbackEx.getMessage());
            }

            // 先尝试删除可能存在的视图（避免 ORA-00955 错误）
            try (Statement dropStmt = targetConn.createStatement()) {
                String dropSql = targetDialect.buildDropViewSql(targetViewName);
                dropStmt.execute(dropSql);
                log.debug("删除已存在的视图: {}", targetViewName);
            } catch (Exception dropEx) {
                log.debug("删除视图失败(可能不存在): {}", dropEx.getMessage());
            }

            String emptyViewSql = "CREATE VIEW " + targetDialect.quoteIdentifier(targetViewName) + " AS SELECT 1 AS empty_view_fallback";
            DbType dbType = targetDialect.getDbType();
            if (dbType != DbType.GAUSSDB && dbType != DbType.POSTGRESQL) {
                emptyViewSql += " FROM DUAL";
            }
            try (Statement emptyStmt = targetConn.createStatement()) {
                emptyStmt.execute(emptyViewSql);
                log.info("兜底创建空视图成功: {}", targetViewName);
            } catch (Exception fallbackEx) {
                log.error("兜底创建空视图同样失败: {}", targetViewName, fallbackEx);
                throw new RuntimeException("视图执行失败 (" + e.getMessage() + ") 且空视图兜底失败: " + fallbackEx.getMessage());
            }
        }
    }

    /**
     * 清洗 Oracle DDL 中不兼容的语法，使其可在 GaussDB/DM/PG 上执行
     */
    private String cleanOracleDdl(String ddl) {
        // 移除 EDITIONABLE / NONEDITIONABLE 关键字
        ddl = ddl.replaceAll("(?i)\\bEDITIONABLE\\b", "");
        ddl = ddl.replaceAll("(?i)\\bNONEDITIONABLE\\b", "");
        // 移除 schema 限定符 "SCHEMA_NAME"."OBJECT_NAME" → "OBJECT_NAME"
        ddl = ddl.replaceAll("\"[A-Za-z0-9_]+\"\\s*\\.\\s*\"", "\"");

        // ===== Oracle 系统包替换 =====
        // DBMS_OUTPUT.PUT_LINE(...) → RAISE NOTICE '%', ...
        ddl = ddl.replaceAll("(?im)^\\s*DBMS_OUTPUT\\.PUT_LINE\\s*\\((.+?)\\)\\s*;",
                "RAISE NOTICE '%', $1;");
        // RAISE_APPLICATION_ERROR(-20001, 'msg') → RAISE EXCEPTION '%', 'msg'
        // 使用非贪婪匹配，支持嵌套括号和多行
        ddl = ddl.replaceAll("(?i)RAISE_APPLICATION_ERROR\\s*\\(\\s*-?\\d+\\s*,\\s*([^;]+?)\\s*\\)\\s*;",
                "RAISE EXCEPTION '%', $1;");
        // UTL_RAW.CAST_TO_RAW(x) → x::bytea  (GaussDB/PG 兼容)
        ddl = ddl.replaceAll("(?i)UTL_RAW\\.CAST_TO_RAW\\s*\\((.+?)\\)", "($1)::bytea");
        // UTL_RAW.CAST_TO_VARCHAR2(x) → encode(x, 'escape')
        ddl = ddl.replaceAll("(?i)UTL_RAW\\.CAST_TO_VARCHAR2\\s*\\((.+?)\\)", "encode($1, 'escape')");
        // DBMS_CRYPTO / DBMS_LOB 等 schema 前缀去掉（可能已在目标库安装了扩展）
        ddl = ddl.replaceAll("(?i)\\bDBMS_CRYPTO\\.", "DBMS_CRYPTO.");
        ddl = ddl.replaceAll("(?i)\\bDBMS_LOB\\.", "DBMS_LOB.");

        // 移除末尾的 ALTER ... COMPILE 语句
        ddl = ddl.replaceAll("(?is)\\bALTER\\s+(PROCEDURE|FUNCTION)\\s+.*?COMPILE.*?;", "");
        // 移除末尾孤立的 /（Oracle SQL*Plus 执行符）
        ddl = ddl.replaceAll("(?m)^\\s*/\\s*$", "");
        // 移除 END 后的过程/函数名标签: END proc_name; → END;
        // 注意：不能替换 END IF / END LOOP / END CASE / END WHILE / END FOR
        ddl = ddl.replaceAll("(?im)\\bEND\\s+(?!IF\\b|LOOP\\b|CASE\\b|WHILE\\b|FOR\\b)([A-Za-z_][A-Za-z0-9_]*)\\s*;", "END;");
        // VARCHAR2 → VARCHAR (GaussDB兼容)
        ddl = ddl.replaceAll("(?i)\\bVARCHAR2\\b", "VARCHAR");
        // NUMBER → NUMERIC
        ddl = ddl.replaceAll("(?i)\\bNUMBER\\b", "NUMERIC");
        // 去除多余空格
        ddl = ddl.replaceAll("  +", " ");
        return ddl.trim();
    }

    private void pushProgress(Long taskId, String type, String name, int current, int total, String status) {
        if (taskId == null || messagingTemplate == null) return;
        Map<String, Object> msg = new HashMap<>();
        msg.put("taskId", taskId);
        msg.put("type", type);
        msg.put("name", name);
        msg.put("current", current);
        msg.put("total", total);
        msg.put("status", status);
        try {
            messagingTemplate.convertAndSend("/topic/sync-progress/" + taskId, msg);
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 根据目标数据库类型规范化标识符大小写
     * Oracle → 全大写, GaussDB/PostgreSQL → 全小写, 其他 → 保持原样
     */
    private String normalizeIdentifier(String identifier, DbType targetDbType) {
        if (identifier == null) return null;
        return switch (targetDbType) {
            case ORACLE -> identifier.toUpperCase();
            case GAUSSDB, POSTGRESQL -> identifier.toLowerCase();
            default -> identifier;
        };
    }

    /**
     * 判断目标库是否需要小写标识符
     */
    private boolean needsLowerCase(DbType dbType) {
        return dbType == DbType.GAUSSDB || dbType == DbType.POSTGRESQL;
    }
}
