package su.terrafirmagreg.core.compat.tfc.solar.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Star(double ascension, double declination, int color, double magnitude) {

    private static final Codec<String> COLOR_STRING_CODEC = Codec.STRING;

    private static final Codec<Integer> COLOR_CODEC = COLOR_STRING_CODEC.xmap(hex -> {
        final String normalized = hex.startsWith("#") ? hex.substring(1) : hex;
        return Integer.parseInt(normalized, 16);
    }, color -> String.format("%06x", color & 0xFFFFFF));

    public static final Codec<Star> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("ascension").forGetter(Star::ascension),
            Codec.DOUBLE.fieldOf("declination").forGetter(Star::declination),
            Codec.DOUBLE.fieldOf("magnitude").forGetter(Star::magnitude),
            COLOR_CODEC.optionalFieldOf("color", 0xFFFFFF).forGetter(Star::color))
            .apply(instance, (ascension, declination, magnitude, color) -> new Star(ascension, declination, color, magnitude)));

    private static final Codec<List<Double>> COMPACT_TUPLE_CODEC = Codec.DOUBLE.listOf().flatXmap(
            values -> {
                if (values.size() != 4) {
                    return DataResult.error(() -> "Compact star must have 4 values [asc, dec, mag, paletteIndex]");
                }
                return DataResult.success(values);
            },
            star -> DataResult.error(() -> "Compact star encoding is handled by StarCatalog"));

    private record CompactRaw(boolean replace, List<String> palette, List<List<Double>> stars) {
    }

    private static final Codec<CompactRaw> COMPACT_RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("replace", false).forGetter(CompactRaw::replace),
            COLOR_STRING_CODEC.listOf().fieldOf("palette").forGetter(CompactRaw::palette),
            COMPACT_TUPLE_CODEC.listOf().fieldOf("stars").forGetter(CompactRaw::stars))
            .apply(instance, CompactRaw::new));

    private static final Codec<StarCatalog> LEGACY_CATALOG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("replace", false).forGetter(StarCatalog::replace),
            CODEC.listOf().fieldOf("stars").forGetter(StarCatalog::stars))
            .apply(instance, StarCatalog::new));

    private static final Codec<StarCatalog> COMPACT_CATALOG_CODEC = COMPACT_RAW_CODEC.flatXmap(
            raw -> {
                try {
                    return DataResult.success(new StarCatalog(raw.replace(), decodeCompactStars(raw.stars(), raw.palette())));
                } catch (RuntimeException error) {
                    return DataResult.error(error::getMessage);
                }
            },
            catalog -> DataResult.error(() -> "Compact star catalog encoding is not supported"));

    public static DataResult<StarCatalog> parseCatalog(JsonElement element) {
        if (element.isJsonObject() && element.getAsJsonObject().has("palette")) {
            return COMPACT_CATALOG_CODEC.parse(JsonOps.INSTANCE, element);
        }
        return LEGACY_CATALOG_CODEC.parse(JsonOps.INSTANCE, element);
    }

    private static List<Star> decodeCompactStars(List<List<Double>> tuples, List<String> palette) {
        if (palette.isEmpty()) {
            throw new IllegalStateException("Compact star catalog requires a non-empty palette");
        }
        final List<Star> stars = new ArrayList<>(tuples.size());
        for (final List<Double> tuple : tuples) {
            stars.add(decodeCompactStar(tuple, palette));
        }
        return stars;
    }

    private static Star decodeCompactStar(List<Double> tuple, List<String> palette) {
        final int paletteIndex = tuple.get(3).intValue();
        if (paletteIndex < 0 || paletteIndex >= palette.size()) {
            throw new IllegalStateException("Star palette index out of range: " + paletteIndex);
        }
        return new Star(tuple.get(0), tuple.get(1), parseColorHex(palette.get(paletteIndex)), tuple.get(2));
    }

    private static int parseColorHex(String hex) {
        final String normalized = hex.startsWith("#") ? hex.substring(1) : hex;
        return Integer.parseInt(normalized, 16);
    }

    public record StarCatalog(boolean replace, List<Star> stars) {
    }
}
