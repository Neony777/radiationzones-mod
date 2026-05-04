package dev.radiationzones.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record InstalledFilter(ResourceLocation filterId, int durability, int maxDurability) {

    public static final Codec<InstalledFilter> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("filter_id").forGetter(InstalledFilter::filterId),
            Codec.INT.fieldOf("durability").forGetter(InstalledFilter::durability),
            Codec.INT.fieldOf("max_durability").forGetter(InstalledFilter::maxDurability)
    ).apply(i, InstalledFilter::new));

    public static final StreamCodec<ByteBuf, InstalledFilter> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, InstalledFilter::filterId,
            ByteBufCodecs.VAR_INT, InstalledFilter::durability,
            ByteBufCodecs.VAR_INT, InstalledFilter::maxDurability,
            InstalledFilter::new
    );

    public InstalledFilter withDurability(int newDur) {
        return new InstalledFilter(filterId, Math.max(0, Math.min(maxDurability, newDur)), maxDurability);
    }

    public boolean isEmpty() { return durability <= 0; }
}
