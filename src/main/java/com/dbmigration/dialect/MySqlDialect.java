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
 * MySQL 方言实现
 */
public class MySqlDialect implements DbDialect {

    @Override
    public DbType getDbType() {
        return DbType.MYSQL;
    }

    @Override
    public List<String> listSchemas(Connection conn) throws SQLException {
        List<String> schemas = new ArrayList<>();
        Set<String> systemDbs = Set.of("information_schema", "mysql", "performance_schema", "sys");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String db = rs.getString(1);
                if (!systemDbs.contains(db.toLowerCase())) {
                    schemas.add(db);
                }
            }
        }
        return schemas;
    }

    @Override
    public List<TableMeta> listTables(Connection conn, String schema) throws SQLException {
        List<TableMeta> tables = new ArrayList<>();
        String catalog = (schema != null && !schema.isBlank()) ? schema : conn.getCatalog();
        try (ResultSet rs = conn.getMetaData().getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                TableMeta meta = new TableMeta();
                meta.setTableName(rs.getString("TABLE_NAME"));
                meta.setComment(rs.getString("REMARKS"));
                tables.add(meta);
            }
        }
        return tables;
    }

    @Override
    public List<ColumnMeta> listColumns(Connection conn, String schema, String tableName) throws SQLException {
        String catalog = (schema != null && !schema.isBlank()) ? schema : conn.getCatalog();

        // 获取主键列
        Set<String> pkColumns = new HashSet<>();
        try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, null, tableName)) {
            while (pkRs.next()) {
                pkColumns.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        List<ColumnMeta> columns = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getColumns(catalog, null, tableName, "%")) {
            while (rs.next()) {
                ColumnMeta col = new ColumnMeta();
                col.setColumnName(rs.getString("COLUMN_NAME"));
                col.setDataType(rs.getString("TYPE_NAME"));
                int size = rs.getInt("COLUMN_SIZE");
                int digits = rs.getInt("DECIMAL_DIGITS");
                col.setColumnSize(size);
                col.setDecimalDigits(digits);
                col.setFullTypeName(buildFullTypeName(rs.getString("TYPE_NAME"), size, digits));
                col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                col.setPrimaryKey(pkColumns.contains(col.getColumnName()));
                col.setComment(rs.getString("REMARKS"));
                columns.add(col);
            }
        }
        return columns;
    }

    @Override
    public PreparedStatement prepareStreamQuery(Connection conn, String schema, String table, List<String> columns) throws SQLException {
        String cols = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String fullTable = (schema != null && !schema.isBlank()) ? quoteIdentifier(schema) + "." + quoteIdentifier(table) : quoteIdentifier(table);
        String sql = "SELECT " + cols + " FROM " + fullTable;

        // MySQL 流式读取: fetchSize=Integer.MIN_VALUE
        PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        ps.setFetchSize(Integer.MIN_VALUE);
        return ps;
    }

    @Override
    public String buildBatchInsertSql(String schema, String table, List<String> columns) {
        String fullTable = (schema != null && !schema.isBlank()) ? quoteIdentifier(schema) + "." + quoteIdentifier(table) : quoteIdentifier(table);
        String cols = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + fullTable + " (" + cols + ") VALUES (" + placeholders + ")";
    }

    @Override
    public String buildUpsertSql(String schema, String table, List<String> columns, List<String> pkColumns) {
        String baseSql = buildBatchInsertSql(schema, table, columns);
        // INSERT INTO ... ON DUPLICATE KEY UPDATE col1=VALUES(col1), col2=VALUES(col2)
        String updateCols = columns.stream()
                .filter(c -> !pkColumns.contains(c))
                .map(c -> quoteIdentifier(c) + "=VALUES(" + quoteIdentifier(c) + ")")
                .collect(Collectors.joining(", "));
        if (updateCols.isBlank()) return baseSql;
        return baseSql + " ON DUPLICATE KEY UPDATE " + updateCols;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    private String buildFullTypeName(String typeName, int size, int digits) {
        if (digits > 0) {
            return typeName + "(" + size + "," + digits + ")";
        } else if (size > 0 && !"INT".equalsIgnoreCase(typeName) && !"BIGINT".equalsIgnoreCase(typeName)
                && !"TINYINT".equalsIgnoreCase(typeName) && !"SMALLINT".equalsIgnoreCase(typeName)) {
            return typeName + "(" + size + ")";
        }
        return typeName;
    }

    @Override
    public List<String> listFunctions(Connection conn, String schema) throws SQLException {
        List<String> funcs = new ArrayList<>();
        String db = (schema != null && !schema.isBlank()) ? schema : conn.getCatalog();
        String sql = "SELECT ROUTINE_NAME FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA = ? AND ROUTINE_TYPE = 'FUNCTION' ORDER BY ROUTINE_NAME";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) funcs.add(rs.getString(1));
            }
        }
        return funcs;
    }

    @Override
    public List<String> listProcedures(Connection conn, String schema) throws SQLException {
        List<String> procs = new ArrayList<>();
        String db = (schema != null && !schema.isBlank()) ? schema : conn.getCatalog();
        String sql = "SELECT ROUTINE_NAME FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA = ? AND ROUTINE_TYPE = 'PROCEDURE' ORDER BY ROUTINE_NAME";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) procs.add(rs.getString(1));
            }
        }
        return procs;
    }

    @Override
    public String getObjectDdl(Connection conn, String schema, String objectName, String objectType) throws SQLException {
        String keyword = "FUNCTION".equalsIgnoreCase(objectType) ? "FUNCTION" : "PROCEDURE";
        String sql = "SHOW CREATE " + keyword + " " + quoteIdentifier(objectName);
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                // Column 2 = "Create Function" / "Create Procedure"
                return rs.getString(2);
            }
        }
        return null;
    }

    @Override
    public List<String> listViews(Connection conn, String schema) throws SQLException {
        List<String> views = new ArrayList<>();
        String db = (schema != null && !schema.isBlank()) ? schema : conn.getCatalog();
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) views.add(rs.getString(1));
            }
        }
        return views;
    }

    @Override
    public String getViewDdl(Connection conn, String schema, String viewName) throws SQLException {
        String sql = "SHOW CREATE VIEW " + quoteIdentifier(viewName);
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getString(2);
        }
        return null;
    }

    @Override
    public String buildTableCommentSql(String tableName, String comment) {
        return "ALTER TABLE " + quoteIdentifier(tableName) + " COMMENT = '" + escapeComment(comment) + "'";
    }

    @Override
    public String buildColumnCommentSql(String tableName, String columnName, String comment) {
        // MySQL 列注释需要完整的 MODIFY 语句，这里用 COMMENT ON 方式不支持
        // 改用 information_schema 查询后再 ALTER，这里直接跳过（返回 null 表示不支持单独设列注释）
        return null; // MySQL 列注释在 buildCreateTableSql 中内联处理
    }
}
