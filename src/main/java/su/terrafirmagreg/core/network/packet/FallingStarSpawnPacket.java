package su.terrafirmagreg.core.network.packet;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import su.terrafirmagreg.core.compat.tfc.solar.meteor.client.FallingStarParticleOptions;

public final class FallingStarSpawnPacket {
    private final double originX;
    private final double originY;
    private final double originZ;
    private final double velX;
    private final double velY;
    private final double velZ;
    private final float red;
    private final float green;
    private final float blue;
    private final float scale;

    public FallingStarSpawnPacket(
            double originX,
            double originY,
            double originZ,
            double velX,
            double velY,
            double velZ,
            float red,
            float green,
            float blue,
            float scale) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.scale = scale;
    }

    public double originX() {
        return originX;
    }

    public double originY() {
        return originY;
    }

    public double originZ() {
        return originZ;
    }

    public double velX() {
        return velX;
    }

    public double velY() {
        return velY;
    }

    public double velZ() {
        return velZ;
    }

    public float red() {
        return red;
    }

    public float green() {
        return green;
    }

    public float blue() {
        return blue;
    }

    public float scale() {
        return scale;
    }

    public static void encode(FallingStarSpawnPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.originX);
        buffer.writeDouble(packet.originY);
        buffer.writeDouble(packet.originZ);
        buffer.writeDouble(packet.velX);
        buffer.writeDouble(packet.velY);
        buffer.writeDouble(packet.velZ);
        buffer.writeFloat(packet.red);
        buffer.writeFloat(packet.green);
        buffer.writeFloat(packet.blue);
        buffer.writeFloat(packet.scale);
    }

    public static FallingStarSpawnPacket decode(FriendlyByteBuf buffer) {
        return new FallingStarSpawnPacket(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat());
    }

    public static void handle(FallingStarSpawnPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            final Minecraft client = Minecraft.getInstance();
            if (client.level == null) {
                return;
            }
            client.level.addAlwaysVisibleParticle(
                    new FallingStarParticleOptions(packet.red, packet.green, packet.blue, packet.scale),
                    true,
                    packet.originX,
                    packet.originY,
                    packet.originZ,
                    packet.velX,
                    packet.velY,
                    packet.velZ);
        });
        context.get().setPacketHandled(true);
    }
}
