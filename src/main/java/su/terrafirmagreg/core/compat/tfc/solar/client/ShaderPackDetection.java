package su.terrafirmagreg.core.compat.tfc.solar.client;

import su.terrafirmagreg.core.config.TFGConfig;

public final class ShaderPackDetection {
    private ShaderPackDetection() {
    }

    public static boolean shouldUseVanillaSky() {
        if (!TFGConfig.CLIENT.disableCustomSkyWithActiveShaders.get()) {
            return false;
        }
        return isShaderPackActive();
    }

    /**
     * When a shader pack renders its own sun and moon, the custom sky renderer should skip them.
     */
    public static boolean shouldDelegateCelestialBodiesToShaders() {
        return !shouldUseVanillaSky() && isShaderPackActive();
    }

    public static boolean isShaderPackActive() {
        try {
            final Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            final Object config = iris.getMethod("getIrisConfig").invoke(null);
            if (!(boolean) config.getClass().getMethod("areShadersEnabled").invoke(config)) {
                return false;
            }
            return hasExternalShaderPackSelected(iris);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean hasExternalShaderPackSelected(Class<?> iris) throws ReflectiveOperationException {
        for (final String methodName : new String[] { "getCurrentPackName", "getSelectedShaderPack" }) {
            try {
                final Object packName = iris.getMethod(methodName).invoke(null);
                if (packName instanceof String name) {
                    return isExternalShaderPackName(name);
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next Iris/Oculus entry point.
            }
        }
        return true;
    }

    private static boolean isExternalShaderPackName(String packName) {
        if (packName == null || packName.isBlank()) {
            return false;
        }
        final String normalized = packName.trim().toLowerCase();
        return !normalized.equals("none")
                && !normalized.equals("vanilla")
                && !normalized.equals("(internal)")
                && !normalized.equals("internal");
    }
}
