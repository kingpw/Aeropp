package com.testmod.client.model;

import com.testmod.client.model.generated.Generated_Models;
import com.testmod.entity.Flak_Cannon_Entity;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 高射炮固定炮台的模型：几何来自 Blockbench 的 model/flak_cannon
 * （经 {@code gradlew importModels} 生成到 client/model/generated/Flak_Cannon.java）。
 *
 * <p>层次：base（底座+护板，静态）→ turret（炮塔座，随 yaw 旋转）→ barrel（炮管，随 pitch 俯仰）。
 * 旋转方向沿用已验证约定（模型空间 y 向下、1 格=16 单位）：yaw 用 +、pitch 用 −。
 * 炮塔状态来自实体的 {@link Flak_Cannon_Entity#getTurretState()}：{@code state = (yaw°, pitch°, hp)}。
 */
public class Flak_Cannon_Model extends EntityModel<Flak_Cannon_Entity> {

    /** 模型层位置（由 model_loader.gradle 注册：testmod:flak_cannon） */
    public static final ModelLayerLocation LAYER_LOCATION = Generated_Models.location("flak_cannon");

    /** 炮塔水平/俯仰旋转方向（同大型怪物飞艇验证过的常量） */
    private static final float YAW_DIR = 1.0F;
    private static final float PITCH_DIR = -1.0F;

    private final ModelPart base;
    private final ModelPart turret;
    private final ModelPart barrel;

    public Flak_Cannon_Model(ModelPart root) {
        this.base = root.getChild("base");
        this.turret = root.getChild("turret");
        this.barrel = this.turret.getChild("barrel");
    }

    public static LayerDefinition createBodyLayer() {
        return Generated_Models.layer("flak_cannon");
    }

    @Override
    public void setupAnim(Flak_Cannon_Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        Vector3f state = entity.getTurretState();
        boolean alive = state.z() > 0.0F;
        // 炮塔转 yaw（水平）、炮管抬 pitch（俯仰）；损毁则隐藏炮管 + 塔座歪斜
        this.turret.yRot = alive ? state.x() * Mth.DEG_TO_RAD * YAW_DIR : 0.0F;
        this.turret.zRot = alive ? 0.0F : 0.3F;
        this.barrel.xRot = alive ? state.y() * Mth.DEG_TO_RAD * PITCH_DIR : 0.0F;
        this.barrel.visible = alive;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                               int color) {
        this.base.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        this.turret.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }

    /** 渲染器（内部类，少一个文件） */
    public static class Renderer extends MobRenderer<Flak_Cannon_Entity, Flak_Cannon_Model> {

        private static final ResourceLocation TEXTURE =
                ResourceLocation.parse("testmod:textures/entity/flak_cannon.png");

        public Renderer(EntityRendererProvider.Context context) {
            super(context, new Flak_Cannon_Model(context.bakeLayer(LAYER_LOCATION)), 1.0F);
        }

        @Override
        public ResourceLocation getTextureLocation(Flak_Cannon_Entity entity) {
            return TEXTURE;
        }
    }
}
