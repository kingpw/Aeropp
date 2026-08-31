# Aeropp

Minecraft 1.21.1 + NeoForge 21.1.248 + Java 21 的联合开发整合包。

本仓库保存整合包的可审查来源文件，使用 [packwiz](https://packwiz.infra.link/) 管理模组版本和文件索引，使用 PCL2 创建、导入并启动本地游戏实例。

## 当前状态

- 仓库：`kingpw/Aeropp`
- Minecraft：`1.21.1`
- Mod 加载器：`NeoForge 21.1.248`
- Java：`21`（请在 PCL2 的该实例设置中指定 64 位 Java 21）
- 发布方式：不发布到 Modrinth 或 CurseForge；GitHub 私有仓库用于联合开发
- 当前内容：54 个模组文件（53 个 packwiz 元数据项目和 1 个 `testmod` 测试模组 JAR）及 1 个光影包

## 目录约定

```text
pack.toml             # 整合包名称、版本、Minecraft 和 NeoForge 版本
index.toml            # packwiz 文件索引
mods/*.pw.toml        # 模组元数据，不直接提交模组 JAR
config/               # 客户端配置
defaultconfigs/       # 新世界默认配置
kubejs/               # KubeJS 脚本（如果使用）
resourcepacks/        # 资源包
shaderpacks/          # 光影包
docs/                 # 开发记录和协作说明
```

## 开发流程

首次使用：

```powershell
git clone https://github.com/kingpw/Aeropp.git
cd Aeropp
```

使用项目根目录中的本地 `packwiz.exe`，可以从 Modrinth 或 CurseForge 添加模组：

```powershell
.\packwiz.exe modrinth add <模组链接或项目 ID>
# 或：.\packwiz.exe curseforge add <模组链接或项目 ID>
.\packwiz.exe refresh
git add .
git commit -m "添加 xxx 模组"
git push
```

更新模组前先新建分支，并在 GitHub 提交 Pull Request：

```powershell
git switch -c feature/add-xxx
.\packwiz.exe update --all
.\packwiz.exe refresh
git add .
git commit -m "更新模组版本"
git push -u origin feature/add-xxx
```

## 在 PCL2 中测试

1. 确保 PCL2 使用 64 位 Java 21。
2. 使用 packwiz 导出临时 `.mrpack`：

   ```powershell
   New-Item -ItemType Directory -Path build -Force | Out-Null
   .\packwiz.exe modrinth export -o build/Aeropp-0.1.0.mrpack
   ```

3. 在 PCL2 中导入该整合包，使用独立的实例目录测试。
4. 测试通过后再提交配置、脚本和 `packwiz` 元数据；不要提交日志、存档或下载的模组 JAR。

由于仓库是私有的，本项目不依赖 packwiz 的在线自动更新链接；成员通过 GitHub `pull` 获取变更，再在本地导出并导入 PCL2。

## 协作约定

- `main` 只保留可运行版本；开发使用 `feature/*` 或 `fix/*` 分支。
- 提交信息说明具体改动，例如 `添加 Create`、`调整性能配置`。
- 涉及 Minecraft、NeoForge 或模组大版本变更时，必须在 PR 中记录兼容性和测试结果。
- 模组文件应从 Modrinth、CurseForge 或作者提供的可信来源获取，并遵守对应许可。
- 修改自研模组后，可运行 `.快速测试构建.ps1` 构建三个开发工程并将 JAR 原位同步到 PCL2 的 Aeropp 实例；请先关闭正在运行的游戏。
