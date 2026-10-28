M0：项目基础
- Git 仓库
- .gitignore
- AGENTS.md
- README.md
- docs/REQUIREMENTS.md
- docs/ARCHITECTURE.md
- docs/ROADMAP.md
- docs/DEVELOPMENT.md
- Java 21
- Maven
- JUnit 5
- JavaFX
- 最小可启动窗口
  你现在基本就在这里，已经快完成。

M1：主界面骨架
- 左侧导航栏
- 顶部标题区
- 主内容区
- 页面切换机制
- Dashboard 占位页
- Projects 占位页
- Git 占位页
- Notes 占位页
- Agents 占位页
- Settings 占位页
  这一阶段先把软件“壳子”搭出来，不做真实业务。

M2：Project Manager
- Project 数据模型
- 项目列表
- 添加本地项目
- 删除项目
- 修改项目名称
- 保存项目路径
- 启动时恢复项目列表
- 选择当前项目
  这是第一个真正的核心业务模块。

M3：Project Launcher
- 用资源管理器打开项目目录
- 用终端打开项目目录
- 用 IDEA 打开项目
- 后期支持 VS Code / Cursor
- 检查路径是否存在
- IDE 路径设置

M4：Git 基础层
- 检测项目是不是 Git 仓库
- 获取当前分支
- 获取 Working Tree 状态
- Modified / Untracked / Deleted 文件
- Git 是否安装检测
- Git 命令执行封装
- 错误处理
  注意，这阶段先做“读取”，不要急着实现 commit / push。

M5：Git Dashboard
- 当前分支显示
- 修改文件数量
- Git 状态列表
- 最近 Commit
- Commit 作者
- Commit 时间
- Hash 简写
- 刷新按钮

M6：Dev Notes
- 每个项目自己的开发日志
- 新建日志
- 编辑日志
- 删除日志
- 日期
- Markdown / 普通文本支持可以后加

M7：TODO
- 每个项目自己的 TODO
- 添加任务
- 完成任务
- 删除任务
- 优先级
- 排序
- 未完成数量显示到 Dashboard

当前进度hear，TASK-040

M8：Dashboard
到这里再正式做 Dashboard，因为这时候才有真实数据。
Dashboard 可以显示：
- 当前项目
- Git 分支
- Git 修改数量
- 最近 Commit
- 未完成 TODO
- 最近开发日志
- 项目数量
- 快捷启动按钮

M9：Developer Environment
- Java 版本
- Maven 版本
- Git 版本
- Node.js 版本
- Python 版本
- IDEA 路径
- VS Code 路径
- Cursor 路径
- WSL 状态
- PATH 检查
  这个模块以后会非常适合你自己用。

M10：AI Agent Launcher
第一版先做启动器，不直接接 API。
比如：
Codex CLI      [启动]
Claude CLI     [启动]
Antigravity    [启动]
PowerShell     [启动]
WSL            [启动]

后面可以支持：
- 在当前项目路径打开 Agent
- 自动带项目上下文
- Agent 配置
- 自定义启动命令

M11：Settings
- IDEA 路径
- Cursor 路径
- VS Code 路径
- Terminal 选择
- Agent 命令
- 默认项目目录
- 主题
- 启动行为

M12：本地数据持久化
前面可以先简单存 JSON，到这里再统一整理。
比如：
%APPDATA%\AI-Developer-Hub\

里面：
settings.json
projects.json
notes/
todos/


M13：UI / UX 美化
这时再集中做：
- 深色主题
- 卡片式布局
- 图标
- Hover
- 空状态
- Toast
- Dialog
- CSS 统一
- 字体
- 间距
- 动画
  不建议前期花太多时间美化。

M14：稳定性
- 异常处理
- 路径不存在
- Git 未安装
- IDE 未安装
- 配置损坏
- JSON 损坏
- 中文路径
- 空格路径
- 长时间命令
- Process timeout

M15：测试体系
- ProjectService 测试
- GitService 测试
- Repository 测试
- 配置读写测试
- 边界测试
- 集成测试

M16：打包
- jpackage
- Windows .exe
- 图标
- 应用名称
- 版本号
- 安装目录
- 卸载
- 是否附带 JRE

M17：v1.0
v1.0 的标准我建议是：
你能真正每天打开它，并用它管理自己的开发项目。