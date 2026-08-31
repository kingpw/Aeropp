package com.testmod.client.model;

import com.testmod.client.model.generated.Generated_Models;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * 双联装舰载炮塔模型：几何来自 Blockbench 导出的 twin_turret
 * （model/twin_turret/twin_turret.java → generated/Twin_Turret.java，经 model_loader.gradle 自动接入）。
 * 结构：{@code turret_rf}（偏航组，含座圈与炮塔主体）→ {@code barrel_rf}（俯仰组，双联炮管）。
 *
 * <p>渲染/模型整合函数 {@link #renderAt}：传入<b>相对父类实体的模型空间位置</b>（格），
 * 自动复刻 LivingEntityRenderer 的模型空间变换链，把炮塔画到该挂点并按状态独立转向/俯仰
 * （损毁则炮管隐藏 + 炮塔歪斜）——与 {@link Ship_Turret_Model} 同套路，任何实体都可以直接调用。
 *
 * <p>旋转方向：实体模型空间 y 轴向下（渲染 scale(-1,-1,1)），绕 y 的 yRot=θ 等价世界 −θ；
 * 炮管建模朝 −z，抬头要 θ<0 → pitch 用 −1。实测方向反了改 <b>YAW_DIR / PITCH_DIR</b> 两个常量即可。
 */
public class Twin_Turret_Model {

    /** 模型层 = Generated_Models 注册的 twin_turret（模型层名 = 模型名） */
    public static final ModelLayerLocation LAYER_LOCATION = Generated_Models.location("twin_turret");

    /** 运行时旋转方向（同亡灵天城验证过的常量） */
    private static final float YAW_DIR = 1.0F;
    private static final float PITCH_DIR = -1.0F;
    /** 微调：让炮塔底面与判定箱底面齐平（模型空间 y，向上为负；按导出组偏移 (0,18,0) 推算） */
    private static final float EXPORT_DY = -0.125F;

    private final ModelPart turret;
    private final ModelPart barrel;

    public Twin_Turret_Model(ModelPart root) {
        this.turret = root.getChild("turret_rf");
        this.barrel = this.turret.getChild("barrel_rf");
    }

    public static LayerDefinition createBodyLayer() {
        // 用 Blockbench 建模/导出的双联炮塔几何（改模型 = 改 model/twin_turret/twin_turret.bbmodel 重新导出即可）
        return Generated_Models.layer("twin_turret");
    }

    /**
     * 整合函数：把一门双联炮塔画到父类实体的挂点。
     *
     * @param poseStack     渲染用的 PoseStack（调用方负责 push/pop，本函数内部自管一组变换）
     * @param buffer        已绑定贴图的 VertexConsumer（用 RenderType.entityCutoutNoCull 获取）
     * @param packedLight   光照
     * @param parent        父类实体（取其朝向做整体旋转）
     * @param partialTicks  插值系数
     * @param local         挂点位置，<b>模型空间、相对父类实体</b>（格）：x=右舷为负，z=舰首为负，y=上为负
     * @param baseYawDeg    炮塔基准朝向（度；0 = 正对模型 −z / 舰首）
     * @param state         炮塔状态 (yaw°, pitch°, hp)，hp≤0 则为损毁造型
     * @param flipped       倒挂挂载（舰腹炮塔）：绕舰首轴翻转 180° 挂在挂点下方，偏航/俯仰方向自动取反
     */
    public void renderAt(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                         LivingEntity parent, float partialTicks,
                         float[] local, float baseYawDeg, Vector3f state, boolean flipped) {
        boolean alive = state.z() > 0.0F;
        float yawDir = flipped ? -YAW_DIR : YAW_DIR;
        float pitchDir = flipped ? -PITCH_DIR : PITCH_DIR;
        this.turret.yRot = baseYawDeg * Mth.DEG_TO_RAD + (alive ? state.x() * Mth.DEG_TO_RAD * yawDir : 0.0F);
        this.turret.zRot = alive ? 0.0F : 0.3F;
        this.barrel.xRot = alive ? state.y() * Mth.DEG_TO_RAD * pitchDir : 0.0F;
        this.barrel.visible = alive;
        // 复刻 LivingEntityRenderer.render 的变换链（必须同序，否则与主体模型空间错位）：
        // scale(getScale) → [YRot(180−yBodyRot) → 死亡ZP] → scale(-1,-1,1) → translate(0,-1.501,0) → translate(挂点)
        poseStack.pushPose();
        float scale = parent.getScale();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTicks, parent.yBodyRotO, parent.yBodyRot)));
        // 死亡时随主体一起绕 Z 翻转（复刻 setupRotations 的死亡旋转）
        if (parent.deathTime > 0) {
            float f = ((float) parent.deathTime + partialTicks - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Math.min(f, 1.0F) * 180.0F));
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        poseStack.translate(local[0], local[1] + EXPORT_DY, local[2]);
        // 倒挂：绕舰首轴（z）滚转 180°，炮塔底面贴在挂点上、塔身垂向下方
        if (flipped) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        }
        this.turret.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
