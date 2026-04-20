package com.dbmigration.datasource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dbmigration.datasource.entity.DataSourceConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源配置 Mapper
 */
@Mapper
public interface DataSourceMapper extends BaseMapper<DataSourceConfig> {
}
