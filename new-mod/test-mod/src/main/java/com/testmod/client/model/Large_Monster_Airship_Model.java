package com.testmod.client.model;
import com.testmod.entity.Large_Monster_Airship_Entity;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 大型怪物飞艇的模型（Blockbench 建模导出，1024×512 纹理，23 个部件）。
 *
 * 层次：body → turret_left / turret_right → barrel_left / barrel_right
 * 注意：modded_entity 导出会翻转 x 轴，所以 Blockbench 里的 turret_left（+x）
 * 在 Java 中位于 x = -48，也就是**实体的右舷**（MC 模型 x 负 = 实体右侧）。
 * 为免混淆，本类字段按舷别命名：STAR = 右舷（x=-48），PORT = 左舷（x=+48）。
 */
public class Large_Monster_Airship_Model extends EntityModel<Large_Monster_Airship_Entity> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.parse("testmod:large_monster_airship"), "main");

    /** 炮塔基准朝向（导出的 PartPose 里已带：右舷 +90°、左舷 -90°） */
    private static final float BASE_STAR = 1.5708F;
    private static final float BASE_PORT = -1.5708F;
    /**
     * 炮塔水平/俯仰旋转方向。
     * 推导：实体模型空间 y 轴向下（渲染时 scale(-1,-1,1)），绕 y 旋转 θ 等价于世界 −θ；
     * 而 {@code horizAngle} 用的是 atan2(z,x)（+x→+z 为正）= 世界绕 −y，两次反转抵消 → yaw 用 +1。
     * 炮管建模朝 −z，R_x(θ)·(0,0,−1)=(0,sinθ,−cosθ)，模型 +y 朝下 → 抬头要 θ<0 → pitch 用 −1。
     */
    private static final float YAW_DIR = 1.0F;
    private static final float PITCH_DIR = -1.0F;

    private final ModelPart body;
    private final ModelPart turretStar, barrelStar;
    private final ModelPart turretPort, barrelPort;

    public Large_Monster_Airship_Model(ModelPart root) {
        this.body = root.getChild("body");
        this.turretStar = this.body.getChild("turret_left");
        this.barrelStar = this.turretStar.getChild("barrel_left");
        this.turretPort = this.body.getChild("turret_right");
        this.barrelPort = this.turretPort.getChild("barrel_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(632, 182).addBox(-32.0F, -44.0F, -96.0F, 64.0F, 32.0F, 64.0F, new CubeDeformation(0.0F))
                .texOffs(632, 182).addBox(-32.0F, -44.0F, -32.0F, 64.0F, 32.0F, 64.0F, new CubeDeformation(0.0F))
                .texOffs(632, 182).addBox(-32.0F, -44.0F, 32.0F, 64.0F, 32.0F, 64.0F, new CubeDeformation(0.0F))
                .texOffs(464, 0).addBox(-24.0F, -12.0F, -80.0F, 48.0F, 8.0F, 160.0F, new CubeDeformation(0.0F))
                .texOffs(344, 182).addBox(-12.0F, -4.0F, -60.0F, 24.0F, 4.0F, 120.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-28.0F, -50.0F, -88.0F, 56.0F, 6.0F, 176.0F, new CubeDeformation(0.0F))
                .texOffs(0, 182).addBox(-32.0F, -56.0F, -84.0F, 4.0F, 6.0F, 168.0F, new CubeDeformation(0.0F))
                .texOffs(0, 182).addBox(28.0F, -56.0F, -84.0F, 4.0F, 6.0F, 168.0F, new CubeDeformation(0.0F))
                .texOffs(888, 182).addBox(-14.0F, -62.0F, 36.0F, 28.0F, 12.0F, 40.0F, new CubeDeformation(0.0F))
                .texOffs(112, 356).addBox(-8.0F, -64.0F, 44.0F, 16.0F, 2.0F, 24.0F, new CubeDeformation(0.0F))
                .texOffs(192, 356).addBox(-8.0F, -58.0F, -80.0F, 16.0F, 8.0F, 20.0F, new CubeDeformation(0.0F))
                .texOffs(304, 356).addBox(-26.0F, -62.0F, 78.0F, 12.0F, 12.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(304, 356).addBox(14.0F, -62.0F, 78.0F, 12.0F, 12.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(264, 356).addBox(-32.0F, -38.0F, -8.0F, 4.0F, 16.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(264, 356).addBox(28.0F, -38.0F, -8.0F, 4.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition turret_left = body.addOrReplaceChild("turret_left", CubeListBuilder.create().texOffs(880, 0).addBox(-16.0F, -16.0F, -16.0F, 32.0F, 32.0F, 32.0F, new CubeDeformation(0.0F))
                .texOffs(0, 356).addBox(-14.0F, -20.0F, -14.0F, 28.0F, 4.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-32.0F, -30.0F, 0.0F, 0.0F, BASE_STAR, 0.0F));

        turret_left.addOrReplaceChild("barrel_left", CubeListBuilder.create().texOffs(0, 388).addBox(-6.0F, -6.0F, -44.0F, 12.0F, 12.0F, 28.0F, new CubeDeformation(0.0F))
                .texOffs(352, 356).addBox(-7.0F, -7.0F, -50.0F, 14.0F, 14.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition turret_right = body.addOrReplaceChild("turret_right", CubeListBuilder.create().texOffs(880, 0).addBox(-16.0F, -16.0F, -16.0F, 32.0F, 32.0F, 32.0F, new CubeDeformation(0.0F))
                .texOffs(0, 356).addBox(-14.0F, -20.0F, -14.0F, 28.0F, 4.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(32.0F, -30.0F, 0.0F, 0.0F, BASE_PORT, 0.0F));

        turret_right.addOrReplaceChild("barrel_right", CubeListBuilder.create().texOffs(0, 388).addBox(-6.0F, -6.0F, -44.0F, 12.0F, 12.0F, 28.0F, new CubeDeformation(0.0F))
                .texOffs(352, 356).addBox(-7.0F, -7.0F, -50.0F, 14.0F, 14.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 1024, 512);
    }

    @Override
    public void setupAnim(Large_Monster_Airship_Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        applyTurret(this.turretStar, this.barrelStar, BASE_STAR, entity.getTurretState(true));
        applyTurret(this.turretPort, this.barrelPort, BASE_PORT, entity.getTurretState(false));
    }

    /** state = (yaw°, pitch°, hp)：转动炮塔，损毁则隐藏炮管并让炮塔歪斜 */
    private static void applyTurret(ModelPart turret, ModelPart barrel, float baseYaw, Vector3f state) {
        boolean alive = state.z() > 0.0F;
        turret.yRot = baseYaw + (alive ? state.x() * Mth.DEG_TO_RAD * YAW_DIR : 0.0F);
        turret.zRot = alive ? 0.0F : 0.3F;
        barrel.xRot = alive ? state.y() * Mth.DEG_TO_RAD * PITCH_DIR : 0.0F;
        barrel.visible = alive;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                               int color) {
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }

    /** 渲染器（内部类，少一个文件） */
    public static class Renderer extends MobRenderer<Large_Monster_Airship_Entity, Large_Monster_Airship_Model> {

        private static final ResourceLocation TEXTURE =
                ResourceLocation.parse("testmod:textures/entity/large_monster_airship.png");

        public Renderer(EntityRendererProvider.Context context) {
            super(context, new Large_Monster_Airship_Model(context.bakeLayer(LAYER_LOCATION)), 3.0F);
        }

        @Override
        public ResourceLocation getTextureLocation(Large_Monster_Airship_Entity entity) {
            return TEXTURE;
        }
    }
}
