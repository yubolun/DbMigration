package com.dbmigration.common;

import lombok.Getter;

/**
 * 支持的数据库类型枚举
 */
@Getter
public enum DbType {

    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s", "SELECT 1"),
    ORACLE("Oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//%s:%d/%s", "SELECT 1 FROM DUAL"),
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s", "SELECT 1"),
    DM("达梦8", "dm.jdbc.driver.DmDriver", "jdbc:dm://%s:%d/%s", "SELECT 1"),
    GAUSSDB("GaussDB", "com.huawei.gaussdb.jdbc.Driver", "jdbc:gaussdb://%s:%d/%s", "SELECT 1"),
    OCEANBASE("OceanBase", "com.oceanbase.jdbc.Driver", "jdbc:oceanbase://%s:%d/%s", "SELECT 1");

    private final String displayName;
    private final String driverClassName;
    private final String urlTemplate;
    private final String validationQuery;

    DbType(String displayName, String driverClassName, String urlTemplate, String validationQuery) {
        this.displayName = displayName;
        this.driverClassName = driverClassName;
        this.urlTemplate = urlTemplate;
        this.validationQuery = validationQuery;
    }

    /**
     * 根据数据源配置构建 JDBC URL
     */
    public String buildUrl(String host, int port, String dbName, String extraParams) {
        String baseUrl = String.format(urlTemplate, host, port, dbName);
        if (extraParams != null && !extraParams.isBlank()) {
            // MySQL/PG use ? params, Oracle uses differently
            String separator = baseUrl.contains("?") ? "&" : "?";
            if (this == ORACLE) {
                // Oracle thin URL doesn't use ? params in the same way
                return baseUrl;
            }
            return baseUrl + separator + extraParams;
        }
        return baseUrl;
    }
}
