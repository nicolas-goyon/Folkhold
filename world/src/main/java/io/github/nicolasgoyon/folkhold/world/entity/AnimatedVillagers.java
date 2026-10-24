package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The animations of the villagers with an animated model: the builders and the handymen. These villagers stay
 * vanilla villagers (bed, bell, raids, zombies). Only their rendering changes: a GeckoLib model with arms that move
 * replaces the vanilla model, which has crossed arms.
 *
 * <ul>
 *   <li>The model and the animations: {@code geo/entity/animated_villager.geo.json} and
 *       {@code animations/entity/animated_villager.animation.json}.</li>
 *   <li>The movement controller plays the walk or the idle animation.</li>
 *   <li>The server plays the actions on the clients ({@link #place}, {@link #use}).</li>
 * </ul>
 *
 * <p>One instance serves all the villagers. GeckoLib keeps the state of the animations for each entity. The instance
 * must exist on the server and on the client before the first action ({@link #init}).
 */
public final class AnimatedVillagers implements GeoReplacedEntity {
    /** The controller of the actions. */
    public static final String ACTION_CONTROLLER = "action";
    /** The action of a block placement: the right arm goes forward and down. */
    public static final String PLACE = "place";
    /** The action of a use, for example to light a brazier: the two arms go forward. */
    public static final String USE = "use";

    private static final RawAnimation PLACE_ANIMATION = RawAnimation.begin().thenPlay("action.place");
    private static final RawAnimation USE_ANIMATION = RawAnimation.begin().thenPlay("action.use");
    private static final AnimatedVillagers INSTANCE = new AnimatedVillagers();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private AnimatedVillagers() {
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /** The instance for all the animated villagers. */
    public static AnimatedVillagers get() {
        return INSTANCE;
    }

    /** Creates the instance and registers it for the actions from the server. Call it at the start of the mod. */
    public static void init() {
        // The class initialization creates and registers the instance.
    }

    /** True if a villager has the animated model: a builder or a handyman. */
    public static boolean isAnimated(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return !villager.isBaby()
                && (profession == ModProfessions.BUILDER.get() || profession == ModProfessions.HANDYMAN.get());
    }

    /**
     * Plays the action of a block placement on the clients near the villager. Server side.
     *
     * @param villager the villager
     */
    public static void place(Villager villager) {
        INSTANCE.triggerAnim(villager, ACTION_CONTROLLER, PLACE);
    }

    /**
     * Plays the action of a use on the clients near the villager, for example to light a brazier. Server side.
     *
     * @param villager the villager
     */
    public static void use(Villager villager) {
        INSTANCE.triggerAnim(villager, ACTION_CONTROLLER, USE);
    }

    @Override
    public EntityType<?> getReplacingEntityType() {
        return EntityType.VILLAGER;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController(this));
        controllers.add(new AnimationController<>(this, ACTION_CONTROLLER, 2, state -> PlayState.STOP)
                .triggerableAnim(PLACE, PLACE_ANIMATION)
                .triggerableAnim(USE, USE_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
