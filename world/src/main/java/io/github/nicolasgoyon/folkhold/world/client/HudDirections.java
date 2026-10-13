package io.github.nicolasgoyon.folkhold.world.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The direction and the distance from the player to a target, for the HUD panels. */
final class HudDirections {
    private static final String KEY = "hud.folkhold_world.distance.";
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private HudDirections() {
    }

    /** The horizontal distance from the player to a position, in blocks. */
    static int distance(LocalPlayer player, double x, double z) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    /** The arrow to a position from the view of the player: "↑" is in front, "→" is on the right. */
    static String arrow(LocalPlayer player, double x, double z) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        // The yaw of the direction to the position: 0 is south, 90 is west, as the yaw of an entity.
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Mth.wrapDegrees(yaw - player.getYRot());
        return ARROWS[Math.floorMod((int) Math.round(relative / 45.0), ARROWS.length)];
    }

    /** A distance in blocks, or in kilometers with one decimal from 1000 blocks. */
    static Component distanceText(int blocks) {
        if (blocks < 1000) {
            return Component.translatable(KEY + "m", blocks);
        }
        return Component.translatable(KEY + "km", blocks / 1000, blocks % 1000 / 100);
    }

    /** The arrow and the distance: "↗ 120 m". */
    static Component toward(LocalPlayer player, double x, double z) {
        return Component.literal(arrow(player, x, z) + " ").append(distanceText(distance(player, x, z)));
    }
}
