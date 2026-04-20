package com.dbmigration.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dbmigration.sync.entity.FieldMapping;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FieldMappingMapper extends BaseMapper<FieldMapping> {
}
