package com.testmod.item;

import com.testmod.entity.Flak_Shell_Entity;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 高射炮弹：参考雪球，右键投掷发射 Flak_Shell_Entity（便于测试）。
 * 上抛角度由玩家视线决定，直线飞行（投射物本身不加重力下坠）。
 */
public class Flak_Shell_Item extends Item {

    private static final float FIRE_SPEED = 1.5F;
    private static final float INACCURACY = 1.0F;

    public Flak_Shell_Item(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Vec3 viewDir = player.getViewVector(1.0F);
            Flak_Shell_Entity shell = new Flak_Shell_Entity(level, player, viewDir);
            shell.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
            shell.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, FIRE_SPEED, INACCURACY);
            level.addFreshEntity(shell);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }
}
