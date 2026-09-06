package su.terrafirmagreg.core.compat.tfc.solar.meteor.client;

import java.util.Locale;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

import su.terrafirmagreg.core.common.data.TFGParticles;

public class FallingStarParticleOptions implements ParticleOptions {
    public static final Codec<FallingStarParticleOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("r").forGetter(FallingStarParticleOptions::getRed),
            Codec.FLOAT.fieldOf("g").forGetter(FallingStarParticleOptions::getGreen),
            Codec.FLOAT.fieldOf("b").forGetter(FallingStarParticleOptions::getBlue),
            Codec.FLOAT.fieldOf("scale").forGetter(FallingStarParticleOptions::getScale))
            .apply(instance, FallingStarParticleOptions::new));

    public static final ParticleOptions.Deserializer<FallingStarParticleOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public FallingStarParticleOptions fromCommand(ParticleType<FallingStarParticleOptions> type, StringReader reader)
                throws CommandSyntaxException {
            reader.expect(' ');
            final float red = reader.readFloat();
            reader.expect(' ');
            final float green = reader.readFloat();
            reader.expect(' ');
            final float blue = reader.readFloat();
            reader.expect(' ');
            final float scale = reader.readFloat();
            return new FallingStarParticleOptions(red, green, blue, scale);
        }

        @Override
        public FallingStarParticleOptions fromNetwork(ParticleType<FallingStarParticleOptions> type, FriendlyByteBuf buffer) {
            return new FallingStarParticleOptions(
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat());
        }
    };

    private final float red;
    private final float green;
    private final float blue;
    private final float scale;

    public FallingStarParticleOptions(float red, float green, float blue, float scale) {
        this.red = Mth.clamp(red, 0.0F, 1.0F);
        this.green = Mth.clamp(green, 0.0F, 1.0F);
        this.blue = Mth.clamp(blue, 0.0F, 1.0F);
        this.scale = Math.max(0.01F, scale);
    }

    public float getRed() {
        return red;
    }

    public float getGreen() {
        return green;
    }

    public float getBlue() {
        return blue;
    }

    public float getScale() {
        return scale;
    }

    @Override
    public ParticleType<?> getType() {
        return TFGParticles.FALLING_STAR.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeFloat(red);
        buffer.writeFloat(green);
        buffer.writeFloat(blue);
        buffer.writeFloat(scale);
    }

    @Override
    public String writeToString() {
        return String.format(
                Locale.ROOT,
                "%s %.2f %.2f %.2f %.2f",
                TFGParticles.FALLING_STAR.getId(),
                red,
                green,
                blue,
                scale);
    }
}
