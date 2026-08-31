package com.testmod.network;

import com.testmod.Test_Mod;
import com.testmod.item.laser_gun;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端 → 服务端："按 R 换弹"。服务端校验手上确实是激光枪后回满弹药 + 上换弹冷却。 */
public record Laser_Reload_Payload() implements CustomPacketPayload {

    public static final Laser_Reload_Payload INSTANCE = new Laser_Reload_Payload();
    public static final Type<Laser_Reload_Payload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Test_Mod.MODID, "laser_reload"));
    public static final StreamCodec<ByteBuf, Laser_Reload_Payload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Laser_Reload_Payload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack s = sp.getItemInHand(hand);
                    if (s.is(Test_Mod.LASER_GUN.get())) {
                        laser_gun.startReload(sp, s);
                        return;
                    }
                }
            }
        });
    }
}
