package com.dbmigration.dialect;

import com.dbmigration.common.DbType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方言工厂
 * 根据数据库类型获取对应的方言实现
 */
@Component
public class DialectFactory {

    private static final Map<DbType, DbDialect> DIALECT_MAP = new ConcurrentHashMap<>();

    static {
        DIALECT_MAP.put(DbType.MYSQL, new MySqlDialect());
        DIALECT_MAP.put(DbType.ORACLE, new OracleDialect());
        DIALECT_MAP.put(DbType.POSTGRESQL, new PostgreSqlDialect());
        DIALECT_MAP.put(DbType.DM, new DmDialect());
        DIALECT_MAP.put(DbType.GAUSSDB, new GaussDialect());
        DIALECT_MAP.put(DbType.OCEANBASE, new OceanBaseDialect());
    }

    /**
     * 获取方言实例
     */
    public DbDialect getDialect(DbType dbType) {
        DbDialect dialect = DIALECT_MAP.get(dbType);
        if (dialect == null) {
            throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
        return dialect;
    }

    /**
     * 根据字符串类型名获取方言
     */
    public DbDialect getDialect(String dbTypeName) {
        return getDialect(DbType.valueOf(dbTypeName.toUpperCase()));
    }
}
