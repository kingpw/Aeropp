# Aeropp 项目中的 packwiz

## 1. 作用

`packwiz` 是 Minecraft 整合包的开发管理工具，用于维护整合包元数据、模组下载信息和文件索引，并将项目导出为启动器可导入的安装包。

在 Aeropp 中，Git 仓库保存可审查的源文件，不直接提交下载得到的模组 JAR：

```text
pack.toml             # 整合包名称、版本、Minecraft 和 NeoForge 版本
index.toml            # 文件索引及哈希
mods/*.pw.toml        # 模组下载元数据，不是模组 JAR
config/               # 客户端或通用配置
defaultconfigs/       # 新世界默认服务端配置
kubejs/               # KubeJS 脚本（如果使用）
.packwizignore        # 不纳入整合包索引的文件规则
```

当前项目版本约束为 Minecraft `1.21.1`、NeoForge `21.1.248`，整合包版本为 `0.1.0`。

官方文档：

- [packwiz 安装](https://packwiz.infra.link/installation/)
- [快速开始](https://packwiz.infra.link/tutorials/creating/getting-started/)
- [添加模组](https://packwiz.infra.link/tutorials/creating/adding-mods/)
- [Modrinth 导出](https://packwiz.infra.link/reference/commands/packwiz/modrinth/export/)

## 2. 当前环境测试结果

测试日期：2026-08-28

packwiz 已从官方源码编译并放置于项目根目录：

```text
C:\mcpack\NeoAero\Aeropp\packwiz.exe
```

构建来源为 `github.com/packwiz/packwiz@latest`，本次解析到：

```text
v0.0.0-20260218225342-dfd8b68a4796
```

可执行文件 SHA-256：

```text
E99A2AECAE64E1DE27412B2B5EFFDCDDD72CC25EE6A3F197381213ECEECB0B64
```

本次可用性测试结果：

- `packwiz --help`：成功，退出码 `0`；
- `packwiz refresh`：在隔离的 Aeropp 测试副本中成功，退出码 `0`；
- `packwiz modrinth export`：成功生成 `.mrpack`，退出码 `0`；
- 导出包包含 `modrinth.index.json`，说明基本导出结构有效。

当前构建不支持 `packwiz --version` 或 `packwiz version`。验证工具可执行性时使用：

```powershell
.\packwiz.exe --help
```

如果本机已安装 Go，也可以按照官方说明安装最新版：

```powershell
go install github.com/packwiz/packwiz@latest
```

安装后重新打开终端，并确保 Go 的可执行文件目录已加入 `PATH`。

## 3. Aeropp 常用工作流

所有 packwiz 命令都应在本项目根目录执行，而不是在 `.minecraft` 游戏实例目录中执行：

```powershell
cd C:\mcpack\NeoAero\Aeropp
```

### 添加模组

推荐从 Modrinth 或 CurseForge 添加模组元数据：

```powershell
.\packwiz.exe modrinth install <模组链接、项目 ID 或 slug>
.\packwiz.exe curseforge install <模组链接、项目 ID 或 slug>
```

项目现有文档中的 `add` 写法是 `install` 的常用别名，例如：

```powershell
.\packwiz.exe modrinth add <模组链接或项目 ID>
```

添加后刷新索引：

```powershell
.\packwiz.exe refresh
```

### 更新模组

建议先创建功能分支，再更新并检查索引变化：

```powershell
git switch -c feature/update-mods
.\packwiz.exe update --all
.\packwiz.exe refresh
git diff --check
git status
```

确认版本、依赖和游戏测试结果后，再提交 `.pw.toml`、配置和脚本等源文件。

### 导出测试包

Aeropp 当前采用 Modrinth `.mrpack` 作为临时测试包格式：

```powershell
New-Item -ItemType Directory -Path build -Force | Out-Null
.\packwiz.exe modrinth export -o build/Aeropp-0.1.0.mrpack
```

将生成的 `build/Aeropp-0.1.0.mrpack` 导入 PCL2 或其他兼容启动器，并使用独立实例测试。CurseForge 格式可使用：

```powershell
.\packwiz.exe curseforge export
```

### 本地服务测试

需要使用 packwiz-installer 或类似方式进行本地更新测试时，可运行：

```powershell
.\packwiz.exe serve
```

默认会提供本地 `pack.toml` 服务，并在请求时刷新索引。此方式适合开发验证，不等同于最终发布包。

## 4. 提交与排错注意事项

- 每次手动添加、删除或修改整合包文件后运行 `packwiz refresh`。
- Git 中提交 `.pw.toml`、配置、脚本、资源和索引，不提交模组 JAR、日志、缓存、存档或生成的 `build/` 导出包。
- 不要在 `.minecraft` 或启动器实例目录直接运行 `packwiz init`；packwiz 项目目录应与游戏实例分开。
- 如果导出失败，先检查 `pack.toml`、`index.toml`、模组元数据、网络访问和 `packwiz --help` 输出。
- 如果新增了不应打包的文件，将规则写入 `.packwizignore`，然后重新运行 `packwiz refresh`。
- 在本项目中，导出成功不代表整合包可运行；仍需在 PCL2 中使用 64 位 Java 21 完成启动、多人、物理载具、存档和性能验证。

## 5. 试制整合包迁移记录

- 原始包：`C:\Users\KunYu\Downloads\试制整合包.mrpack`；
- 原始包 SHA-256：`BBB62CFD08DB2B8A1A7152D38534D8BBCF1E3EA6A667C531FA29331BE412F951`；
- 原始环境：Minecraft `1.21.1`、NeoForge `21.1.248`；
- 已转换为 54 个 `.pw.toml`：53 个模组元数据和 1 个光影包元数据；
- 已保留 `config/`、`tacz/`、`options.txt` 和 `TrashSlotSaveState.json`，并排除 PCL 本机设置及 `.bak` 文件；
- packwiz 自动补充了原包缺失的必需依赖 `WorldgenFeatureFix`；
- `mods/test-testmod-1.0.0.jar` 是 `new-mod/testmod` 构建的测试模组，许可标记为 All Rights Reserved；该测试产物使用 `test-` 文件名前缀并纳入 Git 与 packwiz 索引。
- 在分发包含 CurseForge 模组或自研 `testmod` 的 `.mrpack` 前，必须确认相应许可和再分发权限。
