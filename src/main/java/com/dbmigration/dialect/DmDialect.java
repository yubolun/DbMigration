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
 * 达梦8 (DM8) 方言实现
 * DM 兼容 Oracle 语法, 使用 INFORMATION_SCHEMA 或 DBA_TABLES 系统视图
 */
public class DmDialect implements DbDialect {

    @Override
    public DbType getDbType() {
        return DbType.DM;
    }

    @Override
    public List<String> listSchemas(Connection conn) throws SQLException {
        List<String> schemas = new ArrayList<>();
        String sql = """
            SELECT USERNAME FROM ALL_USERS
            WHERE USERNAME NOT IN ('SYS','SYSSSO','SYSAUDITOR','SYSDBA','CTISYS','SYS_OBJECTS')
            ORDER BY USERNAME
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
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase() : conn.getSchema();
        String sql = """
            SELECT TABLE_NAME, COMMENTS
            FROM ALL_TAB_COMMENTS
            WHERE OWNER = ? AND TABLE_TYPE = 'TABLE' AND TABLE_NAME NOT LIKE 'BIN$%'
            ORDER BY TABLE_NAME
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TableMeta meta = new TableMeta();
                    meta.setTableName(rs.getString("TABLE_NAME"));
                    meta.setComment(rs.getString("COMMENTS"));
                    tables.add(meta);
                }
            }
        }
        return tables;
    }

    @Override
    public List<ColumnMeta> listColumns(Connection conn, String schema, String tableName) throws SQLException {
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase() : conn.getSchema();
        Set<String> pkColumns = new HashSet<>();
        try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(null, owner, tableName.toUpperCase())) {
            while (pkRs.next()) {
                pkColumns.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        List<ColumnMeta> columns = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getColumns(null, owner, tableName.toUpperCase(), "%")) {
            while (rs.next()) {
                ColumnMeta col = new ColumnMeta();
                col.setColumnName(rs.getString("COLUMN_NAME"));
                col.setDataType(rs.getString("TYPE_NAME"));
                int size = rs.getInt("COLUMN_SIZE");
                int digits = rs.getInt("DECIMAL_DIGITS");
                col.setColumnSize(size);
                col.setDecimalDigits(digits);
                col.setFullTypeName(rs.getString("TYPE_NAME") + (size > 0 ? "(" + size + (digits > 0 ? "," + digits : "") + ")" : ""));
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
        String owner = (schema != null && !schema.isBlank()) ? quoteIdentifier(schema) + "." : "";
        String sql = "SELECT " + cols + " FROM " + owner + quoteIdentifier(table);

        PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        ps.setFetchSize(1000);
        return ps;
    }

    @Override
    public String buildBatchInsertSql(String schema, String table, List<String> columns) {
        String owner = (schema != null && !schema.isBlank()) ? quoteIdentifier(schema) + "." : "";
        String cols = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + owner + quoteIdentifier(table) + " (" + cols + ") VALUES (" + placeholders + ")";
    }

    @Override
    public String buildUpsertSql(String schema, String table, List<String> columns, List<String> pkColumns) {
        // DM 支持 MERGE INTO 语法
        String owner = (schema != null && !schema.isBlank()) ? quoteIdentifier(schema) + "." : "";
        String fullTable = owner + quoteIdentifier(table);
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
          .append(" FROM DUAL) s ON (").append(onClause).append(")");
        if (!updateCols.isBlank()) {
            sb.append(" WHEN MATCHED THEN UPDATE SET ").append(updateCols);
        }
        sb.append(" WHEN NOT MATCHED THEN INSERT (").append(cols).append(") VALUES (").append(insertCols).append(")");
        return sb.toString();
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.toUpperCase() + "\"";
    }

    @Override
    public List<String> listFunctions(Connection conn, String schema) throws SQLException {
        List<String> funcs = new ArrayList<>();
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase() : conn.getSchema();
        String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_TYPE = 'FUNCTION' ORDER BY OBJECT_NAME";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) funcs.add(rs.getString(1));
            }
        }
        return funcs;
    }

    @Override
    public List<String> listProcedures(Connection conn, String schema) throws SQLException {
        List<String> procs = new ArrayList<>();
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase() : conn.getSchema();
        String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_TYPE = 'PROCEDURE' ORDER BY OBJECT_NAME";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) procs.add(rs.getString(1));
            }
        }
        return procs;
    }

    @Override
    public String getObjectDdl(Connection conn, String schema, String objectName, String objectType) throws SQLException {
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase() : conn.getSchema();
        try {
            String sql = "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM DUAL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, objectType.toUpperCase());
                ps.setString(2, objectName.toUpperCase());
                ps.setString(3, owner);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            // fallback: DM may not support DBMS_METADATA
        }
        return null;
    }

    @Override
    public List<String> listViews(Connection conn, String schema) throws SQLException {
        List<String> views = new ArrayList<>();
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase() : conn.getSchema();
        String sql = "SELECT VIEW_NAME FROM ALL_VIEWS WHERE OWNER = ? ORDER BY VIEW_NAME";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) views.add(rs.getString(1));
            }
        }
        return views;
    }

    @Override
    public String getViewDdl(Connection conn, String schema, String viewName) throws SQLException {
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase() : conn.getSchema();
        String sql = "SELECT TEXT FROM ALL_VIEWS WHERE OWNER = ? AND VIEW_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, viewName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "CREATE OR REPLACE VIEW " + quoteIdentifier(viewName) + " AS " + rs.getString(1);
                }
            }
        }
        return null;
    }
}
