package com.testmod.client.renderer;

import com.testmod.entity.Laser_Zombie_Entity;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 尸界军渲染器：复用原版僵尸模型 + 皮肤（外观和原版僵尸一致），
 * 加装备层（{@link HumanoidArmorLayer}）渲染海龟壳/深绿皮革胸甲，
 * 加 {@link ItemInHandLayer} 渲染主手激光枪。
 * 激光光束粒子由网络包触发（Laser_Beam.spawnBeam），不在这里画。
 */
public class Laser_Zombie_Renderer extends MobRenderer<Laser_Zombie_Entity, ZombieModel<Laser_Zombie_Entity>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.parse("textures/entity/zombie/zombie.png");

    public Laser_Zombie_Renderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        // 装备层：海龟壳 + 深绿色皮革胸甲（支持皮革染色）
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        // 手上激光枪：原版 ItemInHandLayer 用物品模型渲染（laser_gun 普通物品模型，直接可见）
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(Laser_Zombie_Entity entity) {
        return TEXTURE;
    }
}
