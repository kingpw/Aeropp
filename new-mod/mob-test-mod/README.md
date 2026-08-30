# Aeropp Mob Test

这是 Aeropp 的敌对飞行生物实验模组。

## Sky Raider

- 实体 ID：`aeropp_mobtest:sky_raider`
- 继承原版 `Ghast`，保留恶魂的飞行移动控制、随机漂浮、目标筛选和攻击节奏。
- 通过 `ProjectileFactory` 创建投射物，当前实现为原版 `LargeFireball`，爆炸强度为 1。
- 玩家距离超过 16 格后增加追踪加速度，最大速度上限为基础值的 2.5 倍；速度有硬上限，不会无限累乘。
- 当前只支持命令生成，不加入自然生成表，便于测试。

## 构建

需要 Java 21：

```powershell
.\gradlew.bat build
```

运行任务：`runClient`、`runServer`、`runGameTestServer`。

## 游戏内验证

```text
/summon aeropp_mobtest:sky_raider ~ ~10 ~
```

验证攻击时观察 64 格内的恶魂式蓄力、火焰弹命中和爆炸行为；将玩家拉远后观察实体追踪速度是否提高。

## 投射物扩展

新增投射物时实现 `ProjectileFactory<P>`，并在实体构造或配置层注入，不要把具体投射物类型写死在 AI Goal 中。当前 `LargeFireballProjectileFactory` 是默认实现。
