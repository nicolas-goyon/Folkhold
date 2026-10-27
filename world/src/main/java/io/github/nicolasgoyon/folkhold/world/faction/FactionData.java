package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.sim.faction.Attitude;
import io.github.nicolasgoyon.folkhold.core.sim.faction.CultureRegions;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionGenerator;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionTrait;
import io.github.nicolasgoyon.folkhold.core.sim.faction.Relations;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The factions of a world and their relations. Saved in the Overworld data ({@code folkhold_factions.dat}).
 *
 * <p>The first access makes the factions from the world seed and the cultures (see {@link FactionGenerator}). A
 * position belongs to a faction of the culture of its region (see {@link CultureMap}).
 */
public final class FactionData extends SavedData {
    private static final String FILE_ID = "folkhold_factions";

    private final List<FactionSpec> factions = new ArrayList<>();
    private Relations relations = new Relations();
    /** The cultures of the regions of the Overworld. Not saved: the seed and the biomes give it again. */
    private CultureMap cultures;

    private FactionData() {
    }

    /**
     * The faction data of the server. The first call makes the factions.
     *
     * @param server the server
     * @return the faction data
     */
    public static FactionData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        FactionData data = overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FactionData::new, FactionData::load, null), FILE_ID);
        if (data.factions.isEmpty()) {
            data.generate(server.getWorldData().worldGenOptions().seed(), overworld.getSharedSpawnPos());
        }
        if (data.cultures == null) {
            data.cultures = CultureMap.of(overworld);
        }
        return data;
    }

    private void generate(long seed, BlockPos spawn) {
        List<FactionSpec> made = FactionGenerator.generate(seed,
                Cultures.ALL.stream().map(Culture::info).toList(), spawn.getX(), spawn.getZ(),
                FactionGenerator.Settings.DEFAULT);
        factions.addAll(made);
        relations = Relations.initial(made);
        setDirty();
        FolkholdWorld.LOGGER.info("Created {} factions: {}", made.size(),
                made.stream().map(f -> f.name() + " (" + f.attitude() + ")").toList());
    }

    public List<FactionSpec> factions() {
        return List.copyOf(factions);
    }

    public Relations relations() {
        return relations;
    }

    public Optional<FactionSpec> byId(String id) {
        return factions.stream().filter(faction -> faction.id().equals(id)).findFirst();
    }

    /**
     * The faction that owns a position: the nearest faction of the culture of the region of the position (see
     * {@link CultureRegions}).
     *
     * @param pos the position
     * @return the owner faction
     */
    public FactionSpec ownerAt(BlockPos pos) {
        if (cultures == null) {
            return FactionGenerator.owner(factions, pos.getX(), pos.getZ());
        }
        return CultureRegions.owner(factions, cultures.cultureAt(pos.getX(), pos.getZ()), pos.getX(), pos.getZ());
    }

    /**
     * The culture of the region of a position.
     *
     * @param pos the position
     * @return the id of the culture
     */
    public String cultureAt(BlockPos pos) {
        return ownerAt(pos).cultureId();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (FactionSpec faction : factions) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", faction.id());
            entry.putString("Culture", faction.cultureId());
            entry.putString("Name", faction.name());
            entry.putInt("Color", faction.color());
            entry.putString("Attitude", faction.attitude().name().toLowerCase(Locale.ROOT));
            ListTag traits = new ListTag();
            for (FactionTrait trait : faction.traits()) {
                traits.add(StringTag.valueOf(trait.name().toLowerCase(Locale.ROOT)));
            }
            entry.put("Traits", traits);
            entry.putInt("CenterX", faction.centerX());
            entry.putInt("CenterZ", faction.centerZ());
            list.add(entry);
        }
        tag.put("Factions", list);
        CompoundTag relationTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : relations.asMap().entrySet()) {
            relationTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Relations", relationTag);
        return tag;
    }

    /** The constant of an enum with a saved name (any case), or empty. */
    private static <E extends Enum<E>> Optional<E> constant(Class<E> type, String name) {
        for (E value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(name)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Reads the data from a tag. {@link #save(CompoundTag, HolderLookup.Provider)} writes this tag. An unknown
     * trait name is skipped, and an unknown attitude name gives {@link Attitude#NEUTRAL}, with a warning in the
     * log: a renamed constant does not stop the world load.
     *
     * @param tag        the tag
     * @param registries the registries
     * @return the data
     */
    public static FactionData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionData data = new FactionData();
        ListTag list = tag.getList("Factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString("Id");
            Set<FactionTrait> traits = EnumSet.noneOf(FactionTrait.class);
            ListTag traitList = entry.getList("Traits", Tag.TAG_STRING);
            for (int t = 0; t < traitList.size(); t++) {
                String name = traitList.getString(t);
                Optional<FactionTrait> trait = constant(FactionTrait.class, name);
                if (trait.isPresent()) {
                    traits.add(trait.get());
                } else {
                    FolkholdWorld.LOGGER.warn("Unknown trait \"{}\" of the faction {}: skipped", name, id);
                }
            }
            String attitudeName = entry.getString("Attitude");
            Attitude attitude = constant(Attitude.class, attitudeName).orElseGet(() -> {
                FolkholdWorld.LOGGER.warn("Unknown attitude \"{}\" of the faction {}: neutral", attitudeName, id);
                return Attitude.NEUTRAL;
            });
            data.factions.add(new FactionSpec(id, entry.getString("Culture"), entry.getString("Name"),
                    entry.getInt("Color"), attitude, traits, entry.getInt("CenterX"), entry.getInt("CenterZ")));
        }
        CompoundTag relationTag = tag.getCompound("Relations");
        for (String key : relationTag.getAllKeys()) {
            data.relations.putRaw(key, relationTag.getInt(key));
        }
        return data;
    }
}
