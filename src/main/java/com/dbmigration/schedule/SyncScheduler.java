package com.dbmigration.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dbmigration.sync.entity.SyncTask;
import com.dbmigration.sync.engine.SyncEngine;
import com.dbmigration.sync.mapper.SyncTaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 同步任务定时调度器
 * 从数据库加载带 cron_expr 的任务，动态注册/取消定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final SyncTaskMapper syncTaskMapper;
    private final SyncEngine syncEngine;
    private final TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    /**
     * 应用启动后加载所有带 Cron 表达式的任务
     */
    @PostConstruct
    public void init() {
        List<SyncTask> tasks = syncTaskMapper.selectList(
                new LambdaQueryWrapper<SyncTask>()
                        .isNotNull(SyncTask::getCronExpr)
                        .ne(SyncTask::getCronExpr, "")
                        .eq(SyncTask::getDeleted, 0)
        );
        for (SyncTask task : tasks) {
            scheduleTask(task);
        }
        log.info("已加载 {} 个定时同步任务", tasks.size());
    }

    /**
     * 注册定时任务
     */
    public void scheduleTask(SyncTask task) {
        if (task.getCronExpr() == null || task.getCronExpr().isBlank()) {
            return;
        }
        // 先取消已有的
        cancelTask(task.getId());

        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> {
                        try {
                            log.info("定时触发同步任务: taskId={}, taskName={}", task.getId(), task.getTaskName());
                            syncEngine.execute(task.getId());
                        } catch (Exception e) {
                            log.error("定时同步任务执行失败: taskId={}", task.getId(), e);
                        }
                    },
                    new CronTrigger(task.getCronExpr())
            );
            scheduledFutures.put(task.getId(), future);
            log.info("注册定时任务: taskId={}, cron={}", task.getId(), task.getCronExpr());
        } catch (Exception e) {
            log.error("注册定时任务失败: taskId={}, cron={}", task.getId(), task.getCronExpr(), e);
        }
    }

    /**
     * 取消定时任务
     */
    public void cancelTask(Long taskId) {
        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("取消定时任务: taskId={}", taskId);
        }
    }

    /**
     * 重新加载所有定时任务
     */
    public void reload() {
        // 取消所有
        scheduledFutures.forEach((id, future) -> future.cancel(false));
        scheduledFutures.clear();
        // 重新加载
        init();
    }
}
