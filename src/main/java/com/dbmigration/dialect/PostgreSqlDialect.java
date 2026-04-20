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
 * PostgreSQL 方言实现
 */
public class PostgreSqlDialect implements DbDialect {

    @Override
    public DbType getDbType() {
        return DbType.POSTGRESQL;
    }

    @Override
    public List<String> listSchemas(Connection conn) throws SQLException {
        List<String> schemas = new ArrayList<>();
        String sql = """
            SELECT schema_name FROM information_schema.schemata
            WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
            AND schema_name NOT LIKE 'pg_temp_%'
            AND schema_name NOT LIKE 'pg_toast_temp_%'
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
            SELECT t.tablename AS table_name,
                   obj_description((quote_ident(t.schemaname) || '.' || quote_ident(t.tablename))::regclass) AS comment
            FROM pg_tables t
            WHERE t.schemaname = ?
            ORDER BY t.tablename
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
            SELECT c.column_name, c.data_type, c.udt_name, c.character_maximum_length,
                   c.numeric_precision, c.numeric_scale, c.is_nullable,
                   pgd.description AS comment
            FROM information_schema.columns c
            LEFT JOIN pg_catalog.pg_statio_all_tables st ON st.relname = c.table_name AND st.schemaname = c.table_schema
            LEFT JOIN pg_catalog.pg_description pgd ON pgd.objoid = st.relid AND pgd.objsubid = c.ordinal_position
            WHERE c.table_schema = ? AND c.table_name = ?
            ORDER BY c.ordinal_position
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
                    col.setFullTypeName(buildPgFullType(rs.getString("data_type"), charLen, numPrec, numScale));
                    col.setNullable("YES".equals(rs.getString("is_nullable")));
                    col.setPrimaryKey(pkColumns.contains(col.getColumnName()));
                    col.setComment(rs.getString("comment"));
                    columns.add(col);
                }
            }
        }
        return columns;
    }

    @Override
    public PreparedStatement prepareStreamQuery(Connection conn, String schema, String table, List<String> columns) throws SQLException {
        // PG 流式读取: 必须关闭 autoCommit + 设置 fetchSize
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
        String baseSql = buildBatchInsertSql(schema, table, columns);
        String pkCols = pkColumns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String updateCols = columns.stream()
                .filter(c -> !pkColumns.contains(c))
                .map(c -> quoteIdentifier(c) + "=EXCLUDED." + quoteIdentifier(c))
                .collect(Collectors.joining(", "));
        if (updateCols.isBlank()) return baseSql + " ON CONFLICT (" + pkCols + ") DO NOTHING";
        return baseSql + " ON CONFLICT (" + pkCols + ") DO UPDATE SET " + updateCols;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        // PostgreSQL 默认将无引号标识符折叠为小写，因此统一转小写
        // 对于纯小写+数字+下划线的简单标识符，不加引号
        String lower = identifier.toLowerCase();
        if (lower.matches("[a-z_][a-z0-9_]*")) {
            return lower;
        }
        return "\"" + lower + "\"";
    }

    private String buildPgFullType(String dataType, Integer charLen, Integer numPrec, Integer numScale) {
        if ("character varying".equalsIgnoreCase(dataType) && charLen != null) {
            return "varchar(" + charLen + ")";
        }
        if ("numeric".equalsIgnoreCase(dataType) && numPrec != null) {
            return numScale != null && numScale > 0 ? "numeric(" + numPrec + "," + numScale + ")" : "numeric(" + numPrec + ")";
        }
        return dataType;
    }

    @Override
    public List<String> listFunctions(Connection conn, String schema) throws SQLException {
        List<String> funcs = new ArrayList<>();
        String schemaName = (schema != null && !schema.isBlank()) ? schema : "public";
        String sql = """
            SELECT p.proname AS func_name
            FROM pg_proc p
            JOIN pg_namespace n ON p.pronamespace = n.oid
            WHERE n.nspname = ? AND p.prokind = 'f'
            ORDER BY p.proname
            """;
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
        String sql = """
            SELECT p.proname AS proc_name
            FROM pg_proc p
            JOIN pg_namespace n ON p.pronamespace = n.oid
            WHERE n.nspname = ? AND p.prokind = 'p'
            ORDER BY p.proname
            """;
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
        String sql = """
            SELECT pg_get_functiondef(p.oid)
            FROM pg_proc p
            JOIN pg_namespace n ON p.pronamespace = n.oid
            WHERE n.nspname = ? AND p.proname = ?
            """;
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
}
