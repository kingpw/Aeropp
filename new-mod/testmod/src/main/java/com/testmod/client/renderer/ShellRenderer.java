package com.testmod.client.renderer;
import com.testmod.entity.ShellEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 炮弹渲染器：把 testmod:shell 画成一个长方体（金属炮弹）。
 *
 * 不用 ModelPart（单一立方体 UV 会采样到亚像素区域导致细节丢失），
 * 这里直接在 PoseStack 上把 6 个面画出来，每面给法线 + 贴图坐标，朝向飞行方向。
 */
public class ShellRenderer extends EntityRenderer<ShellEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.parse("testmod:textures/entity/shell.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);

    // 长方体半长：x=0.25,y=0.25,z=0.5 → 整体 0.5×0.5×1.0 格（弹身沿 z 轴拉长）
    private static final float HX = 0.25F, HY = 0.25F, HZ = 0.5F;

    // 复用实例，避免每帧 new Vector3f/Quaternionf（mulPose 只读不修改传入四元数，可安全复用）
    private static final Vector3f FORWARD = new Vector3f(0.0F, 0.0F, 1.0F);
    private final Vector3f dirVec = new Vector3f();
    private final Quaternionf renderRot = new Quaternionf();

    public ShellRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ShellEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // 等比例缩小到 60%（整体缩放，中心不动）
        poseStack.scale(0.6F, 0.6F, 0.6F);
        // 直接用速度向量确定朝向：让弹体长轴(+z)对齐飞行方向。
        // 不看基类 rotateTowardsMovement 的 yRot/xRot——它每 tick 对 pitch 做
        // lerp(0.2) 渐转向，发射初期会打转；用 quaternion 瞬时对齐，稳定不转。
        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-7) {
            this.renderRot.rotationTo(FORWARD, this.dirVec.set((float) motion.x, (float) motion.y, (float) motion.z).normalize());
            poseStack.mulPose(this.renderRot);
        }

        VertexConsumer vc = bufferSource.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        int light = packedLight;

        // 8 个角点（相对中心）
        float px0 = -HX;  // 左
        float px1 = HX;   // 右
        float py0 = -HY;  // 下
        float py1 = HY;   // 上
        float pz0 = -HZ;  // 前
        float pz1 = HZ;   // 后

        // +X 面 (右)
        quad(vc, pose, light,
                px1,py1,pz0,  px1,py1,pz1,  px1,py0,pz1,  px1,py0,pz0,
                1,0,0);
        // -X 面 (左)
        quad(vc, pose, light,
                px0,py0,pz0,  px0,py0,pz1,  px0,py1,pz1,  px0,py1,pz0,
                -1,0,0);
        // +Y 面 (上)
        quad(vc, pose, light,
                px0,py1,pz0,  px0,py1,pz1,  px1,py1,pz1,  px1,py1,pz0,
                0,1,0);
        // -Y 面 (下)
        quad(vc, pose, light,
                px0,py0,pz0,  px1,py0,pz0,  px1,py0,pz1,  px0,py0,pz1,
                0,-1,0);
        // +Z 面 (后)
        quad(vc, pose, light,
                px0,py0,pz1,  px1,py0,pz1,  px1,py1,pz1,  px0,py1,pz1,
                0,0,1);
        // -Z 面 (前)
        quad(vc, pose, light,
                px0,py0,pz0,  px0,py1,pz0,  px1,py1,pz0,  px1,py0,pz0,
                0,0,-1);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    /** 画一个四边形面（4 角点 + 法线），UV 铺满整张贴图。 */
    private void quad(VertexConsumer vc, PoseStack.Pose pose, int light,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      float nx, float ny, float nz) {
        vertex(vc, pose, light, x1, y1, z1, 0f, 0f, nx, ny, nz);
        vertex(vc, pose, light, x2, y2, z2, 1f, 0f, nx, ny, nz);
        vertex(vc, pose, light, x3, y3, z3, 1f, 1f, nx, ny, nz);
        vertex(vc, pose, light, x4, y4, z4, 0f, 1f, nx, ny, nz);
    }

    private void vertex(VertexConsumer vc, PoseStack.Pose pose, int light,
                        float x, float y, float z, float u, float v,
                        float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(ShellEntity entity) {
        return TEXTURE;
    }
}
