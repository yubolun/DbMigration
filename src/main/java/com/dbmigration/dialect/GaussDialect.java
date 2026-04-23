package com.dbmigration.dialect;

import com.dbmigration.common.DbType;
import com.dbmigration.metadata.ColumnMeta;
import com.dbmigration.metadata.TableMeta;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GaussDB 方言实现
 * GaussDB 兼容 PostgreSQL 协议, 使用 information_schema 视图
 */
public class GaussDialect implements DbDialect {

    @Override
    public DbType getDbType() {
        return DbType.GAUSSDB;
    }

    @Override
    public List<String> listSchemas(Connection conn) throws SQLException {
        List<String> schemas = new ArrayList<>();
        String sql = """
            SELECT schema_name FROM information_schema.schemata
            WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast',
                'pg_temp_1', 'pg_toast_temp_1', 'cstore', 'db4ai', 'dbe_perf',
                'dbe_pldebugger', 'dbe_sql_util', 'pkg_service', 'sqladvisor',
                'blockchain', 'snapshot')
            AND schema_name NOT LIKE 'pg_%'
            ORDER BY schema_name
            """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                schemas.add(rs.getString(1));
            }
        }
        return schemas;
    }

    @Override
    public List<TableMeta> listTables(Connection conn, String schema) throws SQLException {
        List<TableMeta> tables = new ArrayList<>();
        String schemaName = (schema != null && !schema.isBlank()) ? schema : "public";
        String sql = """
            SELECT table_name,
                   obj_description((quote_ident(table_schema) || '.' || quote_ident(table_name))::regclass) AS comment
            FROM information_schema.tables
            WHERE table_schema = ? AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TableMeta meta = new TableMeta();
                    meta.setTableName(rs.getString("table_name"));
                    meta.setComment(rs.getString("comment"));
                    tables.add(meta);
                }
            }
        }
        return tables;
    }

    @Override
    public List<ColumnMeta> listColumns(Connection conn, String schema, String tableName) throws SQLException {
        String schemaName = (schema != null && !schema.isBlank()) ? schema : "public";
        Set<String> pkColumns = new HashSet<>();
        try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(null, schemaName, tableName)) {
            while (pkRs.next()) {
                pkColumns.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        List<ColumnMeta> columns = new ArrayList<>();
        String sql = """
            SELECT column_name, data_type, character_maximum_length,
                   numeric_precision, numeric_scale, is_nullable
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ?
            ORDER BY ordinal_position
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ColumnMeta col = new ColumnMeta();
                    col.setColumnName(rs.getString("column_name"));
                    col.setDataType(rs.getString("data_type"));
                    Integer charLen = rs.getObject("character_maximum_length", Integer.class);
                    Integer numPrec = rs.getObject("numeric_precision", Integer.class);
                    Integer numScale = rs.getObject("numeric_scale", Integer.class);
                    col.setColumnSize(charLen != null ? charLen : (numPrec != null ? numPrec : 0));
                    col.setDecimalDigits(numScale != null ? numScale : 0);
                    col.setFullTypeName(rs.getString("data_type"));
                    col.setNullable("YES".equals(rs.getString("is_nullable")));
                    col.setPrimaryKey(pkColumns.contains(col.getColumnName()));
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    @Override
    public PreparedStatement prepareStreamQuery(Connection conn, String schema, String table, List<String> columns) throws SQLException {
        conn.setAutoCommit(false);
        String cols = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String schemaName = (schema != null && !schema.isBlank()) ? quoteIdentifier(schema) + "." : "";
        String sql = "SELECT " + cols + " FROM " + schemaName + quoteIdentifier(table);

        PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        ps.setFetchSize(1000);
        return ps;
    }

    @Override
    public String buildBatchInsertSql(String schema, String table, List<String> columns) {
        String schemaName = (schema != null && !schema.isBlank()) ? quoteIdentifier(schema) + "." : "";
        String cols = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + schemaName + quoteIdentifier(table) + " (" + cols + ") VALUES (" + placeholders + ")";
    }

    @Override
    public String buildUpsertSql(String schema, String table, List<String> columns, List<String> pkColumns) {
        // GaussDB 不支持 ON CONFLICT，使用 MERGE INTO 语法
        String schemaName = (schema != null && !schema.isBlank()) ? quoteIdentifier(schema) + "." : "";
        String fullTable = schemaName + quoteIdentifier(table);
        String cols = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String srcCols = columns.stream().map(c -> "? AS " + quoteIdentifier(c)).collect(Collectors.joining(", "));
        String onClause = pkColumns.stream()
                .map(c -> "t." + quoteIdentifier(c) + "=s." + quoteIdentifier(c))
                .collect(Collectors.joining(" AND "));
        String updateCols = columns.stream()
                .filter(c -> !pkColumns.contains(c))
                .map(c -> "t." + quoteIdentifier(c) + "=s." + quoteIdentifier(c))
                .collect(Collectors.joining(", "));
        String insertCols = columns.stream().map(c -> "s." + quoteIdentifier(c)).collect(Collectors.joining(", "));
        StringBuilder sb = new StringBuilder();
        sb.append("MERGE INTO ").append(fullTable).append(" t USING (SELECT ").append(srcCols)
          .append(") s ON (").append(onClause).append(")");
        if (!updateCols.isBlank()) {
            sb.append(" WHEN MATCHED THEN UPDATE SET ").append(updateCols);
        }
        sb.append(" WHEN NOT MATCHED THEN INSERT (").append(cols).append(") VALUES (").append(insertCols).append(")");
        return sb.toString();
    }

    @Override
    public String quoteIdentifier(String identifier) {
        // GaussDB 默认将无引号标识符折叠为小写，因此统一转小写
        // 对于纯小写+数字+下划线的简单标识符，不加引号（更符合 GaussDB 习惯）
        String lower = identifier.toLowerCase();
        if (lower.matches("[a-z_][a-z0-9_]*")) {
            return lower;
        }
        return "\"" + lower + "\"";
    }

    @Override
    public List<String> listFunctions(Connection conn, String schema) throws SQLException {
        List<String> funcs = new ArrayList<>();
        String schemaName = (schema != null && !schema.isBlank()) ? schema : "public";
        String sql = "SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = ? AND p.prokind = 'f' ORDER BY p.proname";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) funcs.add(rs.getString(1));
            }
        }
        return funcs;
    }

    @Override
    public List<String> listProcedures(Connection conn, String schema) throws SQLException {
        List<String> procs = new ArrayList<>();
        String schemaName = (schema != null && !schema.isBlank()) ? schema : "public";
        String sql = "SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = ? AND p.prokind = 'p' ORDER BY p.proname";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) procs.add(rs.getString(1));
            }
        }
        return procs;
    }

    @Override
    public String getObjectDdl(Connection conn, String schema, String objectName, String objectType) throws SQLException {
        String schemaName = (schema != null && !schema.isBlank()) ? schema : "public";
        String sql = "SELECT pg_get_functiondef(p.oid) FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = ? AND p.proname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, objectName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    @Override
    public List<String> listViews(Connection conn, String schema) throws SQLException {
        List<String> views = new ArrayList<>();
        String s = (schema != null && !schema.isBlank()) ? schema : "public";
        String sql = "SELECT viewname FROM pg_views WHERE schemaname = ? ORDER BY viewname";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) views.add(rs.getString(1));
            }
        }
        return views;
    }

    @Override
    public String getViewDdl(Connection conn, String schema, String viewName) throws SQLException {
        String s = (schema != null && !schema.isBlank()) ? schema : "public";
        String sql = "SELECT pg_get_viewdef(c.oid, true) FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid WHERE n.nspname = ? AND c.relname = ? AND c.relkind = 'v'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s);
            ps.setString(2, viewName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "CREATE OR REPLACE VIEW " + quoteIdentifier(viewName) + " AS " + rs.getString(1);
                }
            }
        }
        return null;
    }

    @Override
    public boolean tableExists(Connection conn, String tableName) throws SQLException {
        // 从连接中获取当前 schema
        String currentSchema = conn.getSchema();
        if (currentSchema == null || currentSchema.isBlank()) {
            currentSchema = "public";
        }

        String sql = "SELECT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = ? AND tablename = ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentSchema);
            ps.setString(2, tableName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        }
        return false;
    }
}
