package su.terrafirmagreg.core.compat.tfc.solar.client;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Constellation(int id, String name, List<Edge> edges) {

    public record Edge(double ascension1, double declination1, double ascension2, double declination2) {
    }

    private static final Codec<Edge> EDGE_CODEC = Codec.DOUBLE.listOf().flatXmap(
            values -> {
                if (values.size() != 4) {
                    return DataResult.error(() -> "Constellation edge must have 4 values [asc1, decl1, asc2, decl2]");
                }
                return DataResult.success(new Edge(values.get(0), values.get(1), values.get(2), values.get(3)));
            },
            edge -> DataResult.success(List.of(edge.ascension1(), edge.declination1(), edge.ascension2(), edge.declination2())));

    public static final Codec<Constellation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("id", 0).forGetter(Constellation::id),
            Codec.STRING.optionalFieldOf("name", "Unknown").forGetter(Constellation::name),
            EDGE_CODEC.listOf().optionalFieldOf("edges", List.of()).forGetter(Constellation::edges))
            .apply(instance, Constellation::new));

    public static final Codec<ConstellationCatalog> CATALOG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("replace", false).forGetter(ConstellationCatalog::replace),
            CODEC.listOf().fieldOf("constellations").forGetter(ConstellationCatalog::constellations))
            .apply(instance, ConstellationCatalog::new));

    public record ConstellationCatalog(boolean replace, List<Constellation> constellations) {
    }
}
