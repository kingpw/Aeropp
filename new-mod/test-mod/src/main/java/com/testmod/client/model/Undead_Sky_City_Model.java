package com.testmod.client.model;
import com.testmod.client.model.generated.Generated_Models;
import com.testmod.entity.Undead_Sky_City_Entity;

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
 * 亡灵天城的模型（2048×1024 纹理）。
 *
 * <p>主体只有气囊 + 吊舱（几何来自 model/ 下的 Blockbench 导出，见 {@link Generated_Models}）；
 * <b>炮塔由独立的 {@link Ship_Turret_Model} 渲染</b>：渲染器用其整合函数
 * {@link Ship_Turret_Model#renderAt} 在 7 个挂点各画一份，挂点 = 相对父类实体的模型空间位置。
 */
public class Undead_Sky_City_Model extends EntityModel<Undead_Sky_City_Entity> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.parse("testmod:undead_sky_city"), "main");

    private final ModelPart body;

    public Undead_Sky_City_Model(ModelPart root) {
        this.body = root.getChild("body");
    }

    public static LayerDefinition createBodyLayer() {
        return Generated_Models.layer("undead_sky_city");
    }

    @Override
    public void setupAnim(Undead_Sky_City_Entity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
                               int packedOverlay, int color) {
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }

    /** 渲染器（内部类，少一个文件）：主体 + 7 门炮塔（Ship_Turret_Model 整合函数） */
    public static class Renderer extends MobRenderer<Undead_Sky_City_Entity, Undead_Sky_City_Model> {

        private static final ResourceLocation TEXTURE =
                ResourceLocation.parse("testmod:textures/entity/undead_sky_city.png");
        /** 炮塔独立贴图（ Blockbench 里保存的 turret_model，UV 区域与其一致） */
        private static final ResourceLocation TURRET_TEXTURE =
                ResourceLocation.parse("testmod:textures/entity/turret_model.png");

        private final Ship_Turret_Model turretModel;

        public Renderer(EntityRendererProvider.Context context) {
            super(context, new Undead_Sky_City_Model(context.bakeLayer(LAYER_LOCATION)), 3.0F);
            this.turretModel = new Ship_Turret_Model(context.bakeLayer(Ship_Turret_Model.LAYER_LOCATION));
        }

        @Override
        public ResourceLocation getTextureLocation(Undead_Sky_City_Entity entity) {
            return TEXTURE;
        }

        @Override
        public void render(Undead_Sky_City_Entity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            // 7 门炮塔：各自独立转向/俯仰/损毁，贴图用主人的 turret_model。
            // 挂点/朝向来自实体（由 TURRET_MOUNT 推导）：改实体配置 = 判定箱与模型同步移动
            VertexConsumer turretBuffer = buffer.getBuffer(RenderType.entityCutoutNoCull(TURRET_TEXTURE));
            float[][] render = entity.getTurretRender();
            for (int i = 0; i < render.length; i++) {
                this.turretModel.renderAt(poseStack, turretBuffer, packedLight, entity, partialTicks,
                        render[i], render[i][3], entity.getTurretState(i));
            }
        }
    }
}
