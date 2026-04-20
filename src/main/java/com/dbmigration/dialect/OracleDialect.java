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
 * Oracle 方言实现
 */
public class OracleDialect implements DbDialect {

    @Override
    public DbType getDbType() {
        return DbType.ORACLE;
    }

    @Override
    public List<String> listSchemas(Connection conn) throws SQLException {
        List<String> schemas = new ArrayList<>();
        String sql = """
            SELECT USERNAME FROM ALL_USERS
            WHERE USERNAME NOT IN ('SYS','SYSTEM','DBSNMP','OUTLN','DIP','ORACLE_OCM',
                'APPQOSSYS','WMSYS','EXFSYS','CTXSYS','XDB','ORDDATA','ORDSYS','MDSYS',
                'OLAPSYS','OJVMSYS','GSMADMIN_INTERNAL','LBACSYS','DVSYS','DVF',
                'REMOTE_SCHEDULER_AGENT','DBSFWUSER','GGSYS','ANONYMOUS','SYSBACKUP',
                'SYSDG','SYSKM','SYSRAC','SYS$UMF','AUDSYS','XS$NULL')
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
        String sql = "SELECT TABLE_NAME, COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER = ? AND TABLE_TYPE = 'TABLE' AND TABLE_NAME NOT LIKE 'BIN$%' AND SUBSTR(table_name, -2) NOT BETWEEN '22' AND '99' ORDER BY TABLE_NAME";
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

        // 获取主键列
        Set<String> pkColumns = new HashSet<>();
        try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(null, owner, tableName.toUpperCase())) {
            while (pkRs.next()) {
                pkColumns.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        List<ColumnMeta> columns = new ArrayList<>();
        String sql = """
            SELECT c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH, c.DATA_PRECISION, c.DATA_SCALE, c.NULLABLE,
                   cc.COMMENTS
            FROM ALL_TAB_COLUMNS c
            LEFT JOIN ALL_COL_COMMENTS cc ON c.OWNER = cc.OWNER AND c.TABLE_NAME = cc.TABLE_NAME AND c.COLUMN_NAME = cc.COLUMN_NAME
            WHERE c.OWNER = ? AND c.TABLE_NAME = ?
            ORDER BY c.COLUMN_ID
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ColumnMeta col = new ColumnMeta();
                    col.setColumnName(rs.getString("COLUMN_NAME"));
                    col.setDataType(rs.getString("DATA_TYPE"));
                    int precision = rs.getInt("DATA_PRECISION");
                    int scale = rs.getInt("DATA_SCALE");
                    int length = rs.getInt("DATA_LENGTH");
                    col.setColumnSize(precision > 0 ? precision : length);
                    col.setDecimalDigits(scale);
                    col.setFullTypeName(buildOracleFullType(rs.getString("DATA_TYPE"), precision, scale, length));
                    col.setNullable("Y".equals(rs.getString("NULLABLE")));
                    col.setPrimaryKey(pkColumns.contains(col.getColumnName()));
                    col.setComment(rs.getString("COMMENTS"));
                    columns.add(col);
                }
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

    private String buildOracleFullType(String dataType, int precision, int scale, int length) {
        if ("NUMBER".equalsIgnoreCase(dataType)) {
            if (precision > 0) return scale > 0 ? "NUMBER(" + precision + "," + scale + ")" : "NUMBER(" + precision + ")";
            return "NUMBER";
        }
        if ("VARCHAR2".equalsIgnoreCase(dataType) || "CHAR".equalsIgnoreCase(dataType) || "NVARCHAR2".equalsIgnoreCase(dataType)) {
            return dataType + "(" + length + ")";
        }
        return dataType;
    }

    @Override
    public List<String> listFunctions(Connection conn, String schema) throws SQLException {
        List<String> funcs = new ArrayList<>();
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase() : conn.getSchema();
        String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_TYPE = 'FUNCTION' AND STATUS = 'VALID' ORDER BY OBJECT_NAME";
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
        String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_TYPE = 'PROCEDURE' AND STATUS = 'VALID' ORDER BY OBJECT_NAME";
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
        String sql = "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM DUAL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, objectType.toUpperCase());
            ps.setString(2, objectName.toUpperCase());
            ps.setString(3, owner);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    @Override
    public List<String> listViews(Connection conn, String schema) throws SQLException {
        List<String> views = new ArrayList<>();
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase() : conn.getSchema();
        String sql = "SELECT VIEW_NAME FROM ALL_VIEWS WHERE OWNER = ? AND SUBSTR(VIEW_NAME, -2) NOT BETWEEN '22' AND '99' ORDER BY VIEW_NAME";
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
                    String text = rs.getString(1);
                    return "CREATE OR REPLACE VIEW " + quoteIdentifier(viewName) + " AS " + text;
                }
            }
        }
        return null;
    }
}
