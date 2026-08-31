package com.testmod.item;

import com.testmod.network.Laser_Fired_Payload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 激光枪：物品壳。「发射激光」的全部逻辑在 Laser_Beam 模块（光束几何/伤害/命中特效/粒子）；
 * 这里只管物品触发（冷却闸门、击发确认发包）和玩家持枪手的枪口位置。
 */
public class laser_gun extends Item {

    /**
     * 统一射击间隔（tick）：单击与按住连发共用的唯一射速闸门，想调射速只改这一个数。
     * 4 tick = 原版按住连发的节奏（Minecraft.rightClickDelay=4，每 4 tick 才触发一次 startUseItem）。
     * 单击走 keyUse.consumeClick() 路径本可绕开 rightClickDelay，且 MultiPlayerGameMode 冷却中也照样发包，
     * 所以必须在 use() 内部自己把关；间隔定为 4 tick 后，单击再快也不会超过按住的速度。
     */
    private static final int FIRE_INTERVAL_TICKS = 1;
    /** 弹药 = 耐久度：满耐久 = 满弹匣，每发 +1 损耗（永不打空报废——空了就换弹） */
    public static final int MAX_AMMO = 40;
    /** 换弹时长（tick）：换弹期间用长冷却挡住射击闸门，HUD 也会显示冷却扫过 */
    public static final int RELOAD_TICKS = 30;

    public laser_gun(Properties properties) {
        super(properties);
    }

    /** 弹药条（耐久条）常显：默认实现是有损耗才显示，满弹匣时也显示方便看剩余弹药 */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    /** 玩家持枪的光束（精准方向 = 视线） */
    public static Laser_Beam.Beam playerBeam(Level level, Player player, InteractionHand hand) {
        return playerBeam(level, player, hand, player.getViewVector(1.0F));
    }

    /** 玩家持枪的光束：枪口位置按持枪手偏移（主手→主臂侧，副手→另一侧；含左撇子设置）+ 下 0.1；方向可传入加了散布的 dir */
    public static Laser_Beam.Beam playerBeam(Level level, Player player, InteractionHand hand, Vec3 dir) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 right;
        if (Math.abs(look.x) < 0.001 && Math.abs(look.z) < 0.001) {
            right = new Vec3(1, 0, 0);
        } else {
            right = new Vec3(-look.z, 0, look.x).normalize();
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        Vec3 muzzle = eye.add(right.scale(arm == HumanoidArm.RIGHT ? 0.2 : -0.2).add(0, -0.1, 0));
        return Laser_Beam.computeBeam(level, player, muzzle, dir);
    }

    /** 换弹（R 键，由 Laser_Reload_Payload 触发）：回满弹药 + 换弹冷却（期间射击闸门自然关闭） */
    public static void startReload(Player player, ItemStack stack) {
        stack.setDamageValue(0);
        player.getCooldowns().addCooldown(stack.getItem(), RELOAD_TICKS);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // 客户端不做任何射击效果（粒子/后座等服务端确认后由 Laser_Fired_Payload 回调播放），
            // 只上本地冷却做输入节流 + HUD 提示。射击判定只有一个权威（服务端），
            // 从根本上消除"双端冷却窗口错开"导致的粒子与伤害不一致（曾有伤害没光束的 bug）。
            player.getCooldowns().addCooldown(this, FIRE_INTERVAL_TICKS);
            return InteractionResultHolder.consume(itemStack);
        }

        // ===== 唯一射击闸门（服务端权威）：冷却检查+施加原子完成 =====
        // 单击（consumeClick）、按住连发（rightClickDelay）、服务端收包，三条路全部汇聚于此
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(itemStack);
        }

        // 弹药检查：弹匣空 → 咔哒一声，打不出去（按 R 换弹）
        if (itemStack.getDamageValue() >= MAX_AMMO) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
            return InteractionResultHolder.fail(itemStack);
        }

        player.getCooldowns().addCooldown(this, FIRE_INTERVAL_TICKS);
        itemStack.setDamageValue(itemStack.getDamageValue() + 1); // 消耗 1 发（耐久度当弹药）

        // 弹道散布在服务端加一次，真实方向与枪口几何随击发包广播给附近玩家（视觉与判定同一条线）
        Vec3 dir = Laser_Beam.spread(player.getViewVector(1.0F), player.getRandom());
        Laser_Beam.Beam beam = playerBeam(level, player, hand, dir);
        Laser_Beam.fire((ServerLevel) level, player, beam);

        // 广播光束几何（start/dir/len）给附近玩家：每个靠近的玩家（含射手自己）都画同一条服务端判定线，
        // 联机时其他人也能看到激光；射手身份由 shooterId 比对，本地只对它触发后座。
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new Laser_Fired_Payload(
                    beam.start().x, beam.start().y, beam.start().z,
                    beam.dir().x, beam.dir().y, beam.dir().z,
                    beam.len(), sp.getId()));
        }
        // CONSUME：使用成功但不播放挥臂动画（SUCCESS 才挥臂，见 InteractionResult.shouldSwing），射击时模型保持不动
        return InteractionResultHolder.consume(itemStack);
    }
}
