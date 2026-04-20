package com.dbmigration.dialect;

import com.dbmigration.common.DbType;
import com.dbmigration.metadata.ColumnMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨库类型映射工具
 * 将源数据库列类型转换为目标数据库兼容的类型
 */
public class TypeMapper {

    /**
     * MySQL/OceanBase 行大小上限 (字节), 预留余量
     */
    private static final int MYSQL_ROW_SIZE_LIMIT = 8000;

    /**
     * 将源列类型映射为目标数据库的列类型定义（单列模式）
     */
    public static String mapColumnType(ColumnMeta col, DbType targetType) {
        String srcType = col.getDataType().toUpperCase().trim();
        int size = col.getColumnSize() != null ? col.getColumnSize() : 0;
        int scale = col.getDecimalDigits() != null ? col.getDecimalDigits() : 0;

        return switch (targetType) {
            case MYSQL, OCEANBASE -> toMySql(srcType, size, scale);
            case POSTGRESQL, GAUSSDB -> toPostgres(srcType, size, scale);
            case ORACLE, DM -> toOracle(srcType, size, scale);
        };
    }

    /**
     * 批量映射列类型（考虑 MySQL 行大小限制）
     * 当目标为 MySQL/OceanBase 且总行大小超限时，自动将大 VARCHAR 列降级为 TEXT
     *
     * @return 与 columns 顺序对应的映射类型列表
     */
    public static List<String> mapColumnTypes(List<ColumnMeta> columns, DbType targetType) {
        List<String> result = new ArrayList<>();
        for (ColumnMeta col : columns) {
            result.add(mapColumnType(col, targetType));
        }

        // 仅 MySQL / OceanBase 需要检查行大小
        if (targetType != DbType.MYSQL && targetType != DbType.OCEANBASE) {
            return result;
        }

        // 估算总行大小, 如果超限则降级大 VARCHAR（但跳过主键列）
        int totalBytes = estimateRowBytes(result);
        if (totalBytes > MYSQL_ROW_SIZE_LIMIT) {
            result = adjustForMySqlRowLimit(result, columns);
        }
        return result;
    }

    /**
     * 估算 MySQL 行大小 (utf8mb4: 每字符4字节)
     */
    private static int estimateRowBytes(List<String> types) {
        int total = 0;
        for (String t : types) {
            total += estimateColumnBytes(t);
        }
        return total;
    }

    /**
     * 估算单列的 MySQL 行内存储字节数
     */
    private static int estimateColumnBytes(String mysqlType) {
        String upper = mysqlType.toUpperCase();
        // TEXT / BLOB 类型只占行内 9-12 字节的指针
        if (upper.contains("TEXT") || upper.contains("BLOB")) return 12;
        if (upper.startsWith("VARCHAR")) {
            int len = extractLength(upper);
            // utf8mb4: 每字符最多4字节, 加2字节长度前缀
            return len * 4 + 2;
        }
        if (upper.startsWith("CHAR")) {
            int len = extractLength(upper);
            return len * 4;
        }
        if (upper.startsWith("DECIMAL") || upper.startsWith("NUMERIC")) return 16;
        if (upper.contains("BIGINT")) return 8;
        if (upper.contains("INT")) return 4;
        if (upper.contains("DOUBLE") || upper.contains("FLOAT")) return 8;
        if (upper.contains("DATE") || upper.contains("TIME")) return 8;
        if (upper.startsWith("TINYINT")) return 1;
        return 4; // 默认
    }

    private static int extractLength(String type) {
        int s = type.indexOf('(');
        int e = type.indexOf(')');
        if (s >= 0 && e > s) {
            try {
                String inner = type.substring(s + 1, e);
                if (inner.contains(",")) inner = inner.split(",")[0];
                return Integer.parseInt(inner.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 255;
    }

    /**
     * 当行大小超限时，按列大小从大到小，逐个将 VARCHAR/CHAR 降级为 TEXT
     * 主键列不降级（MySQL 不允许 TEXT 类型做主键）
     */
    private static List<String> adjustForMySqlRowLimit(List<String> types, List<ColumnMeta> columns) {
        List<String> adjusted = new ArrayList<>(types);

        // 构建候选索引：非主键的 VARCHAR/CHAR 列，按大小降序排列
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < adjusted.size(); i++) {
            // 跳过主键列
            if (i < columns.size() && Boolean.TRUE.equals(columns.get(i).getPrimaryKey())) {
                continue;
            }
            String upper = adjusted.get(i).toUpperCase();
            if (upper.startsWith("VARCHAR") || upper.startsWith("CHAR")) {
                candidates.add(i);
            }
        }
        // 大的列优先降级
        candidates.sort((a, b) -> {
            int la = extractLength(adjusted.get(a).toUpperCase());
            int lb = extractLength(adjusted.get(b).toUpperCase());
            return lb - la;
        });

        for (int idx : candidates) {
            if (estimateRowBytes(adjusted) <= MYSQL_ROW_SIZE_LIMIT) break;
            adjusted.set(idx, "TEXT");
        }

        // 如果降级完所有非 PK 的 VARCHAR/CHAR 后仍然超限，
        // 将 PK 的 VARCHAR 限制在 VARCHAR(191) 以内（utf8mb4 索引上限 767/4=191）
        if (estimateRowBytes(adjusted) > MYSQL_ROW_SIZE_LIMIT) {
            for (int i = 0; i < adjusted.size(); i++) {
                if (i < columns.size() && Boolean.TRUE.equals(columns.get(i).getPrimaryKey())) {
                    String upper = adjusted.get(i).toUpperCase();
                    if (upper.startsWith("VARCHAR") || upper.startsWith("CHAR")) {
                        int len = extractLength(upper);
                        if (len > 191) {
                            adjusted.set(i, "VARCHAR(191)");
                        }
                    }
                }
            }
        }
        return adjusted;
    }

    // ==================== 转 MySQL / OceanBase ====================
    private static String toMySql(String srcType, int size, int scale) {
        // 整数类型
        if (matches(srcType, "INT", "INTEGER", "INT4", "SERIAL")) return "INT";
        if (matches(srcType, "BIGINT", "INT8", "BIGSERIAL")) return "BIGINT";
        if (matches(srcType, "SMALLINT", "INT2", "TINYINT")) return "SMALLINT";

        // 浮点 / 精确数值
        if (matches(srcType, "NUMBER", "NUMERIC", "DECIMAL")) {
            if (size > 0 && scale > 0) return "DECIMAL(" + size + "," + scale + ")";
            if (size > 0) return "DECIMAL(" + size + ")";
            return "DECIMAL(38,6)";
        }
        if (matches(srcType, "FLOAT", "BINARY_FLOAT", "REAL", "FLOAT4")) return "FLOAT";
        if (matches(srcType, "DOUBLE", "DOUBLE PRECISION", "BINARY_DOUBLE", "FLOAT8")) return "DOUBLE";

        // 字符串 — 单列 cap 4000, 超过直接 TEXT
        if (matches(srcType, "VARCHAR", "VARCHAR2", "NVARCHAR", "NVARCHAR2", "CHARACTER VARYING")) {
            int len = size > 0 ? size : 255;
            if (len > 4000) return "TEXT";
            return "VARCHAR(" + len + ")";
        }
        if (matches(srcType, "CHAR", "NCHAR", "CHARACTER")) {
            int len = size > 0 ? Math.min(size, 255) : 1;
            return "CHAR(" + len + ")";
        }
        if (matches(srcType, "TEXT", "CLOB", "NCLOB", "LONG", "MEDIUMTEXT", "LONGTEXT")) return "LONGTEXT";

        // 日期时间
        if (matches(srcType, "DATE")) return "DATE";
        if (srcType.startsWith("TIMESTAMP")) return "DATETIME(6)";
        if (matches(srcType, "TIME")) return "TIME";

        // 二进制
        if (matches(srcType, "BLOB", "RAW", "BYTEA", "BINARY", "VARBINARY", "LONG RAW")) return "LONGBLOB";

        // 布尔
        if (matches(srcType, "BOOLEAN", "BOOL", "BIT")) return "TINYINT(1)";

        // 默认
        return "VARCHAR(255)";
    }

    // ==================== 转 PostgreSQL / GaussDB ====================
    private static String toPostgres(String srcType, int size, int scale) {
        if (matches(srcType, "INT", "INTEGER", "INT4", "SERIAL", "MEDIUMINT")) return "INTEGER";
        if (matches(srcType, "BIGINT", "INT8", "BIGSERIAL")) return "BIGINT";
        if (matches(srcType, "SMALLINT", "INT2", "TINYINT")) return "SMALLINT";

        if (matches(srcType, "NUMBER", "NUMERIC", "DECIMAL")) {
            if (size > 0 && scale > 0) return "NUMERIC(" + size + "," + scale + ")";
            if (size > 0) return "NUMERIC(" + size + ")";
            return "NUMERIC";
        }
        if (matches(srcType, "FLOAT", "BINARY_FLOAT", "REAL", "FLOAT4")) return "REAL";
        if (matches(srcType, "DOUBLE", "DOUBLE PRECISION", "BINARY_DOUBLE", "FLOAT8")) return "DOUBLE PRECISION";

        if (matches(srcType, "VARCHAR", "VARCHAR2", "NVARCHAR", "NVARCHAR2", "CHARACTER VARYING")) {
            int len = size > 0 ? size : 255;
            return "VARCHAR(" + len + ")";
        }
        if (matches(srcType, "CHAR", "NCHAR", "CHARACTER")) {
            int len = size > 0 ? size : 1;
            return "CHAR(" + len + ")";
        }
        if (matches(srcType, "TEXT", "CLOB", "NCLOB", "LONG", "MEDIUMTEXT", "LONGTEXT", "TINYTEXT")) return "TEXT";

        if (matches(srcType, "DATE")) return "DATE";
        if (srcType.startsWith("TIMESTAMP")) return "TIMESTAMP";
        if (matches(srcType, "TIME")) return "TIME";
        if (matches(srcType, "DATETIME")) return "TIMESTAMP";

        if (matches(srcType, "BLOB", "RAW", "BINARY", "VARBINARY", "LONGBLOB", "MEDIUMBLOB", "TINYBLOB", "LONG RAW")) return "BYTEA";

        if (matches(srcType, "BOOLEAN", "BOOL")) return "BOOLEAN";
        if (matches(srcType, "BIT")) return "BOOLEAN";

        return "VARCHAR(255)";
    }

    // ==================== 转 Oracle / DM ====================
    private static String toOracle(String srcType, int size, int scale) {
        if (matches(srcType, "INT", "INTEGER", "INT4", "SERIAL", "MEDIUMINT")) return "NUMBER(10)";
        if (matches(srcType, "BIGINT", "INT8", "BIGSERIAL")) return "NUMBER(19)";
        if (matches(srcType, "SMALLINT", "INT2", "TINYINT")) return "NUMBER(5)";

        if (matches(srcType, "NUMBER", "NUMERIC", "DECIMAL")) {
            if (size > 0 && scale > 0) return "NUMBER(" + size + "," + scale + ")";
            if (size > 0) return "NUMBER(" + size + ")";
            return "NUMBER";
        }
        if (matches(srcType, "FLOAT", "BINARY_FLOAT", "REAL", "FLOAT4")) return "BINARY_FLOAT";
        if (matches(srcType, "DOUBLE", "DOUBLE PRECISION", "BINARY_DOUBLE", "FLOAT8")) return "BINARY_DOUBLE";

        if (matches(srcType, "VARCHAR", "VARCHAR2", "NVARCHAR", "NVARCHAR2", "CHARACTER VARYING")) {
            int len = size > 0 ? Math.min(size, 4000) : 255;
            return "VARCHAR2(" + len + ")";
        }
        if (matches(srcType, "CHAR", "NCHAR", "CHARACTER")) {
            int len = size > 0 ? Math.min(size, 2000) : 1;
            return "CHAR(" + len + ")";
        }
        if (matches(srcType, "TEXT", "CLOB", "NCLOB", "MEDIUMTEXT", "LONGTEXT", "TINYTEXT")) return "CLOB";

        if (matches(srcType, "DATE", "DATETIME")) return "DATE";
        if (srcType.startsWith("TIMESTAMP")) return "TIMESTAMP(6)";
        if (matches(srcType, "TIME")) return "DATE";

        if (matches(srcType, "BLOB", "BYTEA", "BINARY", "VARBINARY", "LONGBLOB", "MEDIUMBLOB", "TINYBLOB")) return "BLOB";
        if (matches(srcType, "RAW", "LONG RAW")) return "RAW(2000)";

        if (matches(srcType, "BOOLEAN", "BOOL", "BIT")) return "NUMBER(1)";

        return "VARCHAR2(255)";
    }

    private static boolean matches(String srcType, String... candidates) {
        for (String c : candidates) {
            if (srcType.equalsIgnoreCase(c)) return true;
        }
        return false;
    }
}
