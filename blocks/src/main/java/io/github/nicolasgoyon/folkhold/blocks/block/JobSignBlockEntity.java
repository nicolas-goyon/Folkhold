package io.github.nicolasgoyon.folkhold.blocks.block;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlockEntities;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The data of a {@link JobSignBlock}: the id of the job of the building and the id of the icon item.
 *
 * <p>The data has the keys {@value #JOB} and {@value #ICON}, for example
 * {@code {job: "blacksmith", icon: "minecraft:anvil"}}. A structure template keeps the data. The client gets the
 * data to show the icon.
 */
public class JobSignBlockEntity extends BlockEntity {
    /** The key of the job id in the data. */
    public static final String JOB = "job";
    /** The key of the icon item id in the data. */
    public static final String ICON = "icon";

    private String job = "";
    @Nullable
    private ResourceLocation icon;
    /** The icon item for the renderer. It changes with {@link #icon}. */
    private ItemStack iconStack = ItemStack.EMPTY;

    public JobSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.JOB_SIGN.get(), pos, state);
    }

    /** The job id, or an empty string for a sign with no job. */
    public String job() {
        return job;
    }

    /** The id of the icon item. */
    public Optional<ResourceLocation> icon() {
        return Optional.ofNullable(icon);
    }

    /** The icon item, or an empty stack when the sign has no icon or the item does not exist. */
    public ItemStack iconStack() {
        return iconStack;
    }

    private void updateIconStack() {
        iconStack = icon().flatMap(BuiltInRegistries.ITEM::getOptional).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /** Sets the job id and the icon item id, and sends them to the clients. */
    public void setJob(String job, @Nullable ResourceLocation icon) {
        this.job = job;
        this.icon = icon;
        updateIconStack();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!job.isEmpty()) {
            tag.putString(JOB, job);
        }
        if (icon != null) {
            tag.putString(ICON, icon.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        job = tag.getString(JOB);
        icon = tag.contains(ICON, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(ICON)) : null;
        updateIconStack();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
