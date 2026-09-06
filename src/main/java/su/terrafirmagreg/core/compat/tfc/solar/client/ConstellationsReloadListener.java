package su.terrafirmagreg.core.compat.tfc.solar.client;

import java.io.IOException;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import su.terrafirmagreg.core.TFGCore;

public final class ConstellationsReloadListener extends SimplePreparableReloadListener<JsonElement> {
    private static final ResourceLocation ID = TFGCore.id("stars/constellation_edges.json");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    @Override
    protected JsonElement prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        try (var reader = resourceManager.openAsReader(ID)) {
            return GsonHelper.fromJson(GSON, reader, JsonElement.class);
        } catch (IOException | JsonParseException error) {
            LOGGER.error("Could not load constellation_edges.json", error);
            return JsonNull.INSTANCE;
        }
    }

    @Override
    protected void apply(JsonElement object, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (object == null || object.isJsonNull()) {
            LOGGER.warn("[TFG Constellations] constellation_edges.json missing or invalid");
            TFGSkyRenderer.updateConstellations(null);
            return;
        }
        try {
            final var parsed = Constellation.CATALOG_CODEC.parse(JsonOps.INSTANCE, object);
            parsed.error().ifPresentOrElse(
                    error -> {
                        LOGGER.error("[TFG Constellations] Could not parse constellation_edges.json: {}", error.message());
                        TFGSkyRenderer.updateConstellations(null);
                    },
                    () -> TFGSkyRenderer.updateConstellations(parsed.result().orElseThrow().constellations()));
        } catch (RuntimeException error) {
            LOGGER.error("[TFG Constellations] Could not parse constellation_edges.json", error);
            TFGSkyRenderer.updateConstellations(null);
        }
    }
}
