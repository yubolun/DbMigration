package com.dbmigration.metadata;

import com.dbmigration.common.DbType;
import com.dbmigration.common.Result;
import com.dbmigration.datasource.DynamicDataSourceManager;
import com.dbmigration.datasource.entity.DataSourceConfig;
import com.dbmigration.datasource.mapper.DataSourceMapper;
import com.dbmigration.dialect.DbDialect;
import com.dbmigration.dialect.DialectFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.util.List;

/**
 * 元数据服务 + REST Controller
 * 提供表列表和列信息查询
 */
@Slf4j
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataService {

    private final DataSourceMapper dataSourceMapper;
    private final DynamicDataSourceManager dsManager;
    private final DialectFactory dialectFactory;

    /**
     * 获取数据源下的 Schema/数据库 列表
     */
    @GetMapping("/{dsId}/schemas")
    public Result<List<String>> listSchemas(@PathVariable Long dsId) {
        try {
            DataSourceConfig config = dataSourceMapper.selectById(dsId);
            if (config == null) {
                return Result.fail(404, "数据源不存在");
            }
            DbDialect dialect = dialectFactory.getDialect(config.getDbType());
            try (Connection conn = dsManager.getConnection(dsId)) {
                List<String> schemas = dialect.listSchemas(conn);
                return Result.ok(schemas);
            }
        } catch (Exception e) {
            log.error("获取Schema列表失败: dsId={}", dsId, e);
            return Result.fail("获取Schema列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据源的表列表
     */
    @GetMapping("/{dsId}/tables")
    public Result<List<TableMeta>> listTables(@PathVariable Long dsId,
                                               @RequestParam(required = false) String schema) {
        try {
            DataSourceConfig config = dataSourceMapper.selectById(dsId);
            if (config == null) {
                return Result.fail(404, "数据源不存在");
            }
            DbDialect dialect = dialectFactory.getDialect(config.getDbType());
            try (Connection conn = dsManager.getConnection(dsId)) {
                List<TableMeta> tables = dialect.listTables(conn, schema);
                return Result.ok(tables);
            }
        } catch (Exception e) {
            log.error("获取表列表失败: dsId={}, schema={}", dsId, schema, e);
            return Result.fail("获取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的列信息
     */
    @GetMapping("/{dsId}/tables/{tableName}/columns")
    public Result<List<ColumnMeta>> listColumns(@PathVariable Long dsId, @PathVariable String tableName,
                                                 @RequestParam(required = false) String schema) {
        try {
            DataSourceConfig config = dataSourceMapper.selectById(dsId);
            if (config == null) {
                return Result.fail(404, "数据源不存在");
            }
            DbDialect dialect = dialectFactory.getDialect(config.getDbType());
            try (Connection conn = dsManager.getConnection(dsId)) {
                List<ColumnMeta> columns = dialect.listColumns(conn, schema, tableName);
                return Result.ok(columns);
            }
        } catch (Exception e) {
            log.error("获取列信息失败: dsId={}, schema={}, table={}", dsId, schema, tableName, e);
            return Result.fail("获取列信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据源的函数列表
     */
    @GetMapping("/{dsId}/functions")
    public Result<List<String>> listFunctions(@PathVariable Long dsId,
                                               @RequestParam(required = false) String schema) {
        try {
            DataSourceConfig config = dataSourceMapper.selectById(dsId);
            if (config == null) return Result.fail(404, "数据源不存在");
            DbDialect dialect = dialectFactory.getDialect(config.getDbType());
            try (Connection conn = dsManager.getConnection(dsId)) {
                return Result.ok(dialect.listFunctions(conn, schema));
            }
        } catch (Exception e) {
            log.error("获取函数列表失败: dsId={}, schema={}", dsId, schema, e);
            return Result.fail("获取函数列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据源的存储过程列表
     */
    @GetMapping("/{dsId}/procedures")
    public Result<List<String>> listProcedures(@PathVariable Long dsId,
                                                @RequestParam(required = false) String schema) {
        try {
            DataSourceConfig config = dataSourceMapper.selectById(dsId);
            if (config == null) return Result.fail(404, "数据源不存在");
            DbDialect dialect = dialectFactory.getDialect(config.getDbType());
            try (Connection conn = dsManager.getConnection(dsId)) {
                return Result.ok(dialect.listProcedures(conn, schema));
            }
        } catch (Exception e) {
            log.error("获取存储过程列表失败: dsId={}, schema={}", dsId, schema, e);
            return Result.fail("获取存储过程列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据源的视图列表
     */
    @GetMapping("/{dsId}/views")
    public Result<List<String>> listViews(@PathVariable Long dsId,
                                            @RequestParam(required = false) String schema) {
        try {
            DataSourceConfig config = dataSourceMapper.selectById(dsId);
            if (config == null) return Result.fail(404, "数据源不存在");
            DbDialect dialect = dialectFactory.getDialect(config.getDbType());
            try (Connection conn = dsManager.getConnection(dsId)) {
                return Result.ok(dialect.listViews(conn, schema));
            }
        } catch (Exception e) {
            log.error("获取视图列表失败: dsId={}, schema={}", dsId, schema, e);
            return Result.fail("获取视图列表失败: " + e.getMessage());
        }
    }

    /**
     * 在目标库自动建表（根据源库表结构 + 类型映射）
     */
    @PostMapping("/auto-create-table")
    public Result<String> autoCreateTable(@RequestBody AutoCreateRequest request) {
        try {
            // 1. 读取源表列信息
            DataSourceConfig sourceConfig = dataSourceMapper.selectById(request.getSourceDsId());
            DataSourceConfig targetConfig = dataSourceMapper.selectById(request.getTargetDsId());
            if (sourceConfig == null || targetConfig == null) {
                return Result.fail("源或目标数据源不存在");
            }

            DbDialect sourceDialect = dialectFactory.getDialect(sourceConfig.getDbType());
            DbDialect targetDialect = dialectFactory.getDialect(targetConfig.getDbType());

            List<ColumnMeta> columns;
            try (Connection sourceConn = dsManager.getConnection(request.getSourceDsId())) {
                columns = sourceDialect.listColumns(sourceConn, null, request.getSourceTable());
            }

            if (columns.isEmpty()) {
                return Result.fail("源表没有列信息: " + request.getSourceTable());
            }

            // 2. 生成目标库 CREATE TABLE SQL
            String targetTable = request.getTargetTable() != null && !request.getTargetTable().isBlank()
                    ? request.getTargetTable() : request.getSourceTable();

            // 标识符大小写规范化（Oracle→大写, GaussDB/PG→小写）
            DbType targetDbType = DbType.valueOf(targetConfig.getDbType());
            targetTable = normalizeIdentifier(targetTable, targetDbType);
            if (needsLowerCase(targetDbType)) {
                for (ColumnMeta col : columns) {
                    col.setColumnName(col.getColumnName().toLowerCase());
                }
            }

            String ddl = targetDialect.buildCreateTableSql(targetTable, columns);
            log.info("自动建表 DDL:\n{}", ddl);

            // 3. 在目标库执行 DDL
            try (Connection targetConn = dsManager.getConnection(request.getTargetDsId())) {
                try (var stmt = targetConn.createStatement()) {
                    stmt.execute(ddl);
                }
            }

            return Result.ok("建表成功: " + targetTable, ddl);
        } catch (Exception e) {
            log.error("自动建表失败: {}", request.getSourceTable(), e);
            return Result.fail("建表失败: " + e.getMessage());
        }
    }

    /**
     * 批量自动建表
     */
    @PostMapping("/batch-create-tables")
    public Result<BatchCreateResult> batchCreateTables(@RequestBody BatchCreateRequest request) {
        BatchCreateResult result = new BatchCreateResult();
        result.total = request.getTables().size();

        for (String tableName : request.getTables()) {
            try {
                AutoCreateRequest req = new AutoCreateRequest();
                req.setSourceDsId(request.getSourceDsId());
                req.setTargetDsId(request.getTargetDsId());
                req.setSourceTable(tableName);
                req.setTargetTable(tableName);

                Result<String> res = autoCreateTable(req);
                if (res.getCode() == 200) {
                    result.success++;
                    result.successTables.add(tableName);
                } else {
                    result.failed++;
                    result.failedTables.add(tableName + ": " + res.getMessage());
                }
            } catch (Exception e) {
                result.failed++;
                result.failedTables.add(tableName + ": " + e.getMessage());
            }
        }

        return Result.ok(result);
    }

    // ---- 标识符大小写处理 ----

    private String normalizeIdentifier(String identifier, DbType targetDbType) {
        if (identifier == null) return null;
        return switch (targetDbType) {
            case ORACLE -> identifier.toUpperCase();
            case GAUSSDB, POSTGRESQL -> identifier.toLowerCase();
            default -> identifier;
        };
    }

    private boolean needsLowerCase(DbType dbType) {
        return dbType == DbType.GAUSSDB || dbType == DbType.POSTGRESQL;
    }

    // ---- Request / Response DTOs ----

    @lombok.Data
    public static class AutoCreateRequest {
        private Long sourceDsId;
        private Long targetDsId;
        private String sourceTable;
        private String targetTable;
    }

    @lombok.Data
    public static class BatchCreateRequest {
        private Long sourceDsId;
        private Long targetDsId;
        private java.util.List<String> tables;
    }

    @lombok.Data
    public static class BatchCreateResult {
        int total;
        int success;
        int failed;
        java.util.List<String> successTables = new java.util.ArrayList<>();
        java.util.List<String> failedTables = new java.util.ArrayList<>();
    }
}
