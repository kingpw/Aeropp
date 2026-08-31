# Aeropp 离线结构审图

本工具链直接读取结构 NBT，并使用 `block-model-renderer` 从本地 Minecraft、Create、Aeronautics 与 Simulated JAR 解析方块状态、模型和材质。无需启动游戏。

```powershell
python tools/offline_viewer/render.py `
  src/main/resources/data/aeropp_structures/structure/clockwork_dock.nbt `
  --instance "E:\mc\亡者世界\.minecraft\versions\试制整合包"
```

输出完整外观、工作高度平面剖切、中轴纵剖和锅炉翼剖切。游戏客户端仅用于通过后的最终交互与性能测试。

## 输出视角

- `exterior_southeast.png`、`exterior_northwest.png`：完整外壳和屋面穿插。
- `plan_cut_high.png`：保留 Y≤8，检查隔墙、门洞、通道和功能区边界。
- `center_section_east.png`：中轴纵剖，检查嵌套屋顶、承重和飞艇净空。
- `boiler_section_east.png`：锅炉翼剖切，检查供热、罐体、供水、蒸汽输出和传动。

## 能力边界

渲染器读取标准 JSON 方块模型。Create/Aeronautics 中由运行时代码生成的特殊动画模型可能退化为静态或缺失模型，但墙体、屋顶、门洞、楼梯、管线、罐体和绝大多数建筑方块可准确审查。最终机械交互仍需在通过离线审图后进行一次游戏测试。
