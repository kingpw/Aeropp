package com.testmod.client.model;
import com.testmod.client.model.generated.Generated_Models;
import com.testmod.entity.Warship_Entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 战舰的模型（2048×2048 纹理，几何来自 model/warship/warship.bbmodel 的 Blockbench 导出）。
 *
 * <p>主体 = 舰体 + 甲板 + 上层建筑 + 桨舱 + 尾翼 + 吊舱（{@link Generated_Models} 的 warship 层）；
 * <b>8 门双联炮塔由 {@link Twin_Turret_Model} 独立渲染</b>：渲染器用其整合函数
 * {@link Twin_Turret_Model#renderAt} 在 8 个挂点各画一份（舰腹炮塔倒挂渲染），挂点 = 相对父类实体的模型空间位置。
 */
public class Warship_Model extends EntityModel<Warship_Entity> {

    public static final ModelLayerLocation LAYER_LOCATION = Generated_Models.location("warship");

    private final ModelPart body;

    public Warship_Model(ModelPart root) {
        this.body = root.getChild("body");
    }

    public static LayerDefinition createBodyLayer() {
        return Generated_Models.layer("warship");
    }

    @Override
    public void setupAnim(Warship_Entity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
                               int packedOverlay, int color) {
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }

    /** 渲染器（内部类，少一个文件）：主体 + 6 门双联炮塔（Twin_Turret_Model 整合函数） */
    public static class Renderer extends MobRenderer<Warship_Entity, Warship_Model> {

        private static final ResourceLocation TEXTURE =
                ResourceLocation.parse("testmod:textures/entity/warship.png");
        /** 双联炮塔独立贴图（Blockbench 里保存的 twin_turret，UV 区域与其一致） */
        private static final ResourceLocation TURRET_TEXTURE =
                ResourceLocation.parse("testmod:textures/entity/twin_turret.png");

        private final Twin_Turret_Model turretModel;

        public Renderer(EntityRendererProvider.Context context) {
            super(context, new Warship_Model(context.bakeLayer(LAYER_LOCATION)), 4.0F);
            this.turretModel = new Twin_Turret_Model(context.bakeLayer(Twin_Turret_Model.LAYER_LOCATION));
        }

        @Override
        public ResourceLocation getTextureLocation(Warship_Entity entity) {
            return TEXTURE;
        }

        @Override
        public void render(Warship_Entity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            // 8 门双联炮塔：各自独立转向/俯仰/损毁（6/7 号位为舰腹倒挂炮）。
            // 挂点/朝向来自实体（由 mount 推导）：改实体配置 = 判定箱与模型同步移动
            VertexConsumer turretBuffer = buffer.getBuffer(RenderType.entityCutoutNoCull(TURRET_TEXTURE));
            float[][] render = entity.getTurretRender();
            for (int i = 0; i < render.length; i++) {
                this.turretModel.renderAt(poseStack, turretBuffer, packedLight, entity, partialTicks,
                        render[i], render[i][3], entity.getTurretState(i), render[i][4] > 0.5F);
            }
        }
    }
}
