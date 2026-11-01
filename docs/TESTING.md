# 测试体系（TASK-057）

测试基线为 Java 21、Maven、JUnit 5。沿用现有依赖，不引入测试框架或覆盖率插件。

## 职责与覆盖

当前没有名为 `ProjectService`、`GitService`、`Repository` 的生产类型，按实际职责验收，不为测试新增生产抽象。

| 验收范围 | 实际代码 | 主要测试 |
| --- | --- | --- |
| ProjectService 职责 | `ProjectList`、`CurrentProject`、项目关联的日志/TODO Service | `ProjectListTest`、`CurrentProjectTest`、`ProjectWorkflowIntegrationTest`、现有日志/TODO Service 测试 |
| GitService 职责 | Git Reader、Checker、`GitCommandExecutor` | 各 Git 单项测试及 `GitWorkflowIntegrationTest` |
| Repository 职责 | `ProjectStore`、`ProjectLogStore`、`ProjectTodoStore` | 各 Store 测试、项目联动与数据迁移集成测试 |
| 配置读写 | `AppSettings`、`IdePathSettings`、`AgentPathSettings` | 各配置测试、`SettingsIntegrationTest`、`CorruptDataTest` |
| 边界与错误 | 缺失/非法路径、空值、中文/空格、数量溢出、错误枚举、损坏数据、失效快照 | 对应模块的参数化测试及失败后原文件不变断言 |
| 进程集成 | 真实 Git/Java 子进程、命令超时、Windows 文件占用 | Git/环境执行器测试、`TemporaryProcessOutputTest` |
| UI 冒烟 | 深浅色、Dialog、Toast、Markdown、Git 页滚动 | `UiSmokeTest`，仅 Windows 执行 |

项目联动测试覆盖同名不同路径隔离、添加/去重/改名/移除、当前项目同步、重建对象后的数据恢复，以及独立 Service 实例拒绝过期快照。Git 联动测试覆盖持久化项目的仓库/分支/空提交历史/变更查询，并比较查询前后的 Git 元数据，验证查询不改写仓库。

配置隔离测试验证 Agent 文件损坏不影响其他配置读取，并可从备份恢复。当前持久化格式是 XML；写入 JSON、截断 XML 或无效字段均作为失败场景测试，不代表支持 JSON 存储。

## 运行方式

从项目根目录执行：

```bash
# 默认验收：全部测试
mvn test

# 快速逻辑与临时目录读写测试，不执行真实外部命令或 JavaFX 渲染
mvn "-DexcludedGroups=integration,ui" test

# 集成测试：真实进程、跨模块数据流程、迁移和 Windows 文件锁
mvn -Dgroups=integration test

# Windows JavaFX 冒烟测试
mvn -Dgroups=ui test

# 定位单个测试类
mvn -Dtest=ProjectWorkflowIntegrationTest test
```

`integration`、`ui` 使用 JUnit Tag；Maven Surefire 已支持分组，无需修改 `pom.xml`。部分 Git Reader 测试类同时含解析断言，整类归为集成测试，确保快速组完全不依赖真实 Git。

## 环境与隔离

- 全量和集成测试需要 Git 已加入当前进程的 PATH；缺失 Git 应修复测试环境，不以跳过集成断言获得通过结果。
- Java 子进程使用运行 Maven 的 JDK 路径。Windows 目标平台应使用 JDK 21；IDE 启动测试注入替代启动器，不实际打开用户的 IDE。
- JavaFX 渲染与 Windows 文件锁测试仅在 Windows 执行，其他系统会显示跳过。非 Windows 测试通过不能代替 Windows 验收。
- 数据使用 JUnit `@TempDir`、进程临时文件及 `target/` 预览目录，不读取或迁移真实 `%APPDATA%` 数据，不修改用户全局 Git 配置。
- Git 集成测试只在临时目录创建仓库和文件夹；不执行 `git add`、`commit`、`push`、`merge`、`tag` 等操作。部分既有测试使用底层对象/索引夹具，范围也限于临时仓库。
- 超时测试只终止测试自行创建的进程，不给用户启动的 IDE、终端、Agent 会话设置生命周期上限。
- 过期快照测试验证顺序发生的冲突检测，不声称已验证跨进程并发写入的原子性。

## 结果与人工验收

Surefire 报告位于 `target/surefire-reports/`，失败时记录测试名、原因及与当前修改的关系，不删除失败测试。JavaFX 预览位于 `target/task054-previews/`、`target/task055-previews/`。

自动测试不替代 Windows 真机上的安装路径、原生文件选择器、键盘导航、Hover、动画体验及不同 DPI 的人工验收。当前没有覆盖率统计，测试数量不代表覆盖率百分比。
