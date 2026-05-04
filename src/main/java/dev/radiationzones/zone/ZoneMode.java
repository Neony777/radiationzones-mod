package dev.radiationzones.zone;

/** Whether radiation is dealt INSIDE the zone bounds (a hazard cuboid)
 *  or OUTSIDE them (a safe bubble — world-border style). */
public enum ZoneMode {
    INSIDE,
    OUTSIDE;

    public static ZoneMode fromString(String s) {
        if (s == null) return INSIDE;
        return switch (s.toLowerCase()) {
            case "outside", "safe", "border", "world_border" -> OUTSIDE;
            case "inside", "hazard", "danger" -> INSIDE;
            default -> throw new IllegalArgumentException(
                    "Unknown zone mode '" + s + "' (expected 'inside' or 'outside')");
        };
    }
}
