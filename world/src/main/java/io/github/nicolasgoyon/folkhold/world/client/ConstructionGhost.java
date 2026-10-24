package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiDraw;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiText;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionEndPayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPlacePayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPreviewPayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPreviewPayload.Entry;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionStatusPayload;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The ghost of a structure template on the client: an operator places a structure before its construction (see
 * {@link io.github.nicolasgoyon.folkhold.world.construction.ConstructionPlacement}).
 *
 * <ul>
 *   <li>The ghost follows the block that the player looks at, at {@link #PICK_DISTANCE} blocks or nearer. It stands
 *       on this block, with its center above it (see {@link GhostPlacement}).</li>
 *   <li>The keys of {@link ConstructionKeys} turn and mirror the ghost, and lock its position. A key of movement
 *       moves the ghost by one block, and locks it.</li>
 *   <li>The key "build" sends the position to the server. During the construction, the ghost shows only the blocks
 *       that are not in place, and it goes away when all its blocks are in place.</li>
 *   <li>The key "cancel" removes the ghost.</li>
 * </ul>
 * The panel above the hotbar shows the state of the ghost and its keys. {@link ConstructionGhostRenderer} draws the
 * ghost in the level.
 *
 * <p>The client reads the blocks of the level in the box of the ghost at most once in {@link #MIN_CHECK_TICKS} ticks,
 * and at least once in {@link #CHECK_TICKS} ticks. While the ghost follows the view, it shows all its blocks. After
 * the operator locks it, and during the construction, it shows only the blocks that are not in place.
 */
public final class ConstructionGhost {
    /** The ghost follows the blocks at this distance from the eyes or nearer, in blocks. */
    static final double PICK_DISTANCE = 64.0;
    /** The ghost shows this number of obstacles at most. */
    static final int MAX_OBSTACLES = 512;
    private static final String KEY = "hud.folkhold_world.ghost.";
    /** The client checks the blocks of the level in the box every this number of ticks. */
    private static final int CHECK_TICKS = 10;
    /** After a change of the ghost, the client checks the blocks of the level after this number of ticks or more. */
    private static final int MIN_CHECK_TICKS = 4;
    /** The bottom of the panel is at this distance from the bottom of the screen: above the hotbar and its texts. */
    private static final int PANEL_BOTTOM = 76;
    private static final int PAD = 4;

    @Nullable
    private static Ghost ghost;

    private ConstructionGhost() {
    }

    /** The phase of a ghost. */
    enum Phase {
        /** The operator moves the ghost. */
        PLACING,
        /** The client sent the position to the server, and waits for the answer. */
        WAITING,
        /** The construction runs: the ghost shows the blocks that are not in place. */
        BUILDING
    }

    /** A ghost: the blocks of its template, and its position in the level. */
    static final class Ghost {
        final ResourceLocation template;
        final Vec3i size;
        final List<BlockPos> locals;
        final List<BlockState> states;
        final int stepsPerTick;
        final ClientLevel level;
        Rotation rotation = Rotation.NONE;
        Mirror mirror = Mirror.NONE;
        BlockPos anchor;
        boolean locked;
        Phase phase = Phase.PLACING;
        BlockPos origin = BlockPos.ZERO;
        BoundingBox box = new BoundingBox(BlockPos.ZERO);
        /**
         * The positions of the blocks after the turn and the mirror, relative to {@link #origin}, in the order of
         * {@link #placedStates}. They do not change when the ghost moves.
         */
        List<BlockPos> relatives = List.of();
        /** The states of the blocks, after the turn and the mirror. */
        List<BlockState> placedStates = List.of();
        /** The indexes of the blocks in each section of {@link GhostSections}. */
        Long2ObjectMap<int[]> sections = new Long2ObjectOpenHashMap<>();
        /** The number of the turn and the mirror: it changes at each turn and at each mirror. */
        int shape;
        /** True for the blocks that the ghost shows. */
        boolean[] shown = new boolean[0];
        /** For each section, a number that changes when the blocks that the ghost shows in the section change. */
        final Long2IntMap sectionStamps = new Long2IntOpenHashMap();
        /** The blocks of the level in the box that the construction removes. */
        List<BlockPos> obstacles = List.of();
        /** The number of blocks of the ghost that are not in place. */
        int missing;
        int ticks;
        /** The tick of the last check of the blocks of the level. */
        int lastCheck;
        /** True if the ghost changed after the last check. */
        boolean checkDue;

        Ghost(ResourceLocation template, Vec3i size, List<BlockPos> locals, List<BlockState> states,
                int stepsPerTick, ClientLevel level, BlockPos anchor) {
            this.template = template;
            this.size = size;
            this.locals = locals;
            this.states = states;
            this.stepsPerTick = stepsPerTick;
            this.level = level;
            this.anchor = anchor;
        }
    }

    /** The ghost in the level, or null. */
    @Nullable
    static Ghost current() {
        return ghost;
    }

    /** Shows the ghost of a template. Called by the network handler. */
    public static void start(ConstructionPreviewPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        List<BlockPos> locals = new ArrayList<>(payload.blocks().size());
        List<BlockState> states = new ArrayList<>(payload.blocks().size());
        for (Entry entry : payload.blocks()) {
            locals.add(new BlockPos(entry.x(), entry.y(), entry.z()));
            states.add(payload.palette().get(entry.state()));
        }
        BlockPos anchor = pick(player).orElse(player.blockPosition().relative(player.getDirection(), 4));
        Ghost created = new Ghost(payload.template(), payload.size(), List.copyOf(locals), List.copyOf(states),
                ConstructionScreen.stepsPerTick(), minecraft.level, anchor);
        update(created, true);
        check(created);
        ghost = created;
        ConstructionScreen.closeForGhost();
    }

    /** Shows an answer of the server, in the menu or above the hotbar. Called by the network handler. */
    public static void showStatus(ConstructionStatusPayload payload) {
        Ghost current = ghost;
        if (current != null && current.phase == Phase.WAITING) {
            current.phase = payload.success() ? Phase.BUILDING : Phase.PLACING;
        }
        if (!ConstructionScreen.showStatus(payload)) {
            Minecraft.getInstance().gui.setOverlayMessage(payload.text(), false);
        }
    }

    /**
     * Removes the ghost of a construction at its end: when the construction applied all its steps, or when a command
     * stopped it. Called by the network handler.
     */
    public static void onConstructionEnd(ConstructionEndPayload payload) {
        Ghost current = ghost;
        if (current == null || current.phase != Phase.BUILDING || !current.template.equals(payload.template())
                || !current.origin.equals(payload.origin())) {
            return;
        }
        ghost = null;
        Minecraft.getInstance().gui.setOverlayMessage(
                Component.translatable(KEY + (payload.finished() ? "finished" : "stopped")), false);
    }

    /** Removes the ghost. Called when the player leaves a world or a server. */
    public static void clear() {
        ghost = null;
        ConstructionGhostRenderer.release();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        while (ConstructionKeys.MENU.consumeClick()) {
            player.connection.sendCommand("folkhold construction place");
        }
        Ghost current = ghost;
        if (current == null || current.level != minecraft.level) {
            ghost = null;
            ConstructionKeys.GHOST_KEYS.forEach(ConstructionGhost::clicks);
            return;
        }
        if (clicks(ConstructionKeys.CANCEL) > 0) {
            ghost = null;
            ConstructionKeys.GHOST_KEYS.forEach(ConstructionGhost::clicks);
            minecraft.gui.setOverlayMessage(Component.translatable(KEY + "removed"), false);
            return;
        }
        if (current.phase == Phase.PLACING) {
            placingKeys(current, player);
        } else {
            ConstructionKeys.GHOST_KEYS.forEach(ConstructionGhost::clicks);
        }
        if (current.phase == Phase.PLACING && !current.locked) {
            pick(player).filter(anchor -> !anchor.equals(current.anchor)).ifPresent(anchor -> {
                current.anchor = anchor;
                update(current, false);
            });
        }
        current.ticks++;
        int sinceCheck = current.ticks - current.lastCheck;
        if (sinceCheck >= CHECK_TICKS || (current.checkDue && sinceCheck >= MIN_CHECK_TICKS)) {
            check(current);
        }
    }

    /** The keys of a ghost that the operator moves. */
    private static void placingKeys(Ghost current, LocalPlayer player) {
        boolean changed = false;
        boolean reshaped = false;
        int turns = clicks(ConstructionKeys.ROTATE);
        for (int i = 0; i < turns; i++) {
            current.rotation = current.rotation.getRotated(Rotation.CLOCKWISE_90);
            reshaped = true;
        }
        int mirrors = clicks(ConstructionKeys.MIRROR);
        for (int i = 0; i < mirrors; i++) {
            current.mirror = nextMirror(current.mirror);
            reshaped = true;
        }
        if (clicks(ConstructionKeys.LOCK) % 2 == 1) {
            current.locked = !current.locked;
            current.checkDue = true;
        }
        Direction facing = player.getDirection();
        BlockPos moved = current.anchor
                .relative(facing, clicks(ConstructionKeys.FORWARD))
                .relative(facing.getOpposite(), clicks(ConstructionKeys.BACK))
                .relative(facing.getCounterClockWise(), clicks(ConstructionKeys.LEFT))
                .relative(facing.getClockWise(), clicks(ConstructionKeys.RIGHT))
                .above(clicks(ConstructionKeys.UP))
                .below(clicks(ConstructionKeys.DOWN));
        if (!moved.equals(current.anchor)) {
            current.anchor = moved;
            current.locked = true;
            changed = true;
        }
        if (changed || reshaped) {
            update(current, reshaped);
        }
        if (clicks(ConstructionKeys.BUILD) > 0) {
            current.phase = Phase.WAITING;
            current.locked = true;
            PacketDistributor.sendToServer(new ConstructionPlacePayload(current.template, current.origin,
                    current.rotation, current.mirror, current.stepsPerTick));
        }
    }

    /** The number of clicks of a key since the last tick. The key forgets them. */
    private static int clicks(KeyMapping key) {
        int count = 0;
        while (key.consumeClick()) {
            count++;
        }
        return count;
    }

    private static Mirror nextMirror(Mirror mirror) {
        return switch (mirror) {
            case NONE -> Mirror.LEFT_RIGHT;
            case LEFT_RIGHT -> Mirror.FRONT_BACK;
            case FRONT_BACK -> Mirror.NONE;
        };
    }

    /** The anchor under the view of the player: the position next to the face of the block that the player sees. */
    private static Optional<BlockPos> pick(LocalPlayer player) {
        HitResult hit = player.pick(PICK_DISTANCE, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return Optional.of(blockHit.getBlockPos().relative(blockHit.getDirection()));
        }
        return Optional.empty();
    }

    /**
     * Calculates the position of the ghost again after a change of the anchor, the turn or the mirror. The next
     * check reads the blocks of the level (see {@link #MIN_CHECK_TICKS}).
     *
     * @param current  the ghost
     * @param reshaped true after a turn or a mirror: the blocks of the ghost change
     */
    private static void update(Ghost current, boolean reshaped) {
        current.origin = GhostPlacement.originFor(current.size, current.rotation, current.mirror, current.anchor);
        current.box = GhostPlacement.box(current.size, current.rotation, current.mirror, current.origin);
        current.checkDue = true;
        if (!reshaped) {
            return;
        }
        List<BlockPos> relatives = new ArrayList<>(current.locals.size());
        List<BlockState> states = new ArrayList<>(current.locals.size());
        for (int i = 0; i < current.locals.size(); i++) {
            BlockPos relative = GhostPlacement.levelPos(current.locals.get(i), current.rotation, current.mirror,
                    BlockPos.ZERO);
            relatives.add(relative);
            states.add(current.states.get(i).mirror(current.mirror)
                    .rotate(current.level, relative.offset(current.origin), current.rotation));
        }
        current.relatives = List.copyOf(relatives);
        current.placedStates = List.copyOf(states);
        current.sections = GhostSections.group(current.relatives);
        current.shown = new boolean[relatives.size()];
        Arrays.fill(current.shown, true);
        current.sectionStamps.clear();
        current.shape++;
    }

    /**
     * Compares the ghost with the blocks of the level: the number of blocks that are not in place, the blocks that
     * the ghost shows, and the blocks of the box that the construction removes. A replaceable block (grass, flowers)
     * is not an obstacle on the screen. During the construction, the ghost goes away when all its blocks are in
     * place.
     */
    private static void check(Ghost current) {
        current.lastCheck = current.ticks;
        current.checkDue = false;
        boolean following = current.phase == Phase.PLACING && !current.locked;
        Long2ObjectMap<BlockState> wanted = new Long2ObjectOpenHashMap<>(current.relatives.size());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int missing = 0;
        for (int i = 0; i < current.relatives.size(); i++) {
            BlockPos relative = current.relatives.get(i);
            BlockState state = current.placedStates.get(i);
            pos.setWithOffset(relative, current.origin);
            wanted.put(pos.asLong(), state);
            boolean inPlace = current.level.getBlockState(pos).is(state.getBlock());
            if (!inPlace) {
                missing++;
            }
            boolean show = following || !inPlace;
            if (show != current.shown[i]) {
                current.shown[i] = show;
                current.sectionStamps.mergeInt(GhostSections.key(relative), 1, Integer::sum);
            }
        }
        current.missing = missing;
        if (current.phase == Phase.BUILDING && missing == 0) {
            ghost = null;
            return;
        }
        List<BlockPos> obstacles = new ArrayList<>();
        BoundingBox box = current.box;
        for (BlockPos inBox : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(),
                box.maxZ())) {
            BlockState found = current.level.getBlockState(inBox);
            if (found.isAir() || found.canBeReplaced()) {
                continue;
            }
            BlockState state = wanted.get(inBox.asLong());
            if (state == null || !found.is(state.getBlock())) {
                obstacles.add(inBox.immutable());
                if (obstacles.size() >= MAX_OBSTACLES) {
                    break;
                }
            }
        }
        current.obstacles = List.copyOf(obstacles);
    }

    /** The panel above the hotbar: the template, the phase, the turn, the mirror and the keys. */
    public static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Ghost current = ghost;
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        Font font = minecraft.font;
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(Component.translatable(KEY + "title", current.template.toString()));
        colors.add(UiTheme.GOLD);
        lines.add(Component.translatable(KEY + "state", phaseText(current),
                Component.translatable(KEY + "rotation." + degrees(current.rotation)),
                Component.translatable(KEY + "mirror." + mirrorName(current.mirror))));
        colors.add(UiTheme.TEXT);
        if (current.phase == Phase.PLACING) {
            lines.add(Component.translatable(KEY + "keys", key(ConstructionKeys.ROTATE), key(ConstructionKeys.MIRROR),
                    key(ConstructionKeys.LOCK), key(ConstructionKeys.BUILD), key(ConstructionKeys.CANCEL)));
            colors.add(UiTheme.TEXT_MUTED);
            lines.add(Component.translatable(KEY + "move_keys", key(ConstructionKeys.FORWARD),
                    key(ConstructionKeys.BACK), key(ConstructionKeys.LEFT), key(ConstructionKeys.RIGHT),
                    key(ConstructionKeys.UP), key(ConstructionKeys.DOWN)));
            colors.add(UiTheme.TEXT_MUTED);
            if (!current.obstacles.isEmpty()) {
                lines.add(Component.translatable(KEY + "obstacles", current.obstacles.size()));
                colors.add(UiTheme.TEXT_BAD);
            }
        } else {
            lines.add(Component.translatable(KEY + "cancel_key", key(ConstructionKeys.CANCEL)));
            colors.add(UiTheme.TEXT_MUTED);
        }
        int maxWidth = graphics.guiWidth() - 2 * UiTheme.SCREEN_MARGIN;
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        width = Math.min(width + 2 * PAD, maxWidth);
        int height = lines.size() * (UiTheme.LINE + 2) + 2 * PAD - 2;
        UiRect panel = new UiRect((graphics.guiWidth() - width) / 2, graphics.guiHeight() - PANEL_BOTTOM - height,
                width, height);
        UiDraw.fill(graphics, panel, UiTheme.HUD);
        int y = panel.y() + PAD;
        for (int i = 0; i < lines.size(); i++) {
            UiText.drawCentered(graphics, font, lines.get(i), new UiRect(panel.x() + PAD, y, width - 2 * PAD,
                    UiTheme.LINE), colors.get(i));
            y += UiTheme.LINE + 2;
        }
    }

    private static Component phaseText(Ghost current) {
        return switch (current.phase) {
            case PLACING -> Component.translatable(KEY + (current.locked ? "locked" : "following"));
            case WAITING -> Component.translatable(KEY + "waiting");
            case BUILDING -> Component.translatable(KEY + "building", current.missing);
        };
    }

    private static int degrees(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
        };
    }

    private static String mirrorName(Mirror mirror) {
        return switch (mirror) {
            case NONE -> "none";
            case LEFT_RIGHT -> "left_right";
            case FRONT_BACK -> "front_back";
        };
    }

    private static Component key(KeyMapping key) {
        return key.getTranslatedKeyMessage();
    }
}
