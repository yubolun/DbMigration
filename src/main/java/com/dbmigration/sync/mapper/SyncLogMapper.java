package com.dbmigration.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dbmigration.sync.entity.SyncLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncLogMapper extends BaseMapper<SyncLog> {
}
