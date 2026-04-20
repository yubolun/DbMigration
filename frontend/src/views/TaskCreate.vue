<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">{{ isEdit ? '编辑同步任务' : '创建同步任务' }}</h1>
      <p class="page-subtitle">{{ isEdit ? '修改任务配置后保存' : '选择同步模式，快速完成数据库迁移' }}</p>
    </div>

    <!-- ==================== 模式选择 Tab ==================== -->
    <div class="mode-tabs">
      <div class="mode-tab" :class="{ active: mode === 'FULL_DATA' }" @click="mode = 'FULL_DATA'">
        <div class="mode-icon"><SvgIcon name="download" :size="24" /></div>
        <div class="mode-title">全库数据同步</div>
        <div class="mode-desc">同步所有表结构 + 全量数据</div>
      </div>
      <div class="mode-tab" :class="{ active: mode === 'FULL_SCHEMA' }" @click="mode = 'FULL_SCHEMA'">
        <div class="mode-icon"><SvgIcon name="table" :size="24" /></div>
        <div class="mode-title">全库结构同步</div>
        <div class="mode-desc">仅同步表结构、函数、存储过程</div>
      </div>
      <div class="mode-tab" :class="{ active: mode === 'SELECTIVE' }" @click="mode = 'SELECTIVE'">
        <div class="mode-icon"><SvgIcon name="check-square" :size="24" /></div>
        <div class="mode-title">按需同步</div>
        <div class="mode-desc">自选表/函数/存储过程同步</div>
      </div>
      <div class="mode-tab" :class="{ active: mode === 'DATA_ONLY' }" @click="mode = 'DATA_ONLY'">
        <div class="mode-icon"><SvgIcon name="shuffle" :size="24" /></div>
        <div class="mode-title">纯数据同步</div>
        <div class="mode-desc">不动表结构，仅同步数据</div>
      </div>
    </div>

    <!-- ==================== FULL_DATA: 全库数据同步 ==================== -->
    <div v-if="mode === 'FULL_DATA'" class="slide-enter-active">
      <div class="card mb-4">
        <h3 style="margin-bottom: 16px; font-weight: 600;">① 选择源数据库</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">数据库类型</label>
            <CustomSelect v-model="sourceDbType" :options="dbTypeOptions" placeholder="全部类型" />
          </div>
          <div class="form-group">
            <label class="form-label">数据源</label>
            <CustomSelect v-model="form.sourceDsId" :options="sourceDsOptions" placeholder="请选择数据源" :searchable="sourceDsOptions.length > 5" @change="onSourceDsChange" />
          </div>
          <div class="form-group" v-if="form.sourceDsId">
            <label class="form-label">数据库</label>
            <CustomSelect v-model="sourceSchema" :options="sourceSchemaOptions" placeholder="请选择数据库" :searchable="sourceSchemaOptions.length > 5" @change="loadSourceMeta" />
          </div>
        </div>
        <div v-if="sourceSchema && sourceStats.loaded" class="stats-bar">
          <span class="stat-item"><SvgIcon name="table" :size="13" /> <strong>{{ sourceStats.tables }}</strong> 张表</span>
          <span class="stat-item"><SvgIcon name="settings" :size="13" /> <strong>{{ sourceStats.functions }}</strong> 个函数</span>
          <span class="stat-item"><SvgIcon name="server" :size="13" /> <strong>{{ sourceStats.procedures }}</strong> 个存储过程</span>
          <span class="stat-item"><SvgIcon name="monitor" :size="13" /> <strong>{{ sourceStats.views }}</strong> 个视图</span>
        </div>
      </div>

      <div class="card mb-4">
        <h3 style="margin-bottom: 16px; font-weight: 600;">② 选择目标数据库</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">数据库类型</label>
            <CustomSelect v-model="targetDbType" :options="dbTypeOptions" placeholder="全部类型" />
          </div>
          <div class="form-group">
            <label class="form-label">数据源</label>
            <CustomSelect v-model="form.targetDsId" :options="targetDsOptions" placeholder="请选择数据源" :searchable="targetDsOptions.length > 5" @change="onTargetDsChange" />
          </div>
          <div class="form-group" v-if="form.targetDsId">
            <label class="form-label">数据库</label>
            <CustomSelect v-model="targetSchema" :options="targetSchemaOptions" placeholder="请选择数据库" :searchable="targetSchemaOptions.length > 5" />
          </div>
        </div>
      </div>

      <div class="card mb-4">
        <h3 style="margin-bottom: 16px; font-weight: 600;">③ 同步选项</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">任务名称</label>
            <input class="form-input" v-model="form.taskName" placeholder="例: 生产库全量迁移" />
          </div>
          <div class="form-group">
            <label class="form-label">结构策略</label>
            <CustomSelect v-model="form.schemaStrategy" :options="schemaStrategyOptions" />
          </div>
          <div class="form-group">
            <label class="form-label">批量大小</label>
            <input class="form-input" v-model.number="form.batchSize" type="number" />
          </div>
        </div>
        <div class="check-row">
          <label class="check-label"><input type="checkbox" v-model="form.includeFunctions" /> 同步函数</label>
          <label class="check-label"><input type="checkbox" v-model="form.includeProcedures" /> 同步存储过程</label>
          <label class="check-label"><input type="checkbox" v-model="form.includeViews" /> 同步视图</label>
        </div>
      </div>

      <div style="text-align: right;">
        <router-link to="/tasks" class="btn btn-outline" style="margin-right: 12px;">取消</router-link>
        <button class="btn btn-primary btn-lg" @click="submitBatch('FULL_DATA')"
                :disabled="submitting || !form.sourceDsId || !form.targetDsId || !form.taskName || !sourceSchema">
          {{ submitting ? '保存中...' : (isEdit ? '保存修改' : '开始全库同步') }}
        </button>
      </div>
    </div>

    <!-- ==================== FULL_SCHEMA: 全库结构同步 ==================== -->
    <div v-if="mode === 'FULL_SCHEMA'" class="slide-enter-active">
      <div class="card mb-4">
        <h3 style="margin-bottom: 16px; font-weight: 600;">① 选择源数据库</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">数据库类型</label>
            <CustomSelect v-model="sourceDbType" :options="dbTypeOptions" placeholder="全部类型" />
          </div>
          <div class="form-group">
            <label class="form-label">数据源</label>
            <CustomSelect v-model="form.sourceDsId" :options="sourceDsOptions" placeholder="请选择数据源" :searchable="sourceDsOptions.length > 5" @change="onSourceDsChange" />
          </div>
          <div class="form-group" v-if="form.sourceDsId">
            <label class="form-label">数据库</label>
            <CustomSelect v-model="sourceSchema" :options="sourceSchemaOptions" placeholder="请选择数据库" :searchable="sourceSchemaOptions.length > 5" @change="loadSourceMeta" />
          </div>
        </div>
        <div v-if="sourceSchema && sourceStats.loaded" class="stats-bar">
          <span class="stat-item"><SvgIcon name="table" :size="13" /> <strong>{{ sourceStats.tables }}</strong> 张表</span>
          <span class="stat-item"><SvgIcon name="settings" :size="13" /> <strong>{{ sourceStats.functions }}</strong> 个函数</span>
          <span class="stat-item"><SvgIcon name="server" :size="13" /> <strong>{{ sourceStats.procedures }}</strong> 个存储过程</span>
          <span class="stat-item"><SvgIcon name="monitor" :size="13" /> <strong>{{ sourceStats.views }}</strong> 个视图</span>
        </div>
      </div>

      <div class="card mb-4">
        <h3 style="margin-bottom: 16px; font-weight: 600;">② 选择目标数据库</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">数据库类型</label>
            <CustomSelect v-model="targetDbType" :options="dbTypeOptions" placeholder="全部类型" />
          </div>
          <div class="form-group">
            <label class="form-label">数据源</label>
            <CustomSelect v-model="form.targetDsId" :options="targetDsOptions" placeholder="请选择数据源" :searchable="targetDsOptions.length > 5" @change="onTargetDsChange" />
          </div>
          <div class="form-group" v-if="form.targetDsId">
            <label class="form-label">数据库</label>
            <CustomSelect v-model="targetSchema" :options="targetSchemaOptions" placeholder="请选择数据库" :searchable="targetSchemaOptions.length > 5" />
          </div>
        </div>
      </div>

      <div class="card mb-4">
        <h3 style="margin-bottom: 16px; font-weight: 600;">③ 同步选项</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">任务名称</label>
            <input class="form-input" v-model="form.taskName" placeholder="例: 结构迁移-UAT到PROD" />
          </div>
          <div class="form-group">
            <label class="form-label">结构策略</label>
            <CustomSelect v-model="form.schemaStrategy" :options="schemaStrategyOptions" />
          </div>
        </div>
        <div class="check-row">
          <label class="check-label"><input type="checkbox" v-model="form.includeFunctions" /> 同步函数</label>
          <label class="check-label"><input type="checkbox" v-model="form.includeProcedures" /> 同步存储过程</label>
          <label class="check-label"><input type="checkbox" v-model="form.includeViews" /> 同步视图</label>
        </div>
      </div>

      <div style="text-align: right;">
        <router-link to="/tasks" class="btn btn-outline" style="margin-right: 12px;">取消</router-link>
        <button class="btn btn-primary btn-lg" @click="submitBatch('FULL_SCHEMA')"
                :disabled="submitting || !form.sourceDsId || !form.targetDsId || !form.taskName || !sourceSchema">
          {{ submitting ? '保存中...' : (isEdit ? '保存修改' : '开始结构同步') }}
        </button>
      </div>
    </div>

    <!-- ==================== SELECTIVE: 按需同步 ==================== -->
    <div v-if="mode === 'SELECTIVE'" class="slide-enter-active">
      <div class="card mb-4">
        <h3 style="margin-bottom: 16px; font-weight: 600;">① 选择源数据库 & 同步对象</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">数据库类型</label>
            <CustomSelect v-model="sourceDbType" :options="dbTypeOptions" placeholder="全部类型" />
          </div>
          <div class="form-group">
            <label class="form-label">数据源</label>
            <CustomSelect v-model="form.sourceDsId" :options="sourceDsOptions" placeholder="请选择数据源" :searchable="sourceDsOptions.length > 5" @change="onSourceDsChange" />
          </div>
          <div class="form-group" v-if="form.sourceDsId">
            <label class="form-label">数据库</label>
            <CustomSelect v-model="sourceSchema" :options="sourceSchemaOptions" placeholder="请选择数据库" :searchable="sourceSchemaOptions.length > 5" @change="loadSourceMetaAll" />
          </div>
        </div>
      </div>

      <!-- 表选择 -->
      <div class="card mb-4" v-if="sourceSchema">
        <div class="flex justify-between items-center" style="margin-bottom: 16px;">
          <h3 style="font-weight: 600;">选择表 <span class="text-sm text-muted" v-if="sourceTables.length">(已选 {{ selectedSourceTables.length }}/{{ filteredSourceTables.length }})</span></h3>
          <div class="flex gap-2">
            <input class="form-input" v-model="sourceSearch" placeholder="搜索..." style="width: 180px; padding: 6px 12px;" />
            <button class="btn btn-sm btn-primary" @click="sourceSelectAll"><SvgIcon name="check-square" :size="13" /> 全选</button>
            <button class="btn btn-sm btn-outline" @click="sourceDeselectAll"><SvgIcon name="square" :size="13" /> 清空</button>
            <button class="btn btn-sm btn-outline" @click="sourceInvert"><SvgIcon name="shuffle" :size="13" /> 反选</button>
          </div>
        </div>
        <div v-if="loadingTables" class="text-muted" style="padding: 20px; text-align: center;">加载中...</div>
        <div v-else class="table-wrapper" style="border:none; max-height: 350px; overflow-y: auto;">
          <table>
            <thead>
              <tr>
                <th style="width:40px;"><input type="checkbox" :checked="isSourceAllSelected" @change="toggleSourceAll" /></th>
                <th>表名</th><th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in filteredSourceTables" :key="t.tableName" @click="t._selected = !t._selected"
                  style="cursor:pointer;" :style="t._selected ? 'background:var(--primary-bg)' : ''">
                <td @click.stop><input type="checkbox" v-model="t._selected" /></td>
                <td class="font-mono">{{ t.tableName }}</td>
                <td class="text-sm text-muted">{{ t.comment || '-' }}</td>
              </tr>
              <tr v-if="filteredSourceTables.length === 0">
                <td colspan="3" style="text-align:center; color:var(--text-muted); padding:20px;">无匹配表</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 函数 & 存储过程 -->
      <div class="form-row" v-if="sourceSchema">
        <div class="card" style="flex:1;">
          <div class="flex justify-between items-center" style="margin-bottom:12px;">
            <h3 style="font-weight:600;">函数 <span class="text-sm text-muted">({{ selectedFunctions.length }}/{{ sourceFunctions.length }})</span></h3>
            <div class="flex gap-2">
              <button class="btn btn-sm btn-primary" @click="funcSelectAll">全选</button>
              <button class="btn btn-sm btn-outline" @click="funcDeselectAll">清空</button>
            </div>
          </div>
          <div style="max-height:200px; overflow-y:auto;">
            <div v-for="f in sourceFunctions" :key="f.name" class="check-item" @click="f._selected = !f._selected"
                 :style="f._selected ? 'background:var(--primary-bg)' : ''">
              <input type="checkbox" v-model="f._selected" @click.stop /> <span class="font-mono">{{ f.name }}</span>
            </div>
            <div v-if="sourceFunctions.length === 0" class="text-sm text-muted" style="padding:12px; text-align:center;">无函数</div>
          </div>
        </div>
        <div class="card" style="flex:1;">
          <div class="flex justify-between items-center" style="margin-bottom:12px;">
            <h3 style="font-weight:600;">存储过程 <span class="text-sm text-muted">({{ selectedProcedures.length }}/{{ sourceProcedures.length }})</span></h3>
            <div class="flex gap-2">
              <button class="btn btn-sm btn-primary" @click="procSelectAll">全选</button>
              <button class="btn btn-sm btn-outline" @click="procDeselectAll">清空</button>
            </div>
          </div>
          <div style="max-height:200px; overflow-y:auto;">
            <div v-for="p in sourceProcedures" :key="p.name" class="check-item" @click="p._selected = !p._selected"
                 :style="p._selected ? 'background:var(--primary-bg)' : ''">
              <input type="checkbox" v-model="p._selected" @click.stop /> <span class="font-mono">{{ p.name }}</span>
            </div>
            <div v-if="sourceProcedures.length === 0" class="text-sm text-muted" style="padding:12px; text-align:center;">无存储过程</div>
          </div>
        </div>
        <div class="card" style="flex:1;">
          <div class="flex justify-between items-center" style="margin-bottom:12px;">
            <h3 style="font-weight:600;">视图 <span class="text-sm text-muted">({{ selectedViews.length }}/{{ sourceViews.length }})</span></h3>
            <div class="flex gap-2">
              <button class="btn btn-sm btn-primary" @click="viewSelectAll">全选</button>
              <button class="btn btn-sm btn-outline" @click="viewDeselectAll">清空</button>
            </div>
          </div>
          <div style="max-height:200px; overflow-y:auto;">
            <div v-for="v in sourceViews" :key="v.name" class="check-item" @click="v._selected = !v._selected"
                 :style="v._selected ? 'background:var(--primary-bg)' : ''">
              <input type="checkbox" v-model="v._selected" @click.stop /> <span class="font-mono">{{ v.name }}</span>
            </div>
            <div v-if="sourceViews.length === 0" class="text-sm text-muted" style="padding:12px; text-align:center;">无视图</div>
          </div>
        </div>
      </div>

      <!-- 目标库 + 选项 -->
      <div class="card mb-4" v-if="sourceSchema" style="margin-top:16px;">
        <h3 style="margin-bottom: 16px; font-weight: 600;">② 选择目标数据库 & 同步选项</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">数据库类型</label>
            <CustomSelect v-model="targetDbType" :options="dbTypeOptions" placeholder="全部类型" />
          </div>
          <div class="form-group">
            <label class="form-label">数据源</label>
            <CustomSelect v-model="form.targetDsId" :options="targetDsOptions" placeholder="请选择数据源" :searchable="targetDsOptions.length > 5" @change="onTargetDsChange" />
          </div>
          <div class="form-group" v-if="form.targetDsId">
            <label class="form-label">数据库</label>
            <CustomSelect v-model="targetSchema" :options="targetSchemaOptions" placeholder="请选择数据库" :searchable="targetSchemaOptions.length > 5" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">任务名称</label>
            <input class="form-input" v-model="form.taskName" placeholder="例: 用户表同步" />
          </div>
          <div class="form-group">
            <label class="form-label">结构策略</label>
            <CustomSelect v-model="form.schemaStrategy" :options="schemaStrategyOptions" />
          </div>
          <div class="form-group">
            <label class="form-label">同步模式</label>
            <CustomSelect v-model="form.syncMode" :options="syncModeOptions" />
          </div>
          <div class="form-group">
            <label class="form-label">批量大小</label>
            <input class="form-input" v-model.number="form.batchSize" type="number" />
          </div>
        </div>
      </div>

      <!-- 确认摘要 -->
      <div class="card mb-4" v-if="sourceSchema && form.targetDsId && selectedSourceTables.length > 0" style="border-color: var(--primary); background: var(--primary-bg);">
        <h3 style="margin-bottom: 12px; font-weight: 600;">同步摘要</h3>
        <div class="text-sm">
          <div><SvgIcon name="table" :size="13" /> <strong>{{ selectedSourceTables.length }}</strong> 张表</div>
          <div v-if="selectedFunctions.length > 0"><SvgIcon name="settings" :size="13" /> <strong>{{ selectedFunctions.length }}</strong> 个函数</div>
          <div v-if="selectedProcedures.length > 0"><SvgIcon name="server" :size="13" /> <strong>{{ selectedProcedures.length }}</strong> 个存储过程</div>
          <div v-if="selectedViews.length > 0"><SvgIcon name="monitor" :size="13" /> <strong>{{ selectedViews.length }}</strong> 个视图</div>
        </div>
      </div>

      <div style="text-align: right;" v-if="sourceSchema">
        <router-link to="/tasks" class="btn btn-outline" style="margin-right: 12px;">取消</router-link>
        <button class="btn btn-primary btn-lg" @click="submitSelective"
                :disabled="submitting || !form.targetDsId || !form.taskName || selectedSourceTables.length === 0">
          {{ submitting ? '保存中...' : (isEdit ? '保存修改' : `开始同步 (${selectedSourceTables.length} 表)`) }}
        </button>
      </div>
    </div>

    <!-- ==================== DATA_ONLY: 纯数据同步 ==================== -->
    <div v-if="mode === 'DATA_ONLY'" class="slide-enter-active">
      <div class="card mb-4">
        <h3 style="margin-bottom: 16px; font-weight: 600;">① 选择源数据库 & 要同步的表</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">数据库类型</label>
            <CustomSelect v-model="sourceDbType" :options="dbTypeOptions" placeholder="全部类型" />
          </div>
          <div class="form-group">
            <label class="form-label">数据源</label>
            <CustomSelect v-model="form.sourceDsId" :options="sourceDsOptions" placeholder="请选择数据源" :searchable="sourceDsOptions.length > 5" @change="onSourceDsChange" />
          </div>
          <div class="form-group" v-if="form.sourceDsId">
            <label class="form-label">数据库</label>
            <CustomSelect v-model="sourceSchema" :options="sourceSchemaOptions" placeholder="请选择数据库" :searchable="sourceSchemaOptions.length > 5" @change="loadSourceMetaAll" />
          </div>
        </div>
      </div>

      <!-- 表选择 -->
      <div class="card mb-4" v-if="sourceSchema">
        <div class="flex justify-between items-center" style="margin-bottom: 16px;">
          <h3 style="font-weight: 600;">选择表 <span class="text-sm text-muted" v-if="sourceTables.length">(已选 {{ selectedSourceTables.length }}/{{ filteredSourceTables.length }})</span></h3>
          <div class="flex gap-2">
            <input class="form-input" v-model="sourceSearch" placeholder="搜索..." style="width: 180px; padding: 6px 12px;" />
            <button class="btn btn-sm btn-primary" @click="sourceSelectAll"><SvgIcon name="check-square" :size="13" /> 全选</button>
            <button class="btn btn-sm btn-outline" @click="sourceDeselectAll"><SvgIcon name="square" :size="13" /> 清空</button>
            <button class="btn btn-sm btn-outline" @click="sourceInvert"><SvgIcon name="shuffle" :size="13" /> 反选</button>
          </div>
        </div>
        <div v-if="loadingTables" class="text-muted" style="padding: 20px; text-align: center;">加载中...</div>
        <div v-else class="table-wrapper" style="border:none; max-height: 350px; overflow-y: auto;">
          <table>
            <thead>
              <tr>
                <th style="width:40px;"><input type="checkbox" :checked="isSourceAllSelected" @change="toggleSourceAll" /></th>
                <th>表名</th><th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in filteredSourceTables" :key="t.tableName" @click="t._selected = !t._selected"
                  style="cursor:pointer;" :style="t._selected ? 'background:var(--primary-bg)' : ''">
                <td @click.stop><input type="checkbox" v-model="t._selected" /></td>
                <td class="font-mono">{{ t.tableName }}</td>
                <td class="text-sm text-muted">{{ t.comment || '-' }}</td>
              </tr>
              <tr v-if="filteredSourceTables.length === 0">
                <td colspan="3" style="text-align:center; color:var(--text-muted); padding:20px;">无匹配表</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 目标库 + 选项 -->
      <div class="card mb-4" v-if="sourceSchema" style="margin-top:16px;">
        <h3 style="margin-bottom: 16px; font-weight: 600;">② 选择目标数据库 & 同步选项</h3>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">数据库类型</label>
            <CustomSelect v-model="targetDbType" :options="dbTypeOptions" placeholder="全部类型" />
          </div>
          <div class="form-group">
            <label class="form-label">数据源</label>
            <CustomSelect v-model="form.targetDsId" :options="targetDsOptions" placeholder="请选择数据源" :searchable="targetDsOptions.length > 5" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">任务名称</label>
            <input class="form-input" v-model="form.taskName" placeholder="例: 用户表增量同步" />
          </div>
          <div class="form-group">
            <label class="form-label">同步模式</label>
            <CustomSelect v-model="form.syncMode" :options="syncModeOptions" />
          </div>
          <div class="form-group">
            <label class="form-label">批量大小</label>
            <input class="form-input" v-model.number="form.batchSize" type="number" />
          </div>
        </div>
      </div>

      <!-- 确认摘要 -->
      <div class="card mb-4" v-if="sourceSchema && form.targetDsId && selectedSourceTables.length > 0" style="border-color: var(--primary); background: var(--primary-bg);">
        <h3 style="margin-bottom: 12px; font-weight: 600;">同步摘要</h3>
        <div class="text-sm">
          <div><SvgIcon name="table" :size="13" /> <strong>{{ selectedSourceTables.length }}</strong> 张表</div>
          <div><SvgIcon name="shuffle" :size="13" /> 模式: <strong>{{ form.syncMode === 'INCREMENTAL' ? '增量同步（仅新增/更新）' : '全量同步' }}</strong></div>
          <div><SvgIcon name="alert-triangle" :size="13" /> 不会修改表结构，仅同步数据</div>
        </div>
      </div>

      <div style="text-align: right;" v-if="sourceSchema">
        <router-link to="/tasks" class="btn btn-outline" style="margin-right: 12px;">取消</router-link>
        <button class="btn btn-primary btn-lg" @click="submitDataOnly"
                :disabled="submitting || !form.targetDsId || !form.taskName || selectedSourceTables.length === 0">
          {{ submitting ? '保存中...' : (isEdit ? '保存修改' : `开始数据同步 (${selectedSourceTables.length} 表)`) }}
        </button>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { dsApi, metaApi, taskApi } from '../api'
import CustomSelect from '../components/CustomSelect.vue'
import { useToast } from '../composables/useToast'
import SvgIcon from '../components/SvgIcon.vue'

const router = useRouter()
const route = useRoute()
const toast = useToast()
const mode = ref('FULL_DATA')
const dataSources = ref([])
const submitting = ref(false)
const editId = ref(null)
const isEdit = computed(() => !!editId.value)

const sourceDbType = ref('')
const targetDbType = ref('')
const sourceSearch = ref('')
const loadingTables = ref(false)
const loadingSchemas = ref(false)
const sourceSchema = ref(null)
const sourceSchemaList = ref([])
const targetSchema = ref(null)
const targetSchemaList = ref([])

const sourceTables = ref([])
const sourceFunctions = ref([])
const sourceProcedures = ref([])
const sourceViews = ref([])
const sourceStats = ref({ loaded: false, tables: 0, functions: 0, procedures: 0, views: 0 })

const form = ref({
  sourceDsId: null, targetDsId: null,
  taskName: '', batchSize: 1000, syncMode: 'FULL',
  schemaStrategy: 'DROP_AND_CREATE',
  includeFunctions: true, includeProcedures: true, includeViews: true
})

const dbTypes = ['MYSQL', 'ORACLE', 'POSTGRESQL', 'DM', 'GAUSSDB', 'OCEANBASE']
const dbIcon = (type) => ({ MYSQL: 'M', ORACLE: 'O', POSTGRESQL: 'P', DM: 'D', GAUSSDB: 'G', OCEANBASE: 'OB' })[type] || 'DB'

// ---- 下拉选项 ----
const dbTypeOptions = computed(() => [
  { value: '', label: '全部类型' },
  ...dbTypes.map(t => ({ value: t, label: t, dbType: t }))
])

const sourceDsOptions = computed(() => [
  ...filteredSourceDs.value.map(ds => ({
    value: ds.id,
    label: `${ds.name}（${ds.dbName}）`,
    dbType: ds.dbType
  }))
])

const targetDsOptions = computed(() => [
  ...filteredTargetDs.value.map(ds => ({
    value: ds.id,
    label: `${ds.name}（${ds.dbName}）`,
    dbType: ds.dbType
  }))
])

const schemaStrategyOptions = [
  { value: 'CREATE_IF_NOT_EXISTS', label: '不存在则建表（安全）', icon: 'check-circle', color: '#10b981' },
  { value: 'DROP_AND_CREATE', label: 'DROP 后重建（覆盖）', icon: 'alert-triangle', color: '#ef4444' }
]

const syncModeOptions = [
  { value: 'FULL', label: '全量同步', icon: 'play', color: 'var(--primary)' },
  { value: 'INCREMENTAL', label: '增量同步', icon: 'trending-up', color: '#f59e0b' }
]

// 模式切换时，更新默认策略（仅限新建任务）
watch(mode, (newMode) => {
  if (isEdit.value) return
  if (newMode === 'FULL_DATA' || newMode === 'FULL_SCHEMA') {
    form.value.schemaStrategy = 'DROP_AND_CREATE'
  } else if (newMode === 'SELECTIVE') {
    form.value.schemaStrategy = 'CREATE_IF_NOT_EXISTS'
  }
})

// ---- 数据源过滤 ----
const filteredSourceDs = computed(() => {
  if (!sourceDbType.value) return dataSources.value
  return dataSources.value.filter(ds => ds.dbType === sourceDbType.value)
})
const filteredTargetDs = computed(() => {
  if (!targetDbType.value) return dataSources.value
  return dataSources.value.filter(ds => ds.dbType === targetDbType.value)
})

const sourceSchemaOptions = computed(() =>
  sourceSchemaList.value.map(s => ({ value: s, label: s }))
)

const targetSchemaOptions = computed(() =>
  targetSchemaList.value.map(s => ({ value: s, label: s }))
)

// ---- 表过滤 ----
const filteredSourceTables = computed(() => {
  if (!sourceSearch.value) return sourceTables.value
  const kw = sourceSearch.value.toLowerCase()
  return sourceTables.value.filter(t => t.tableName.toLowerCase().includes(kw) || (t.comment && t.comment.toLowerCase().includes(kw)))
})

const selectedSourceTables = computed(() => sourceTables.value.filter(t => t._selected))
const selectedFunctions = computed(() => sourceFunctions.value.filter(f => f._selected))
const selectedProcedures = computed(() => sourceProcedures.value.filter(p => p._selected))
const selectedViews = computed(() => sourceViews.value.filter(v => v._selected))

const isSourceAllSelected = computed(() => {
  const f = filteredSourceTables.value
  return f.length > 0 && f.every(t => t._selected)
})

// ---- 数据源切换: 加载 Schema 列表 ----
const onSourceDsChange = async () => {
  sourceSchema.value = null
  sourceSchemaList.value = []
  sourceStats.value = { loaded: false, tables: 0, functions: 0, procedures: 0, views: 0 }
  sourceTables.value = []
  sourceFunctions.value = []
  sourceProcedures.value = []
  sourceViews.value = []
  if (!form.value.sourceDsId) return
  loadingSchemas.value = true
  try {
    const res = await metaApi.listSchemas(form.value.sourceDsId)
    sourceSchemaList.value = res.data || []
  } catch (e) {
    toast.error('加载数据库列表失败')
  } finally {
    loadingSchemas.value = false
  }
}

// ---- 目标数据源切换: 加载 Schema 列表 ----
const onTargetDsChange = async () => {
  targetSchema.value = null
  targetSchemaList.value = []
  if (!form.value.targetDsId) return
  try {
    const res = await metaApi.listSchemas(form.value.targetDsId)
    targetSchemaList.value = res.data || []
  } catch (e) {
    toast.error('加载目标数据库列表失败')
  }
}

// ---- 源库元数据加载 (仅统计) ----
const loadSourceMeta = async () => {
  if (!form.value.sourceDsId || !sourceSchema.value) return
  sourceStats.value = { loaded: false, tables: 0, functions: 0, procedures: 0, views: 0 }
  try {
    const schema = sourceSchema.value
    const [tRes, fRes, pRes, vRes] = await Promise.all([
      metaApi.listTables(form.value.sourceDsId, schema),
      metaApi.listFunctions(form.value.sourceDsId, schema),
      metaApi.listProcedures(form.value.sourceDsId, schema),
      metaApi.listViews(form.value.sourceDsId, schema)
    ])
    sourceStats.value = {
      loaded: true,
      tables: (tRes.data || []).length,
      functions: (fRes.data || []).length,
      procedures: (pRes.data || []).length,
      views: (vRes.data || []).length
    }
  } catch (e) {
    toast.error('加载元数据失败')
  }
}

// ---- 源库完整加载 (SELECTIVE 模式) ----
const loadSourceMetaAll = async () => {
  if (!form.value.sourceDsId || !sourceSchema.value) return
  loadingTables.value = true
  sourceTables.value = []
  sourceFunctions.value = []
  sourceProcedures.value = []
  sourceViews.value = []
  try {
    const schema = sourceSchema.value
    const [tRes, fRes, pRes, vRes] = await Promise.all([
      metaApi.listTables(form.value.sourceDsId, schema),
      metaApi.listFunctions(form.value.sourceDsId, schema),
      metaApi.listProcedures(form.value.sourceDsId, schema),
      metaApi.listViews(form.value.sourceDsId, schema)
    ])
    sourceTables.value = (tRes.data || []).map(t => ({ ...t, _selected: false }))
    sourceFunctions.value = (fRes.data || []).map(f => ({ name: f, _selected: false }))
    sourceProcedures.value = (pRes.data || []).map(p => ({ name: p, _selected: false }))
    sourceViews.value = (vRes.data || []).map(v => ({ name: v, _selected: false }))
  } catch (e) { toast.error('加载元数据失败') }
  finally { loadingTables.value = false }
}

// ---- 表 全选/清空/反选 ----
const sourceSelectAll = () => { filteredSourceTables.value.forEach(t => t._selected = true) }
const sourceDeselectAll = () => { filteredSourceTables.value.forEach(t => t._selected = false) }
const sourceInvert = () => { filteredSourceTables.value.forEach(t => t._selected = !t._selected) }
const toggleSourceAll = () => { isSourceAllSelected.value ? sourceDeselectAll() : sourceSelectAll() }

// ---- 函数/存储过程 全选/清空 ----
const funcSelectAll = () => { sourceFunctions.value.forEach(f => f._selected = true) }
const funcDeselectAll = () => { sourceFunctions.value.forEach(f => f._selected = false) }
const procSelectAll = () => { sourceProcedures.value.forEach(p => p._selected = true) }
const procDeselectAll = () => { sourceProcedures.value.forEach(p => p._selected = false) }
const viewSelectAll = () => { sourceViews.value.forEach(v => v._selected = true) }
const viewDeselectAll = () => { sourceViews.value.forEach(v => v._selected = false) }

// ---- 提交: 全库数据 / 全库结构 ----
const submitBatch = async (taskType) => {
  submitting.value = true
  try {
    const payload = {
      taskType,
      taskName: form.value.taskName,
      sourceDsId: form.value.sourceDsId,
      targetDsId: form.value.targetDsId,
      tables: null,
      functions: form.value.includeFunctions ? [] : null,
      procedures: form.value.includeProcedures ? [] : null,
      views: form.value.includeViews ? [] : null,
      schemaStrategy: form.value.schemaStrategy,
      batchSize: form.value.batchSize,
      syncMode: form.value.syncMode,
      sourceSchema: sourceSchema.value,
      targetSchema: targetSchema.value
    }
    if (isEdit.value) {
      await taskApi.updateBatch(editId.value, payload)
      toast.success('任务已更新')
    } else {
      await taskApi.createBatch(payload)
      toast.success('任务已创建并开始执行')
    }
    setTimeout(() => router.push('/tasks'), 1000)
  } catch (e) {
    toast.error((isEdit.value ? '更新' : '创建') + '失败: ' + (e.message || ''))
  } finally { submitting.value = false }
}

// ---- 提交: 按需同步 ----
const submitSelective = async () => {
  submitting.value = true
  try {
    const payload = {
      taskType: 'SELECTIVE',
      taskName: form.value.taskName,
      sourceDsId: form.value.sourceDsId,
      targetDsId: form.value.targetDsId,
      tables: selectedSourceTables.value.map(t => t.tableName),
      functions: selectedFunctions.value.length > 0 ? selectedFunctions.value.map(f => f.name) : null,
      procedures: selectedProcedures.value.length > 0 ? selectedProcedures.value.map(p => p.name) : null,
      views: selectedViews.value.length > 0 ? selectedViews.value.map(v => v.name) : null,
      schemaStrategy: form.value.schemaStrategy,
      batchSize: form.value.batchSize,
      syncMode: form.value.syncMode,
      sourceSchema: sourceSchema.value,
      targetSchema: targetSchema.value
    }
    if (isEdit.value) {
      await taskApi.updateBatch(editId.value, payload)
      toast.success('任务已更新')
    } else {
      await taskApi.createBatch(payload)
      toast.success('任务已创建并开始执行')
    }
    setTimeout(() => router.push('/tasks'), 1000)
  } catch (e) {
    toast.error((isEdit.value ? '更新' : '创建') + '失败: ' + (e.message || ''))
  } finally { submitting.value = false }
}

// ---- 提交: 纯数据同步 ----
const submitDataOnly = async () => {
  submitting.value = true
  try {
    const payload = {
      taskType: 'DATA_ONLY',
      taskName: form.value.taskName,
      sourceDsId: form.value.sourceDsId,
      targetDsId: form.value.targetDsId,
      tables: selectedSourceTables.value.map(t => t.tableName),
      functions: null,
      procedures: null,
      views: null,
      schemaStrategy: 'CREATE_IF_NOT_EXISTS', // 不会用到，仅占位
      batchSize: form.value.batchSize,
      syncMode: form.value.syncMode,
      sourceSchema: sourceSchema.value,
      targetSchema: targetSchema.value
    }
    if (isEdit.value) {
      await taskApi.updateBatch(editId.value, payload)
      toast.success('任务已更新')
    } else {
      await taskApi.createBatch(payload)
      toast.success('任务已创建并开始执行')
    }
    setTimeout(() => router.push('/tasks'), 1000)
  } catch (e) {
    toast.error((isEdit.value ? '更新' : '创建') + '失败: ' + (e.message || ''))
  } finally { submitting.value = false }
}


// ---- 编辑模式: 回填数据 ----
const loadTaskForEdit = async (id) => {
  try {
    const res = await taskApi.getById(id)
    const task = res.data
    if (!task) { toast.error('任务不存在'); router.push('/tasks'); return }
    editId.value = task.id
    mode.value = task.taskType || 'FULL_DATA'
    form.value.taskName = task.taskName
    form.value.sourceDsId = task.sourceDsId
    form.value.targetDsId = task.targetDsId
    form.value.batchSize = task.batchSize || 5000
    form.value.syncMode = task.syncMode || 'FULL'
    form.value.schemaStrategy = task.schemaStrategy || 'CREATE_IF_NOT_EXISTS'
    form.value.includeFunctions = task.includeFunctions !== false
    form.value.includeProcedures = task.includeProcedures !== false
    form.value.includeViews = task.includeViews !== false
    // 匹配源/目标数据库类型筛选
    const srcDs = dataSources.value.find(d => d.id === task.sourceDsId)
    const tgtDs = dataSources.value.find(d => d.id === task.targetDsId)
    if (srcDs) sourceDbType.value = srcDs.dbType
    if (tgtDs) targetDbType.value = tgtDs.dbType
    // 加载 Schema 列表
    if (form.value.sourceDsId) {
      try {
        const schemaRes = await metaApi.listSchemas(form.value.sourceDsId)
        sourceSchemaList.value = schemaRes.data || []
        // 自动选中第一个 schema（编辑时回填）
        if (sourceSchemaList.value.length > 0) {
          sourceSchema.value = task.sourceSchema || sourceSchemaList.value[0]
        }
      } catch (e) { /* ignore */ }
    }
    // 加载目标 Schema 列表
    if (form.value.targetDsId) {
      try {
        const schemaRes = await metaApi.listSchemas(form.value.targetDsId)
        targetSchemaList.value = schemaRes.data || []
        if (targetSchemaList.value.length > 0) {
          targetSchema.value = task.targetSchema || targetSchemaList.value[0]
        }
      } catch (e) { /* ignore */ }
    }
    // 加载元数据
    if (mode.value === 'SELECTIVE' || mode.value === 'DATA_ONLY') {
      await loadSourceMetaAll()
      // 回填选中的表
      if (task.tableList) {
        let selected = []
        try { selected = JSON.parse(task.tableList) } catch { selected = task.tableList.split(',') }
        sourceTables.value.forEach(t => { if (selected.includes(t.tableName)) t._selected = true })
      }
    } else {
      await loadSourceMeta()
    }
  } catch (e) {
    toast.error('加载任务失败')
    router.push('/tasks')
  }
}

const handleKeyDown = (e) => {
  if (e.key === 'Escape') {
    router.push('/tasks')
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleKeyDown)
  const res = await dsApi.list()
  dataSources.value = res.data || []
  // 编辑模式
  if (route.params.id) {
    await loadTaskForEdit(route.params.id)
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<style scoped>
/* TaskCreate 是表单页面，需要整体滚动 */
.page-container {
  overflow-y: auto;
  padding-bottom: 20px;
}
.mode-tabs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 28px;
}
.mode-tab {
  background: var(--bg-card);
  border: 2px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}
.mode-tab::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--primary), var(--secondary));
  transform: scaleX(0);
  transition: transform 0.3s ease;
}
.mode-tab:hover {
  border-color: var(--primary-light);
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}
.mode-tab:hover::before {
  transform: scaleX(1);
}
.mode-tab.active {
  border-color: var(--primary);
  background: var(--primary-bg);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12), var(--shadow-md);
}
.mode-tab.active::before {
  transform: scaleX(1);
}
.mode-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--primary), var(--secondary));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 12px;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.2);
}
.mode-tab.active .mode-icon {
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.35);
}
.mode-title { font-size: 16px; font-weight: 700; margin-bottom: 6px; color: var(--text-primary); }
.mode-desc { font-size: 13px; color: var(--text-muted); line-height: 1.4; }

.stats-bar {
  display: flex;
  gap: 24px;
  margin-top: 16px;
  padding: 14px 20px;
  background: linear-gradient(135deg, var(--primary-bg), rgba(139, 92, 246, 0.04));
  border-radius: var(--radius-sm);
  border: 1px solid rgba(99, 102, 241, 0.1);
}
.stat-item {
  font-size: 14px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.stat-item strong {
  color: var(--primary);
  font-size: 16px;
}

.check-row {
  display: flex;
  gap: 24px;
  margin-top: 16px;
  padding: 14px 0;
}
.check-label {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--bg-input);
  transition: var(--transition);
}
.check-label:hover {
  border-color: var(--primary-light);
  background: var(--primary-bg);
}

.check-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  cursor: pointer;
  border-radius: var(--radius-xs);
  font-size: 13px;
  transition: background 0.15s ease;
  border: 1px solid transparent;
}
.check-item:hover {
  background: var(--bg-hover);
  border-color: var(--border);
}
</style>
