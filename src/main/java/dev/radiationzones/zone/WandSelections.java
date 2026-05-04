package dev.radiationzones.zone;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WandSelections {
    public static final class Selection {
        public BlockPos pos1;
        public BlockPos pos2;
    }

    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();

    private WandSelections() {}

    public static Selection get(UUID player) {
        return SELECTIONS.computeIfAbsent(player, k -> new Selection());
    }

    public static Selection peek(UUID player) {
        return SELECTIONS.get(player);
    }

    public static void clear(UUID player) {
        SELECTIONS.remove(player);
    }
}
