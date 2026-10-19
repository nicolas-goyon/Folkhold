package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaptainEntity;
import io.github.nicolasgoyon.folkhold.world.entity.CaravanMerchant;
import io.github.nicolasgoyon.folkhold.world.entity.FactionGolemEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionHorseEntity;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.entity.HealerEntity;
import io.github.nicolasgoyon.folkhold.world.entity.RiderEntity;
import io.github.nicolasgoyon.folkhold.world.entity.VillageCat;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Cat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types of the world mod. */
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FolkholdWorld.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<GuardEntity>> GUARD = ENTITY_TYPES.register("guard",
            () -> EntityType.Builder.of(GuardEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("guard"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArcherEntity>> ARCHER = ENTITY_TYPES.register("archer",
            () -> EntityType.Builder.of(ArcherEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("archer"));

    public static final DeferredHolder<EntityType<?>, EntityType<CaptainEntity>> CAPTAIN = ENTITY_TYPES.register("captain",
            () -> EntityType.Builder.of(CaptainEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("captain"));

    public static final DeferredHolder<EntityType<?>, EntityType<HealerEntity>> HEALER = ENTITY_TYPES.register("healer",
            () -> EntityType.Builder.of(HealerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("healer"));

    /** The faction golem has the size of the vanilla iron golem. */
    public static final DeferredHolder<EntityType<?>, EntityType<FactionGolemEntity>> FACTION_GOLEM =
            ENTITY_TYPES.register("faction_golem", () -> EntityType.Builder.<FactionGolemEntity>of(
                            FactionGolemEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.7F)
                    .clientTrackingRange(10)
                    .build("faction_golem"));

    /** On a horse, the rider sits with its hips on the saddle, as a vanilla zombie or skeleton. */
    public static final DeferredHolder<EntityType<?>, EntityType<RiderEntity>> RIDER = ENTITY_TYPES.register("rider",
            () -> EntityType.Builder.of(RiderEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .ridingOffset(-0.7F)
                    .clientTrackingRange(10)
                    .build("rider"));

    /** The horse of a rider has the size and the saddle position of the vanilla horse. */
    public static final DeferredHolder<EntityType<?>, EntityType<FactionHorseEntity>> FACTION_HORSE =
            ENTITY_TYPES.register("faction_horse", () -> EntityType.Builder.<FactionHorseEntity>of(
                            FactionHorseEntity::new, MobCategory.MISC)
                    .sized(1.3964844F, 1.6F)
                    .eyeHeight(1.52F)
                    .passengerAttachments(1.44375F)
                    .clientTrackingRange(10)
                    .build("faction_horse"));

    public static final DeferredHolder<EntityType<?>, EntityType<VillageCat>> VILLAGE_CAT =
            ENTITY_TYPES.register("village_cat", () -> EntityType.Builder.<VillageCat>of(VillageCat::new,
                            MobCategory.CREATURE)
                    .sized(0.6F, 0.7F)
                    .clientTrackingRange(8)
                    .build("village_cat"));

    public static final DeferredHolder<EntityType<?>, EntityType<CaravanMerchant>> CARAVAN_MERCHANT =
            ENTITY_TYPES.register("caravan_merchant", () -> EntityType.Builder.<CaravanMerchant>of(
                            CaravanMerchant::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("caravan_merchant"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::onAttributeCreation);
    }

    private static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(GUARD.get(), GuardEntity.createAttributes().build());
        event.put(ARCHER.get(), ArcherEntity.createAttributes().build());
        event.put(CAPTAIN.get(), CaptainEntity.createAttributes().build());
        event.put(HEALER.get(), HealerEntity.createAttributes().build());
        event.put(FACTION_GOLEM.get(), FactionGolemEntity.createAttributes().build());
        event.put(RIDER.get(), RiderEntity.createAttributes().build());
        event.put(FACTION_HORSE.get(), FactionHorseEntity.createAttributes().build());
        event.put(VILLAGE_CAT.get(), Cat.createAttributes().build());
        event.put(CARAVAN_MERCHANT.get(), CaravanMerchant.createAttributes().build());
    }
}
