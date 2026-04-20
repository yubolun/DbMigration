package com.dbmigration.dialect;

import com.dbmigration.common.DbType;
import com.dbmigration.metadata.ColumnMeta;
import com.dbmigration.metadata.TableMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据库方言接口
 * 每种数据库需实现自己的元数据读取和 SQL 构建逻辑
 */
public interface DbDialect {

    /**
     * 获取数据库类型
     */
    DbType getDbType();

    /**
     * 获取所有用户表
     */
    List<TableMeta> listTables(Connection conn, String schema) throws SQLException;

    /**
     * 获取数据源下的 Schema/数据库 列表
     * Oracle: 返回用户(Schema)列表
     * GaussDB/PostgreSQL: 返回 schema 列表
     * MySQL: 返回数据库列表
     */
    default List<String> listSchemas(Connection conn) throws SQLException {
        List<String> schemas = new java.util.ArrayList<>();
        try (java.sql.ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                schemas.add(rs.getString("TABLE_SCHEM"));
            }
        }
        return schemas;
    }

    /**
     * 获取表的列元数据
     */
    List<ColumnMeta> listColumns(Connection conn, String schema, String tableName) throws SQLException;

    /**
     * 构建流式查询 PreparedStatement（使用游标/fetchSize）
     */
    PreparedStatement prepareStreamQuery(Connection conn, String schema, String table, List<String> columns) throws SQLException;

    /**
     * 构建批量插入 SQL
     */
    String buildBatchInsertSql(String schema, String table, List<String> columns);

    /**
     * 构建 UPSERT SQL（增量同步用：存在则更新，不存在则插入）
     * @param pkColumns 主键列名列表
     */
    default String buildUpsertSql(String schema, String table, List<String> columns, List<String> pkColumns) {
        // 默认退化为普通 INSERT（子类应覆盖）
        return buildBatchInsertSql(schema, table, columns);
    }

    /**
     * 获取表总行数 SQL
     */
    default String buildCountSql(String schema, String table) {
        String fullTable = (schema != null && !schema.isBlank()) ? schema + "." + table : table;
        return "SELECT COUNT(*) FROM " + fullTable;
    }

    /**
     * 引用标识符（表名、列名加引号）
     */
    String quoteIdentifier(String identifier);

    /**
     * 构建 CREATE TABLE SQL
     * 根据源库列元数据 + 类型映射生成目标库的建表语句
     */
    default String buildCreateTableSql(String tableName, List<ColumnMeta> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(quoteIdentifier(tableName)).append(" (\n");

        // 批量映射类型（会自动处理 MySQL 宽表行大小限制）
        List<String> mappedTypes = TypeMapper.mapColumnTypes(columns, getDbType());

        List<String> pkCols = new java.util.ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnMeta col = columns.get(i);
            String mappedType = mappedTypes.get(i);
            sb.append("    ").append(quoteIdentifier(col.getColumnName())).append(" ").append(mappedType);
            if (Boolean.FALSE.equals(col.getNullable())) {
                sb.append(" NOT NULL");
            }
            if (Boolean.TRUE.equals(col.getPrimaryKey())) {
                pkCols.add(quoteIdentifier(col.getColumnName()));
            }
            if (i < columns.size() - 1 || !pkCols.isEmpty()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        if (!pkCols.isEmpty()) {
            sb.append("    PRIMARY KEY (").append(String.join(", ", pkCols)).append(")\n");
        }
        sb.append(")");
        // MySQL/OceanBase: 使用 DYNAMIC 行格式, 避免宽表 "Row size too large" 错误
        if (getDbType() == com.dbmigration.common.DbType.MYSQL || getDbType() == com.dbmigration.common.DbType.OCEANBASE) {
            sb.append(" ENGINE=InnoDB ROW_FORMAT=DYNAMIC");
        }
        return sb.toString();
    }

    /**
     * 获取用户函数列表
     */
    default List<String> listFunctions(Connection conn, String schema) throws SQLException {
        return java.util.Collections.emptyList();
    }

    /**
     * 获取用户存储过程列表
     */
    default List<String> listProcedures(Connection conn, String schema) throws SQLException {
        return java.util.Collections.emptyList();
    }

    /**
     * 获取用户视图列表
     */
    default List<String> listViews(Connection conn, String schema) throws SQLException {
        return java.util.Collections.emptyList();
    }

    /**
     * 获取视图的 DDL 定义
     */
    default String getViewDdl(Connection conn, String schema, String viewName) throws SQLException {
        return null;
    }

    /**
     * 获取函数/存储过程的完整 DDL
     * @param objectType FUNCTION 或 PROCEDURE
     */
    default String getObjectDdl(Connection conn, String schema, String objectName, String objectType) throws SQLException {
        return null;
    }

    /**
     * 判断表是否存在
     */
    default boolean tableExists(Connection conn, String tableName) throws SQLException {
        java.sql.DatabaseMetaData meta = conn.getMetaData();
        try (java.sql.ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) return true;
        }
        // 尝试大写
        try (java.sql.ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            if (rs.next()) return true;
        }
        // 尝试小写 (MySQL 默认小写存储)
        try (java.sql.ResultSet rs = meta.getTables(null, null, tableName.toLowerCase(), new String[]{"TABLE"})) {
            if (rs.next()) return true;
        }
        return false;
    }

    /**
     * 构建表注释 SQL
     */
    default String buildTableCommentSql(String tableName, String comment) {
        return "COMMENT ON TABLE " + quoteIdentifier(tableName) + " IS '" + escapeComment(comment) + "'";
    }

    /**
     * 构建列注释 SQL
     */
    default String buildColumnCommentSql(String tableName, String columnName, String comment) {
        return "COMMENT ON COLUMN " + quoteIdentifier(tableName) + "." + quoteIdentifier(columnName)
                + " IS '" + escapeComment(comment) + "'";
    }

    /**
     * 转义注释中的单引号
     */
    default String escapeComment(String comment) {
        if (comment == null) return "";
        return comment.replace("'", "''");
    }
}
