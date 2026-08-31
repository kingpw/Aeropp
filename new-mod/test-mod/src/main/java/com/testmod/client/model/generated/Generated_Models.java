package com.testmod.client.model.generated;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 由 model_loader.gradle 的 importModels 任务生成（手动执行，勿手改）。
 * 来源：model/ 下的 Blockbench 导出 Java 模型文件（改完模型后跑 gradlew importModels 更新）。
 */
public final class Generated_Models {

    private Generated_Models() {
    }

    private static final Map<String, ModelLayerLocation> LOCATIONS = createLocations();
    private static final Map<String, Supplier<LayerDefinition>> LAYERS = createLayers();

    private static Map<String, ModelLayerLocation> createLocations() {
        Map<String, ModelLayerLocation> m = new LinkedHashMap<>();
        m.put("flak_cannon", new ModelLayerLocation(ResourceLocation.parse("testmod:flak_cannon"), "main"));
        m.put("turret_model", new ModelLayerLocation(ResourceLocation.parse("testmod:turret_model"), "main"));
        m.put("twin_turret", new ModelLayerLocation(ResourceLocation.parse("testmod:twin_turret"), "main"));
        m.put("undead_sky_city", new ModelLayerLocation(ResourceLocation.parse("testmod:undead_sky_city"), "main"));
        m.put("warship", new ModelLayerLocation(ResourceLocation.parse("testmod:warship"), "main"));
        return m;
    }

    private static Map<String, Supplier<LayerDefinition>> createLayers() {
        Map<String, Supplier<LayerDefinition>> m = new LinkedHashMap<>();
        m.put("flak_cannon", Flak_Cannon::createBodyLayer);
        m.put("turret_model", Turret_Model::createBodyLayer);
        m.put("twin_turret", Twin_Turret::createBodyLayer);
        m.put("undead_sky_city", Undead_Sky_City::createBodyLayer);
        m.put("warship", Warship::createBodyLayer);
        return m;
    }

    /** 按名称取模型层位置 */
    public static ModelLayerLocation location(String name) {
        ModelLayerLocation loc = LOCATIONS.get(name);
        if (loc == null) {
            throw new IllegalArgumentException("未注册的模型层: " + name);
        }
        return loc;
    }

    /** 按名称取层定义（烘焙用） */
    public static LayerDefinition layer(String name) {
        Supplier<LayerDefinition> supplier = LAYERS.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("未注册的模型层: " + name);
        }
        return supplier.get();
    }

    /** 客户端注册全部模型层：onRegisterLayerDefinitions 里调用 */
    public static void registerAll(EntityRenderersEvent.RegisterLayerDefinitions event) {
        LOCATIONS.forEach((name, loc) -> event.registerLayerDefinition(loc, LAYERS.get(name)));
    }
}
