package com.testmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * 激光枪第一人称后座：开火时 kick() 拉满强度，每 client tick 线性衰减；
 * Test_Mod_Client 的 RenderHandEvent 里对持枪的手 apply()（模型后移 + 枪口小幅上仰）。
 * 后座位移在 vanilla pushPose 之外，同帧另一只手渲染前必须 unapply() 抵消，否则会污染那只手。
 * 本类只引用 blaze3d/joml/Mth（非客户端独占类），从 common 代码的 isClientSide 分支调用安全。
 */
public class Laser_Gun_Recoil {

    // 后座手感参数（想调整就改这里）
    private static final float KICK_BACK = 0.08F;    // 后移幅度（格，朝玩家方向）
    private static final float KICK_UP = 0.015F;     // 轻微上抬（格）
    private static final float KICK_ROT = 3.5F;      // 枪口上跳（度）
    private static final float RECOVER = 1.0F / 6.0F; // 每 tick 恢复量（约 6 tick 回位）

    private static float recoil, prevRecoil; // 当前/上一 tick 强度（0=无，1=满）
    private static float applied;            // 本帧已应用到 pose 的强度（供另一只手抵消）

    /** 开火瞬间：后座拉满 */
    public static void kick() {
        recoil = 1.0F;
    }

    /** 每帧第一只手（主手）渲染前调用：清空上一帧的应用记录（帧率高于 tick 时防止跨帧污染主手） */
    public static void beginFrame() {
        applied = 0.0F;
    }

    /** 每 client tick：记录上一拍（供插值）并衰减 */
    public static void tick() {
        prevRecoil = recoil;
        recoil = Math.max(0.0F, recoil - RECOVER);
        applied = 0.0F; // 防御：跨 tick 清掉可能残留的应用记录
    }

    /** 渲染持枪的手之前调用：应用后座变换 */
    public static void apply(PoseStack pose, float partialTick) {
        float r = Mth.lerp(partialTick, prevRecoil, recoil);
        applied = r;
        if (r <= 0.0F) return;
        pose.translate(0.0F, KICK_UP * r, KICK_BACK * r);
        pose.mulPose(Axis.XP.rotationDegrees(KICK_ROT * r));
    }

    /** 渲染另一只手之前调用：施加 apply 的逆变换，抵消污染 */
    public static void unapply(PoseStack pose) {
        if (applied <= 0.0F) return;
        pose.mulPose(Axis.XP.rotationDegrees(-KICK_ROT * applied));
        pose.translate(0.0F, -KICK_UP * applied, -KICK_BACK * applied);
        applied = 0.0F;
    }
}
