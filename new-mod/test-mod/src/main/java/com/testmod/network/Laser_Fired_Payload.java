package com.testmod.network;

import com.testmod.Test_Mod;
import com.testmod.Test_Mod_Client;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 附近玩家："这一发激光枪击发了"。携带服务端算好的枪口几何
 * （start 枪口 + dir 散布后方向 + len 长度）与射手实体 id。
 * 客户端用几何直接画光束（start+dir+len，与判定同一条线），不依赖接收者自己的视角，
 * 因此联机时所有靠近的玩家都能看到同一条激光；仅射手自己额外触发后座（shooterId 比对）。
 */
public record Laser_Fired_Payload(double startX, double startY, double startZ,
                                  double dirX, double dirY, double dirZ,
                                  double len, int shooterId) implements CustomPacketPayload {

    public static final Type<Laser_Fired_Payload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Test_Mod.MODID, "laser_fired"));

    public static final StreamCodec<ByteBuf, Laser_Fired_Payload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.startX());
                buf.writeDouble(p.startY());
                buf.writeDouble(p.startZ());
                buf.writeDouble(p.dirX());
                buf.writeDouble(p.dirY());
                buf.writeDouble(p.dirZ());
                buf.writeDouble(p.len());
                buf.writeInt(p.shooterId());
            },
            buf -> new Laser_Fired_Payload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** playToClient 只在客户端执行；Test_Mod_Client 引用惰性解析，服务端不会加载客户端类 */
    public static void handle(Laser_Fired_Payload payload, IPayloadContext context) {
        context.enqueueWork(() -> Test_Mod_Client.onPlayerLaser(payload));
    }
}
