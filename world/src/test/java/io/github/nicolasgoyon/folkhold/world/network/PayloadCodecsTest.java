package io.github.nicolasgoyon.folkhold.world.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/**
 * Round trips of the hand-written stream codecs: a payload written to a buffer and read back is equal to the
 * payload. The components are literal texts: their codec compares them by content.
 */
class PayloadCodecsTest {
    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
        codec.encode(buf, value);
        T read = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "The codec must read all the bytes that it wrote");
        return read;
    }

    private static Section section() {
        return new Section(Component.literal("Economy"), List.of(
                new Row(Component.literal("Population"), Component.literal("12")),
                Row.bar(Component.literal("Food"), Component.literal("8 / 10"), 8, 10),
                Row.segments(Component.literal("Garrison"), Component.literal("3"), 3, 5, 1)));
    }

    @Test
    void adminList() {
        AdminListPayload payload = new AdminListPayload(List.of(
                new AdminListPayload.Summary("id-1", "Northam", Component.literal("Ash"), 7,
                        Component.literal("Town"), true, 100, -200, 15, 320, true),
                new AdminListPayload.Summary("id-2", "Ruinton", Component.literal("Oak"), 1,
                        Component.literal("Hamlet"), false, -5, 5, 0, 0, false)));
        assertEquals(payload, roundTrip(AdminListPayload.STREAM_CODEC, payload));
    }

    @Test
    void adminDetails() {
        AdminDetailsPayload payload = new AdminDetailsPayload("id-1", Component.literal("Northam"), true,
                Component.literal("Ash"), "minecraft:plains", 7, Component.literal("Town"), 1, 100, 64, -200,
                List.of(section()), List.of(new AdminDetailsPayload.Stock("minecraft:bread", 40),
                        new AdminDetailsPayload.Stock("minecraft:iron_ingot", 3)), 2);
        assertEquals(payload, roundTrip(AdminDetailsPayload.STREAM_CODEC, payload));
    }

    @Test
    void villageHeart() {
        VillageHeartPayload payload = new VillageHeartPayload("id-1", Component.literal("Northam"),
                Component.literal("Ash"), Component.literal("Highland"), 0xAA5500, 7, Component.literal("Town"),
                "calm", "friend", 250, 300, 200, 60, Component.literal("Welcome"), List.of(
                        new VillageHeartPayload.Service(Component.literal("Quests"), Component.literal("✓"),
                                VillageHeartPayload.AVAILABLE, Component.literal("Two offers"),
                                List.of("minecraft:bread"), VillageHeartPayload.ACTION_QUEST_BOARD),
                        new VillageHeartPayload.Service(Component.literal("Inn"), Component.literal("–"),
                                VillageHeartPayload.LATER, Component.empty(), List.of(),
                                VillageHeartPayload.ACTION_NONE)),
                Component.literal("Do not pillage the bank."), true, List.of(section()));
        assertEquals(payload, roundTrip(VillageHeartPayload.STREAM_CODEC, payload));
    }

    @Test
    void questBoard() {
        QuestBoardPayload payload = new QuestBoardPayload(Component.literal("Northam"), "id-1", "neutral", 3, 20,
                List.of(new QuestBoardPayload.Offer("q-1", "delivery", "minecraft:bread", Component.literal("Bring"),
                        Component.literal("Delivery · 20 min"), Component.literal("5 emeralds"),
                        Component.literal("4 / 8"), -1, 5, 20, Optional.empty()),
                        new QuestBoardPayload.Offer("q-2", "escort", "minecraft:chest", Component.literal("Escort"),
                                Component.literal("Escort · 500 blocks"), Component.literal("12 emeralds"),
                                Component.empty(), 90, 12, 30, Optional.of(Component.literal("Friends only")))),
                List.of(new QuestBoardPayload.Active("q-3", "minecraft:bread", Component.literal("Bring"),
                        Component.literal("Northam · 5"), 600, QuestBoardPayload.ACTION_DELIVER,
                        Optional.of(Component.literal("Missing 2")), false)),
                Optional.of(Component.literal("Quest accepted")), true);
        assertEquals(payload, roundTrip(QuestBoardPayload.STREAM_CODEC, payload));
    }

    @Test
    void questLog() {
        QuestLogPayload payload = new QuestLogPayload(3, List.of(
                new QuestLogPayload.Entry("q-1", "minecraft:bread", Component.literal("Bring 8 bread"), 8, 600,
                        Optional.empty(), Optional.of(new BlockPos(100, 64, -200)), QuestLogPayload.NO_ENTITY),
                new QuestLogPayload.Entry("q-2", "minecraft:chest", Component.literal("Escort to Southam"), 0, 1200,
                        Optional.of(new QuestLogPayload.Escort(120, 500, 18, 20, 12, 48, 30)), Optional.empty(),
                        42)));
        assertEquals(payload, roundTrip(QuestLogPayload.STREAM_CODEC, payload));
    }

    @Test
    void bankPillageRequest() {
        BankPillageRequestPayload payload = new BankPillageRequestPayload(new BlockPos(1, -60, 3),
                Component.literal("Northam"), Component.literal("Ash"), "friend", 300, "hostile", -700, 4, 2);
        assertEquals(payload, roundTrip(BankPillageRequestPayload.STREAM_CODEC, payload));
    }
}
