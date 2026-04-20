package com.dbmigration.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dbmigration.sync.entity.SyncTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncTaskMapper extends BaseMapper<SyncTask> {
}
