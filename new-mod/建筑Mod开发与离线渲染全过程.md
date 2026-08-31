# Aeropp 建筑 Mod 开发与离线渲染全过程

本文面向接手项目的 AI 或开发者，复盘 `Aeropp Structures` 从需求分析、NeoForge Mod 建立、结构模板生成、游戏内测试，到无须启动 Minecraft 的本地材质渲染和多轮识图修改的完整过程。

项目根目录：`E:\探索与学习\mc开发\建筑mod开发`

## 1. 最初目标

建筑 Mod 需要同时服务两类玩法：

- 战斗载具的可重复轰炸靶标；
- 探索、战斗和战利品驱动的地牢建筑。

核心地标 `clockwork_dock` 还需要表达机械动力与航空学的实际功能：原料与仓储、Create 加工线、锅炉动力、飞艇船坞、吊装龙门、控制塔、实验事故区和可实体化航空载具。

开发约束是 Minecraft `1.21.1`、NeoForge `21.1.230`、Create `6.x`，并读取整合包中实际安装的 Aeronautics Bundled/Simulated 资源。Mod ID 为 `aeropp_structures`。

## 2. 使用过的通用工具

| 工具 | 用途 | 是否必须下载 |
| --- | --- | --- |
| Git | 获取 Aeropp 仓库、比较改动；本轮不向远程仓库推送 | 建议 |
| PowerShell | Windows 路径处理、复制 JAR、计算哈希和运行脚本 | Windows 自带 |
| `rg`（ripgrep） | 快速查找源码、资源路径、结构 ID 和文档内容 | 强烈建议 |
| Python 3 | 生成/解析 GZIP NBT、验证资源、准备渲染场景 | 必须；当前脚本兼容 Python 3.8+
| Pillow | 早期二维平面图/剖面图输出 | 仅使用 `render_template_preview.py` 时需要 |
| Java 21 JDK | NeoForge 1.21.1 的正式编译、DataGen、客户端与服务器测试 | 正式构建必须 |
| Gradle / Gradle Wrapper | 构建 NeoForge Mod、运行 `runData`、客户端和服务端 | 正式构建必须 |
| NeoForge ModDevGradle | `build.gradle` 中的开发插件，版本 `2.0.144` | Gradle 自动解析 |
| Node.js 22 | 驱动离线三维渲染脚本 | 离线三维审图必须 |
| pnpm | 安装并锁定 Node 依赖；项目已有 `pnpm-lock.yaml` | 初次安装离线查看器时必须 |
| `block-model-renderer` | 从 Minecraft/Mod JAR 解析 blockstate、模型和材质并渲染 PNG；锁定版本 `2.15.2` | 离线三维审图核心依赖 |
| Three.js | `block-model-renderer` 的三维场景依赖，锁定解析版本 `0.162.0` | 由 pnpm 间接安装 |
| Java `jar` 工具 | Java 源码未变化时，把更新后的 NBT 资源写回现有 JAR | 临时资源封装可用；不能替代正式 Gradle 构建 |

参考项目名称：

- [`block-model-renderer`](https://github.com/ewanhowell5195/block-model-renderer)：Ewan Howell 的 Minecraft Java 方块模型渲染库；
- [`Minecraft Structure Viewer`](https://structure-viewer.ewanhowell.com/)：同作者的浏览器结构查看器，帮助确定离线渲染方案；
- [`NeoForge 1.21.1 Getting Started`](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)：Java 21、MDK 和正式构建要求；
- [`ModDevGradle`](https://github.com/neoforged/ModDevGradle)：NeoForge 官方 Gradle 开发工具链。

### 2.1 AI/Codex 侧调用过的工具

以下名称是 AI 执行环境的能力，不是需要通过 npm/pip 下载的软件：

| 工具名 | 本项目中的作用 |
| --- | --- |
| `exec_command` | 调用 PowerShell、Python、Java、pnpm 和校验命令，读取日志与哈希 |
| `apply_patch` | 修改 Python、JavaScript、Java、JSON 和 Markdown，避免整文件盲目覆盖 |
| `view_image` | 把离线查看器输出的 PNG 交给视觉模型检查空间、屋顶、墙体、承重和机器布局 |
| `web search` / 浏览器检索 | 查找 NeoForge 官方规范、Create 源码、结构 Mod 和大型建筑案例；参考资料只用于归纳规则，不复制资产 |

这里所谓“识图模型多轮修改”，实际链路是 `block-model-renderer` 先生成本地 PNG，再由 AI 环境的 `view_image` 读取图片并提出结构修改。若接手 AI 没有 `view_image`，可使用任何支持本地图片输入的多模态模型代替；不能用纯文本模型凭 NBT 坐标猜测最终外观。

## 3. 项目内自研工具

这些文件已经包含在项目中，不需要另行下载。

### 3.1 `tools/generate_templates.py`

建筑模板的可复现源码。它不启动 Minecraft，而是用 Python 标准库直接写出 GZIP 压缩 NBT：

```text
src/main/resources/data/aeropp_structures/structure/*.nbt
```

主要职责：

- 建立 NBT 根标签、调色板、方块位置和 blockstate 属性；
- 提供 `Structure`、`fill`、`set`、`carve` 等基础构筑操作；
- 提供 Create/Aeronautics 辅助函数，如轴、皮带、齿轮、流体罐、流体管、飞艇部件；
- 生成缺角平面、独立房间外壳、坡屋顶、拱形屋顶和功能设备；
- 同时写出 `tools/template_manifest.json`，记录尺寸、方块数、调色板数量和 SHA-256。

运行：

```powershell
python tools/generate_templates.py
```

重要经验：Minecraft 1.21.1 使用单数目录 `data/<namespace>/structure/`，不是 `structures/`；模板必须为 GZIP 压缩 NBT。此前“JAR 已安装但找不到模板”的根因就包括目录名和打包内容不符合这一约定。

### 3.2 `tools/validate_templates.py`

纯 Python NBT/资源静态校验器，负责：

- 解压并解析每个 NBT；
- 检查尺寸、调色板索引、方块位置和方块实体；
- 检查结构 JSON、模板池和 NBT 路径是否相互对应；
- 输出每个模板的文件大小、方块数和 SHA-256。

运行：

```powershell
python tools/validate_templates.py
```

### 3.3 `tools/validate_create_integration.py`

Create/Aeronautics 专项校验器。它不仅看方块名，还扫描测试整合包各 JAR 中的 `assets/<namespace>/blockstates/*.json`，包括 Aeronautics Bundled 的 `META-INF/jarjar/` 嵌套 JAR。

检查内容包括：

- 模板引用的方块是否确实存在于当前整合包；
- blockstate 属性组合是否有效；
- 皮带的 `start/middle/end` 拓扑和长度；
- Mechanical Press 与 Depot、Mixer 与 Basin 的空间关系；
- Mechanical Arm 下方是否有竖向动力/支撑；
- 高处机械组件是否通过六向连通体回到 Y=0 地基；
- `clockwork_dock` 是否退化为方块数过少的稀疏占位建筑。

运行：

```powershell
python tools/validate_create_integration.py --mods "E:\mc\亡者世界\.minecraft\versions\试制整合包\mods"
```

该脚本在最后一轮发现了机械臂、压机和控制塔火炮的独立悬空组件，促使生成器补上机壳支柱和传动轴。

### 3.4 `tools/render_template_preview.py`

第一代快速审图工具，使用 `Pillow` 读取 NBT 后输出四联二维图：

- 屋顶俯视；
- 指定 Y 层平面图；
- 南立面；
- 中央剖面。

运行示例：

```powershell
python tools/render_template_preview.py `
  src/main/resources/data/aeropp_structures/structure/clockwork_dock.nbt `
  build/previews/clockwork_dock.png --level 7
```

优点是快、依赖少；缺点是使用人工颜色表，不显示真实材质、楼梯朝向和三维遮挡。因此它适合检查占地、墙线和楼层，不适合判断建筑美感。

### 3.5 `tools/offline_viewer/`

第二代真实材质离线三维查看器，是本轮最重要的改进。完整链路如下：

```text
clockwork_dock.nbt
       │
       ▼
prepare_scene.py ──→ clockwork_dock.json + clockwork_dock.packs.json
       │
       ▼
render_structure.mjs + block-model-renderer
       │
       ▼
外观 PNG / 切顶 PNG / 中轴剖面 PNG / 锅炉剖面 PNG / 装配厅剖面 PNG
```

各文件职责：

- `render.py`：一键入口，依次调用 Python 场景准备和 Node 渲染；
- `prepare_scene.py`：解析 NBT 调色板和方块坐标，定位整合包的原版、Create、Aeronautics、Simulated JAR；
- `render_structure.mjs`：调用 `block-model-renderer` 建立正交相机、灯光和场景，按坐标过滤得到切顶/剖面；
- `package.json`：声明 `block-model-renderer` 和本地 Node 22；
- `pnpm-lock.yaml`：锁定 `block-model-renderer 2.15.2`、Node `22.23.2` 等实际版本；
- `cache/`：从 Aeronautics Bundled 的 Jar-in-Jar 中提取的 `aeronautics-neoforge.jar` 与 `simulated.jar`。

首次安装：

```powershell
Set-Location tools\offline_viewer
pnpm install
Set-Location ..\..
```

一键渲染：

```powershell
python tools/offline_viewer/render.py `
  src/main/resources/data/aeropp_structures/structure/clockwork_dock.nbt `
  --instance "E:\mc\亡者世界\.minecraft\versions\试制整合包" `
  --output build\offline-viewer\review
```

固定输出：

- `exterior_southeast.png`：东南外观；
- `exterior_northwest.png`：西北外观；
- `plan_cut_high.png`：只保留 Y≤8，检查房间拓扑、隔墙、门洞和暗道；
- `center_section_east.png`：主厅中轴剖面，检查屋顶穿插、飞艇净空和承重；
- `boiler_section_east.png`：锅炉翼剖面，检查供水、加热、蒸汽输出、传动和检修层；
- `assembly_section_north.png`：装配厅剖面，检查加工线和屋盖。

这种方法的关键价值是：修改 Python 生成器后，只需重新生成 NBT 和运行离线渲染，约几十秒即可得到全套视图，不必每次重新启动客户端、进入世界、放置 81×40×70 建筑再飞行检查。

## 4. NeoForge Mod 的实现方式

本 Mod 采取“少量 Java + 大量数据资源”的结构。

### 4.1 Java 入口

`Aeropp_Structures.java` 使用 NeoForge `DeferredRegister` 注册：

- 8 个建筑测试物品；
- 一个“ Aeropp 建筑测试”创造模式物品栏。

测试物品右键后直接从 `StructureTemplateManager` 读取相应 NBT，并在服务端放置建筑。

### 4.2 快速放置

`StructureSummonItem.java` 是创造模式召唤物品实现；`Structure_Test_Commands.java` 提供管理员命令：

```mcfunction
/aeropp_structures list
/aeropp_structures place clockwork_dock
```

也可以使用原版命令：

```mcfunction
/place template aeropp_structures:clockwork_dock
```

这些入口解决了“只能等待世界生成、找不到模板、每次测试过慢”的问题。

### 4.3 数据驱动世界生成

资源目录还包括：

- `worldgen/structure/*.json`：单个结构定义；
- `worldgen/template_pool/*/start.json`：模板池；
- `worldgen/structure_set/*.json`：生成间距、盐值与权重；
- `tags/worldgen/biome/*.json`：允许生成的生物群系；
- `loot_table/chests/*.json`：靶标、地牢、地标三类战利品。

这种划分使建筑 NBT、自然生成规则和奖励可以分别调整。

## 5. 建筑设计的迭代过程

### 阶段一：简单模板与可安装 JAR

先建立 8 个建筑原型，打通 NeoForge 资源目录、结构 JSON、模板池、自然生成、战利品和 JAR 打包。早期建筑主要验证“能否加载和生成”，几何与功能较简单。

### 阶段二：解决“模板找不到”

重点检查：

1. NBT 是否位于 JAR 内的 `data/aeropp_structures/structure/`；
2. 是否为 GZIP 压缩 NBT；
3. namespace 和 ID 是否一致；
4. JAR 中是否确实包含模板，而不是只包含 Java class；
5. 旧 JAR 备份是否仍以 `.jar` 结尾而被重复加载。

最终实现单 JAR 直接放入 `mods` 即可使用，所有模板包含在 JAR 内。

### 阶段三：机械动力功能化

从“把原版方块替换成 Create 方块”转向按功能组织空间：

- 仓储靠近物流入口；
- 加工线按 Depot/Press、Basin/Mixer、Item Vault 顺序布置；
- 锅炉放入独立砖砌动力翼；
- 航空学部件只出现在船坞、控制和试验区域；
- 龙门、轴承、管线和高架设备必须解释真实用途。

### 阶段四：参考大型建筑并重构巨构

参考过的类型包括 Create 工厂、IDAS、When Dungeons Arise、大型飞艇和航母工厂。提炼出的规则是：先设计体量与功能区，再放机器；复杂几何来自主厅、侧翼、塔楼、高差、屋顶和交通层，而不是随机凹凸或复制小房子。

`clockwork_dock` 最终扩展为 `81×40×70`，核心区域包括：

- 拱顶飞艇主船坞；
- 独立装配厅；
- 封闭锅炉房；
- 物流仓库；
- 多层控制塔；
- 事故试验间；
- 四组落地船架、双龙门和半成品飞艇。

### 阶段五：三轮离线识图修改

第一轮观察到：旧结构的房间平面互相重叠，导致侧翼屋顶伸入主厅、缺少分隔墙、锅炉房与大厅完全贯通。解决方式不是继续局部补洞，而是把六个功能区改为互不重叠的独立 footprint，并只通过明确门洞连接。

第二轮观察到：空间硬伤解决后，主山墙过空、锅炉设备流程不清、大门像没有承重逻辑的道具。随后加入山墙工业装饰、门库、Gantry Shaft/Carriage 横移门、锅炉供水总管、齿轮箱和航空对接设备。

第三轮观察到：锅炉入口仍显得过于开放，检修层支撑不足。最终缩小门洞、安装黄铜双扇防火门，加入连续检修地板、护栏、爬梯与落地支柱，并用拓扑校验器消除机械臂、压机、火炮等剩余悬空组件。

每轮固定执行：

```text
修改 generate_templates.py
→ generate_templates.py
→ validate_templates.py
→ validate_create_integration.py
→ offline_viewer/render.py
→ 识别六张 PNG
→ 根据空间问题继续修改
```

## 6. 构建、封装与部署

正式方式应使用 Java 21：

```powershell
.\gradlew build
.\gradlew runData
```

本轮后期只修改 NBT、没有修改已经编译的 Java class，因此曾使用 JDK 的 `jar` 工具将单个新模板写回现有开发 JAR：

```powershell
jar uf build/libs/aeropp_structures-0.1.0.jar `
  -C src/main/resources `
  data/aeropp_structures/structure/clockwork_dock.nbt
```

这是快速资源迭代办法，不等价于完整 Gradle 构建。只要 Java、元数据或其他资源发生变化，就必须重新执行 Gradle 构建。

部署前后使用 SHA-256 和 ZIP 完整性检查确认：

- JAR 能完整读取；
- JAR 内只有一个 `clockwork_dock.nbt` 条目；
- JAR 内模板哈希与源码资源模板相同；
- 旧版备份使用 `.backup-*` 后缀，不能继续以 `.jar` 结尾。

当前测试部署位置：

```text
E:\mc\亡者世界\.minecraft\versions\试制整合包\mods\aeropp_structures-0.1.0.jar
```

## 7. 哪些事情离线工具不能代替

`block-model-renderer` 读取标准 blockstate、JSON 模型和材质，但不能完整执行 Minecraft/Mod 的运行时代码。因此以下内容仍需最终启动客户端或服务器测试：

- Gantry、门、轴承和传动系统是否真正运动；
- Create 的应力、转速、过滤器和配方；
- Aeronautics/Simulated 物理装配、胶合、飞行和对接；
- 方块实体 NBT、战利品、交互界面和实体；
- 世界生成地形适配、区块加载时间、光照和多人性能；
- 动态模型、动画模型与特殊渲染器。

正确流程是“多轮离线审图，只进行一次或少量最终游戏内验收”，而不是完全取消游戏测试。

## 8. 推荐接手顺序

1. 安装 Java 21、Python、Node 22、pnpm、Git 和 ripgrep。
2. 在 `tools/offline_viewer` 执行 `pnpm install`。
3. 确认测试实例中有匹配版本的 Minecraft、Create 和 Aeronautics Bundled JAR。
4. 运行三个 Python 生成/校验脚本。
5. 运行离线查看器并检查六张 PNG。
6. 修改时优先改 `generate_templates.py`，不要直接手工修改二进制 NBT，否则下一次生成会覆盖修改。
7. 离线审图通过后用 Java 21/Gradle 完整构建。
8. 备份测试世界与旧 JAR，再部署到 `mods`。
9. 用创造栏测试物品、`/aeropp_structures place` 或 `/place template` 做最终游戏内验收。

## 9. 最重要的经验

- 建筑生成器应是唯一可复现来源，NBT 是构建产物。
- 先确定房间图、相邻关系和承重，再添加 Create 机器。
- 每个功能房间必须有明确输入、处理、输出、维护和交通逻辑。
- 不要用机械方块替换装饰方块来假装“机械动力结合”。
- 不要只看外观截图；切顶和剖面更容易发现嵌套屋顶、黑房间和悬空结构。
- 校验脚本需要读取玩家实际安装的 Mod JAR，而不能仅凭记忆猜测方块 ID 和 blockstate。
- 离线渲染负责高频空间迭代，Minecraft 负责低频交互与性能验收。
- 大型建筑的史诗感来自路线、尺度、功能层级和唯一视觉高潮，不来自随机堆砌细节。
