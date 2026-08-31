package com.testmod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * 多部件飞艇的判定箱公共基类（照抄 EnderDragonPart 的关键点）：
 * 只吃攻击判定、不存档、认父实体（自家投射物不会打到自己的部件）。
 *
 * <p>子类只需实现 {@link #hurtPart} 决定伤害落到哪里（宿主血量 / 炮塔耐久）；
 * 是否参与物理碰撞由构造参数 {@code collidable} 决定。
 */
public abstract class Airship_Part<T extends Entity> extends PartEntity<T> {

    private final EntityDimensions size;
    private final boolean collidable;

    protected Airship_Part(T parent, float width, float height, boolean collidable) {
        super(parent);
        this.size = EntityDimensions.scalable(width, height);
        this.collidable = collidable;
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /** 是否参与实体物理碰撞（玩家撞到会被挡住） */
    @Override
    public boolean canBeCollidedWith() {
        return this.collidable;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 自家炮弹擦到部件不算伤害
        if (source.getEntity() == this.getParent() || this.isInvulnerableTo(source)) {
            return false;
        }
        return this.hurtPart(source, amount);
    }

    /** 受击转发：由子类决定伤害落到哪里（宿主 hurtPart / 炮塔耐久） */
    protected abstract boolean hurtPart(DamageSource source, float amount);

    /** 认父实体：自己发射的投射物才不会打到自己的部件 */
    @Override
    public boolean is(Entity entity) {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return this.getParent().getPickResult();
    }
}
