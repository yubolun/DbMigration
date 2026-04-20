package com.dbmigration.sync.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dbmigration.common.Result;
import com.dbmigration.datasource.service.DataSourceService;
import com.dbmigration.sync.entity.SyncLog;
import com.dbmigration.sync.service.SyncTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控看板 REST API
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MonitorController {

    private final SyncTaskService syncTaskService;
    private final DataSourceService dataSourceService;

    /**
     * 看板统计数据
     */
    @GetMapping("/monitor/dashboard")
    public Result<Map<String, Object>> dashboard() {
        SyncTaskService.DashboardStats stats = syncTaskService.getDashboardStats();
        var dsList = dataSourceService.listAll();
        long onlineCount = dsList.stream().filter(ds -> ds.getStatus() != null && ds.getStatus() == 1).count();

        Map<String, Object> data = new HashMap<>();
        data.put("totalDataSources", dsList.size());
        data.put("onlineDataSources", onlineCount);
        data.put("totalTasks", stats.totalTasks);
        data.put("runningTasks", stats.runningTasks);
        data.put("todaySyncRows", stats.todaySyncRows);
        return Result.ok(data);
    }

    /**
     * 同步日志列表（分页）
     */
    @GetMapping("/sync/logs")
    public Result<Page<SyncLog>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskName) {
        return Result.ok(syncTaskService.listLogs(page, size, taskId, status, taskName));
    }
}
