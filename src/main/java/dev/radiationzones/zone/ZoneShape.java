package dev.radiationzones.zone;

/** Cuboid (square footprint) or cylinder (circular footprint, full world height). */
public enum ZoneShape {
    SQUARE,
    CIRCLE;

    public static ZoneShape fromString(String s) {
        if (s == null) return SQUARE;
        return switch (s.toLowerCase()) {
            case "circle", "round", "cylinder" -> CIRCLE;
            case "square", "cuboid", "box" -> SQUARE;
            default -> throw new IllegalArgumentException(
                    "Unknown zone shape '" + s + "' (expected 'square' or 'circle')");
        };
    }
}
