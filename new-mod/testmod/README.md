# testmod

这是从 `src.zip` 整合到 Aeropp 的独立 NeoForge 模组工程，保留原有 `com.testmod` 包名与 `testmod` 资源命名空间。

## 内容

- 大型怪物飞艇、亡灵天城和战舰实体
- 多部件炮塔、高射炮、高射炮弹与激光僵尸
- 激光枪和加强喷气背包
- 配套模型、渲染器、粒子、网络包、纹理、配方和战利品表

## 依赖

- Minecraft `1.21.1`
- NeoForge `21.1.248`
- Create `6.0.10`
- Create Jetpack `5.2.1`

Gradle 使用 Modrinth 版本 ID 锁定与整合包一致的 Create 和 Create Jetpack，并从其 JAR 中提取编译所需的内嵌库。依赖产物只进入 Gradle 构建缓存与 `build/`，不提交到仓库。

## 构建

需要 Java 21：

```powershell
\.\gradlew.bat build
```

运行任务：`runClient`、`runServer`、`runGameTestServer`。

测试产物加入整合包时，文件名必须增加 `test-` 前缀；模组 ID 仍保持 `testmod`。
