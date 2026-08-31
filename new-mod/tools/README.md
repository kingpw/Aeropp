# 建筑模板工具

`generate_templates.py` 使用 Python 标准库生成 GZIP 压缩的 NBT 文件到 `src/main/resources/data/aeropp_structures/structure/*.nbt`，并写出 `template_manifest.json`。Minecraft 1.21.1 的 `StructureTemplateManager` 使用单数 `structure` 目录和 `NbtIo.readCompressed`，因此目录和压缩格式都不能省略。调色板会保存 Create/Aeronautics 的完整 blockstate 属性（例如皮带端点和轴向），而不是只保存方块名。它不联网、不调用 Minecraft，不会修改 `mc开发/Aeropp` 或远程仓库。

运行：

```text
python tools/generate_templates.py
```

```text
python tools/validate_templates.py
python tools/validate_create_integration.py --mods <整合包>/mods
```

后一个脚本会检查必需的 Create/Aeronautics 方块状态，并在提供 `--mods` 时扫描实际 JAR 的 blockstate 资源；还会校验皮带的 `start/middle/end` 拓扑、Create 皮带长度上限、压机/搅拌机与 Depot/Basin 的两格工位关系，以及机械臂下方的竖向动力块。

生成脚本是模板的可复现来源。美术人员以后用结构编辑器替换 `.nbt` 时，应保留尺寸、结构 ID，并更新清单和人工审核记录。
