package io.github.nicolasgoyon.folkhold.world.village;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.io.IOException;
import java.io.Reader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The equipment sets of the garrison units, from the data file
 * {@code data/folkhold_world/folkhold/garrison_equipment.json}. A data pack can replace the file. If the file is
 * missing or not valid, the mod uses {@link #DEFAULT}: the set {@code hamlet} only.
 *
 * <p>Format:
 * <pre>
 * {
 *   "hamlet": {
 *     "guard": {"mainhand": "minecraft:iron_sword", "offhand": "minecraft:shield",
 *               "head": "minecraft:iron_helmet", "chest": "tabard", ...},
 *     "archer": {...},
 *     "captain": {...}
 *   },
 *   "village": {
 *     "guard": {"head": {"item": "minecraft:iron_helmet", "enchantments": {"minecraft:protection": 1}}, ...},
 *     "healer": {"mainhand": {"item": "minecraft:potion", "potion": "minecraft:healing"}, ...}
 *   }
 * }
 * </pre>
 * The level table selects the set of each level ({@code equipment} in {@code levels.json}). A set gives the items of
 * each garrison role. A slot ({@code mainhand}, {@code offhand}, {@code head}, {@code chest}, {@code legs},
 * {@code feet}) gives an item id, or an object with the item id, its enchantments and its potion. A slot that is not
 * in the set stays empty. Two values have a special meaning:
 * <ul>
 *   <li>{@code tabard}: a leather chestplate in the color of the faction of the unit;</li>
 *   <li>{@code banner}: the banner of the faction of the unit.</li>
 * </ul>
 * A unit with a set that is not in the file, or a role that is not in its set, gets the items of its role in the set
 * {@code hamlet} of {@link #DEFAULT}. The archers need a bow in the main hand. The faction golem has no equipment: its
 * role has an empty object.
 */
public final class GarrisonEquipment extends SimplePreparableReloadListener<GarrisonEquipment.Sets> {
    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "folkhold/garrison_equipment.json");
    /** The set of the units with no village, and of the units with an unknown set. */
    public static final String DEFAULT_SET = "hamlet";
    /** The value of a slot for a leather chestplate in the color of the faction. */
    public static final String TABARD = "tabard";
    /** The value of a slot for the banner of the faction. */
    public static final String BANNER = "banner";
    /** The slots of an equipment set, in the order of the file. */
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    /**
     * The item of one equipment slot.
     *
     * @param item         the id of the item, or {@link #TABARD}, or {@link #BANNER}
     * @param enchantments the level of each enchantment, by enchantment id
     * @param potion       the id of the potion in the item (for example a potion or a tipped arrow), or null
     */
    public record Piece(String item, Map<ResourceLocation, Integer> enchantments, @Nullable ResourceLocation potion) {
        public Piece {
            enchantments = Map.copyOf(enchantments);
        }

        /** A piece with no enchantment and no potion. */
        public static Piece of(String item) {
            return new Piece(item, Map.of(), null);
        }

        /** A piece with a potion and no enchantment. */
        public static Piece withPotion(Item item, ResourceLocation potion) {
            return new Piece(BuiltInRegistries.ITEM.getKey(item).toString(), Map.of(), potion);
        }

        /**
         * Makes the item stack of the piece for a unit. An enchantment or a potion that the server does not know is
         * ignored.
         *
         * @param unit the unit that wears the piece
         * @return the item stack
         */
        public ItemStack create(FactionUnit unit) {
            ItemStack stack = switch (item) {
                case TABARD -> unit.factionTabard();
                case BANNER -> unit.factionBanner();
                default -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));
            };
            if (!enchantments.isEmpty()) {
                Registry<Enchantment> registry = unit.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
                enchantments.forEach((id, level) -> registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, id))
                        .ifPresentOrElse(holder -> stack.enchant(holder, level),
                                () -> FolkholdWorld.LOGGER.warn("Unknown enchantment {} in {}", id, FILE)));
            }
            if (potion != null) {
                BuiltInRegistries.POTION.getHolder(potion).ifPresentOrElse(
                        holder -> stack.set(DataComponents.POTION_CONTENTS, new PotionContents(holder)),
                        () -> FolkholdWorld.LOGGER.warn("Unknown potion {} in {}", potion, FILE));
            }
            return stack;
        }
    }

    /**
     * The equipment sets: for each set id, the pieces of each role.
     *
     * @param sets the pieces of each slot, by role id, by set id
     */
    public record Sets(Map<String, Map<String, Map<EquipmentSlot, Piece>>> sets) {
        public Sets {
            sets = Map.copyOf(sets);
        }

        /** The ids of the sets. */
        public List<String> ids() {
            return List.copyOf(sets.keySet());
        }

        /**
         * The pieces of a role in a set. An unknown set, or a role that is not in the set, gives the pieces of the
         * role in the set {@link #DEFAULT_SET} of {@link #DEFAULT}.
         *
         * @param set  the id of the set
         * @param role the role of the unit
         * @return the piece of each slot
         */
        public Map<EquipmentSlot, Piece> pieces(String set, GarrisonRole role) {
            Map<EquipmentSlot, Piece> pieces = sets.getOrDefault(set, Map.of()).get(role.serializedName());
            if (pieces != null) {
                return pieces;
            }
            return DEFAULT.sets.get(DEFAULT_SET).getOrDefault(role.serializedName(), Map.of());
        }
    }

    /** The potion of healing in the hand of a healer. */
    private static final Piece HEALING_POTION = Piece.withPotion(Items.POTION,
            ResourceLocation.withDefaultNamespace("healing"));

    /** The equipment of the units of a hamlet. */
    public static final Sets DEFAULT = new Sets(Map.of(DEFAULT_SET, Map.of(
            GarrisonRole.GUARD.serializedName(), pieces(Items.IRON_SWORD, Items.SHIELD, Items.IRON_HELMET, TABARD,
                    Items.IRON_LEGGINGS, Items.IRON_BOOTS),
            GarrisonRole.ARCHER.serializedName(), pieces(Items.BOW, null, Items.CHAINMAIL_HELMET, TABARD,
                    Items.CHAINMAIL_LEGGINGS, Items.LEATHER_BOOTS),
            GarrisonRole.CAPTAIN.serializedName(), pieces(Items.IRON_SWORD, Items.SHIELD, BANNER,
                    Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
            GarrisonRole.HEALER.serializedName(), pieces(HEALING_POTION, null, null, TABARD,
                    Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS),
            GarrisonRole.GOLEM.serializedName(), Map.of())));

    private static Sets current = DEFAULT;

    /** The current equipment sets. */
    public static Sets get() {
        return current;
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new GarrisonEquipment());
    }

    /**
     * Puts the items of a set in the equipment slots of a unit. A slot that is not in the set becomes empty.
     *
     * @param unit the unit
     * @param set  the id of the set
     */
    public static void equip(FactionUnit unit, String set) {
        Map<EquipmentSlot, Piece> pieces = current.pieces(set, unit.role());
        for (EquipmentSlot slot : SLOTS) {
            Piece piece = pieces.get(slot);
            unit.setItemSlot(slot, piece == null ? ItemStack.EMPTY : piece.create(unit));
        }
    }

    @Override
    protected Sets prepare(ResourceManager manager, ProfilerFiller profiler) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            FolkholdWorld.LOGGER.warn("No garrison equipment {}: the mod uses the default equipment", FILE);
            return DEFAULT;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            FolkholdWorld.LOGGER.error("The garrison equipment {} is not valid: the mod uses the default equipment",
                    FILE, e);
            return DEFAULT;
        }
    }

    @Override
    protected void apply(Sets sets, ResourceManager manager, ProfilerFiller profiler) {
        current = sets;
    }

    /**
     * Reads the equipment sets.
     *
     * @param json the content of the file
     * @return the sets
     * @throws RuntimeException if the content is not valid: unknown role, slot or item
     */
    public static Sets parse(JsonObject json) {
        Map<String, Map<String, Map<EquipmentSlot, Piece>>> sets = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> set : json.entrySet()) {
            Map<String, Map<EquipmentSlot, Piece>> roles = new HashMap<>();
            for (Map.Entry<String, JsonElement> role : GsonHelper.convertToJsonObject(set.getValue(), set.getKey())
                    .entrySet()) {
                if (GarrisonRole.fromSerializedName(role.getKey()).isEmpty()) {
                    throw new IllegalArgumentException("Unknown garrison role " + role.getKey() + " in " + set.getKey());
                }
                roles.put(role.getKey(), parsePieces(GsonHelper.convertToJsonObject(role.getValue(), role.getKey())));
            }
            sets.put(set.getKey(), Map.copyOf(roles));
        }
        return new Sets(sets);
    }

    private static Map<EquipmentSlot, Piece> parsePieces(JsonObject json) {
        Map<EquipmentSlot, Piece> pieces = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            EquipmentSlot slot = EquipmentSlot.byName(entry.getKey().toLowerCase(Locale.ROOT));
            if (!SLOTS.contains(slot)) {
                throw new IllegalArgumentException("Unknown equipment slot " + entry.getKey());
            }
            pieces.put(slot, parsePiece(entry.getValue()));
        }
        return Map.copyOf(pieces);
    }

    private static Piece parsePiece(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return checked(Piece.of(json.getAsString()));
        }
        JsonObject object = json.getAsJsonObject();
        Map<ResourceLocation, Integer> enchantments = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(object, "enchantments", new JsonObject())
                .entrySet()) {
            int level = GsonHelper.convertToInt(entry.getValue(), entry.getKey());
            if (level < 1) {
                throw new IllegalArgumentException("Invalid enchantment level " + entry);
            }
            enchantments.put(ResourceLocation.parse(entry.getKey()), level);
        }
        ResourceLocation potion = object.has("potion")
                ? ResourceLocation.parse(GsonHelper.getAsString(object, "potion")) : null;
        return checked(new Piece(GsonHelper.getAsString(object, "item"), enchantments, potion));
    }

    /** Checks that the item and the potion of a piece exist. */
    private static Piece checked(Piece piece) {
        if (!piece.item().equals(TABARD) && !piece.item().equals(BANNER)
                && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(piece.item()))) {
            throw new IllegalArgumentException("Unknown item " + piece.item() + " in " + FILE);
        }
        if (piece.potion() != null && !BuiltInRegistries.POTION.containsKey(piece.potion())) {
            throw new IllegalArgumentException("Unknown potion " + piece.potion() + " in " + FILE);
        }
        return piece;
    }

    /**
     * The pieces of a role. A slot gets an item with no enchantment, a special value ({@link #TABARD},
     * {@link #BANNER}) or a piece. A null value leaves the slot empty.
     */
    private static Map<EquipmentSlot, Piece> pieces(Object mainhand, Object offhand, Object head, Object chest,
            Object legs, Object feet) {
        Map<EquipmentSlot, Piece> pieces = new EnumMap<>(EquipmentSlot.class);
        Object[] items = {mainhand, offhand, head, chest, legs, feet};
        for (int i = 0; i < SLOTS.size(); i++) {
            if (items[i] instanceof Item item) {
                pieces.put(SLOTS.get(i), Piece.of(BuiltInRegistries.ITEM.getKey(item).toString()));
            } else if (items[i] instanceof String special) {
                pieces.put(SLOTS.get(i), Piece.of(special));
            } else if (items[i] instanceof Piece piece) {
                pieces.put(SLOTS.get(i), piece);
            }
        }
        return Map.copyOf(pieces);
    }
}
