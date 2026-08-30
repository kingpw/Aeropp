# Aeropp Building Test

这是 Aeropp 的第一个世界生成架构实验模组，目标是验证数据驱动结构能否把原版末地城（以及其 Jigsaw 规则中的末地船分支）放入主世界。

## 行为

- 命名空间：`aeropp_buildtest`
- 结构：`aeropp_buildtest:overworld_end_city`
- 生物群系：`minecraft:is_overworld`
- 生成步骤：`surface_structures`
- 放置类型：`random_spread`
- `spacing = 34`、`separation = 26`、`salt = 10387312`，与原版村庄结构集使用同一组间距参数。
- 结构起始池复用 `minecraft:end_city/towers`，末地船是否出现仍由原版末地城模板池的随机分支决定。

## 构建

需要 Java 21。首次构建需要联网下载 Gradle 和 NeoForge 依赖：

```powershell
.\gradlew.bat build
```

如果当前目录没有 Gradle Wrapper，可使用与工程兼容的 Gradle 生成 Wrapper：

```powershell
gradle wrapper --gradle-version 8.8
.\gradlew.bat build
```

构建产物位于 `build/libs/`，运行目录位于 `run/`。

## 游戏内验证

1. 使用 `runServer` 或 `runClient` 启动测试环境。
2. 在主世界执行：

```text
/locate structure aeropp_buildtest:overworld_end_city
```

3. 传送到结果位置，确认末地城主体和可能出现的末地船分支。
4. 创建第二个世界，比较多个候选位置的间距和生成频率。
5. 重启世界后再次执行定位，确认结构不会因重载重复生成。

## 当前限制

- 这是静态世界生成实验，不包含建筑实例 ID、摧毁检测、自动修复和任务奖励。
- 复用原版结构池意味着末地船不是独立的必然结构，而是末地城内部的原版随机分支。
- `spacing`/`separation` 的候选网格与村庄一致，不代表所有生物群系中生成数量完全相同；地形和结构合法性检查仍会影响最终结果。
