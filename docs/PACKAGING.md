# Windows 打包（TASK-058）

使用 JDK 21 的 `jpackage` 生成带 Java 运行时和 JavaFX 的应用目录，再由该目录生成 `.exe` 安装包。打包不修改 `pom.xml`、应用数据格式或版本控制记录。

## 构建环境

- 在 Windows x64 上使用 JDK 21 x64；`JAVA_HOME` 指向完整 JDK，需有 `jpackage.exe`。
- Maven 可从 PATH 找到，或通过 `-MavenCommand` 指定 `mvn.cmd` 完整路径。
- 默认执行全量测试，因此 Git 需要在构建进程的 PATH 中。
- `.exe` 安装包需要 WiX 3.x 的 `candle.exe`、`light.exe`。可从 [WiX 3.14.1 官方发布页](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm) 下载 `wix314-binaries.zip`，解压到 `target/tools/wix-3.14.1/`，或用 `-WixBin` 指定已有目录；无需安装系统软件。
- 首次构建需要访问 Maven 仓库，以取得已有运行时依赖及依赖复制插件。脚本调用固定版本的 Maven Dependency Plugin 3.8.1，不向项目添加应用依赖。

jpackage 的平台要求、应用目录和安装器选项见 [JDK 21 官方文档](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html)。

## 生成安装包

在项目根目录执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1
```

这里的执行策略只作用于本次 PowerShell 进程，不修改系统执行策略或全局环境变量。脚本结束后恢复构建进程中的 PATH 和 JAVA_HOME。

指定本机工具位置时：

```powershell
.\scripts\package-windows.ps1 `
  -JdkHome 'C:\Program Files\Eclipse Adoptium\jdk-21' `
  -MavenCommand 'D:\tools\maven\bin\mvn.cmd' `
  -WixBin 'D:\tools\wix-3.14.1'
```

参数中的路径应替换为本机真实位置。参数和路径使用 PowerShell 数组传递，支持空格。

默认输出到 `target/dist/<版本>-<时间>/`：

```text
AI Developer Hub-0.1.0.exe       安装包
SHA256SUMS.txt                 安装包 SHA-256
build-info.txt                构建工具与参数记录
image/AI Developer Hub/        完整应用目录
    AI Developer Hub.exe      应用启动器
    app/                      应用 JAR、JavaFX Windows 模块、启动配置
    runtime/                  裁剪后的 Java 运行时
```

`-OutputDirectory` 可指定尚不存在的输出目录。脚本拒绝覆盖已有输出，重试时使用新目录，不自动删除先前产物。中间文件保留在 `target/jpackage/`，可用于诊断构建失败。

Maven 的 `0.1.0-SNAPSHOT` 默认转换为安装器版本 `0.1.0`，不修改 POM；可用 `-AppVersion 0.1.1` 显式指定数字版本。不同正式版本应递增安装器版本，并保持脚本中的升级 UUID 不变；同版本反复安装的行为需单独验收。

默认执行 `mvn package`（包含测试），随后复制 runtime scope 依赖并生成产物。`-SkipTests` 仅用于已有独立测试结果的本地打包迭代，不替代发布前验收。

## 只生成应用目录

```powershell
.\scripts\package-windows.ps1 -Type app-image
```

此模式不需要 WiX。运行 `image/AI Developer Hub/AI Developer Hub.exe`；分发时必须携带整个 `AI Developer Hub` 目录，不能只复制启动器 `.exe`。

JavaFX 实现模块在 `app/lib/`，通过相对 `$APPDIR` 的 module path 加载；不依赖机器上的 JavaFX SDK。JDK 模块由 jpackage/jlink 收入运行时。脚本只收集 Windows JavaFX 实现 JAR，排除空的 Maven 占位 JAR 和测试依赖。

## 安装与数据

安装器采用当前用户安装，提供安装目录选择、开始菜单与桌面快捷方式选项。产物未进行代码签名，也不由脚本自动安装或发布。

用户无需额外安装 Java 或 JavaFX。Git、IDE、Maven、Node.js、Agent CLI 等外部开发工具仍由用户安装和配置。

用户数据继续保存在 `%APPDATA%\AI-Developer-Hub\`，与安装目录分离。旧 `.aidevhub` 数据在首次正常启动时按 TASK-053 规则迁移；打包过程本身不读取、迁移或删除真实用户数据。安装/卸载验收应特别确认已有数据的保留情况。

## 启动冒烟验证

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\test-windows-package.ps1 `
  -ImageDirectory '.\target\dist\<本次输出目录>\image\AI Developer Hub'
```

该脚本会短暂打开打包后的真实主窗口，再请求正常关闭。它给子进程提供隔离的 APPDATA 和 Java user.home，将 PATH 限制为 Windows 系统目录，验证无需 PATH 中的 Java 也能启动；不会使用真实配置、迁移真实数据或安装应用。诊断输出位于 `target/package-smoke/`。

人工验收仍包括：在目标 Windows 机器上运行安装器，检查安装目录和快捷方式、从安装目录启动、读取与保存数据、退出、升级和卸载。启动冒烟通过不代表已经验证完整安装/卸载流程。
