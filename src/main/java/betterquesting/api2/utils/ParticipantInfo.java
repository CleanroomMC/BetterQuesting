package betterquesting.api2.utils;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.cache.CapabilityProviderQuestCache;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.questing.party.PartyInventory;
import betterquesting.questing.party.PartyManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.*;

public class ParticipantInfo {
    public final EntityPlayer PLAYER;
    public final UUID UUID;

    public final List<UUID> ALL_UUIDS;
    public final List<EntityPlayer> ACTIVE_PLAYERS;
    public final List<UUID> ACTIVE_UUIDS;

    public final DBEntry<IParty> PARTY_INSTANCE;

    private PartyInventory inventory;

    public ParticipantInfo(@Nonnull EntityPlayer player) {
        this.PLAYER = player;
        this.UUID = QuestingAPI.getQuestingUUID(player);
        this.PARTY_INSTANCE = PartyManager.INSTANCE.getParty(this.UUID);

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();

        if (PARTY_INSTANCE == null || server == null || player instanceof FakePlayer) {
            ACTIVE_PLAYERS = Collections.singletonList(player);
            ACTIVE_UUIDS = Collections.singletonList(UUID);
            ALL_UUIDS = Collections.singletonList(UUID);
            return;
        }

        List<EntityPlayer> actPl = new ArrayList<>();
        List<UUID> actID = new ArrayList<>();
        List<UUID> allID = new ArrayList<>();

        for (UUID mem : PARTY_INSTANCE.getValue().getMembers()) {
            allID.add(mem);
            EntityPlayer pMem = server.getPlayerList().getPlayerByUUID(mem);
            //noinspection ConstantConditions
            if (pMem != null) {
                actPl.add(pMem);
                actID.add(mem);
            }
        }

        // Really shouldn't be modifying these lists anyway but just for safety
        this.ACTIVE_PLAYERS = Collections.unmodifiableList(actPl);
        this.ACTIVE_UUIDS = Collections.unmodifiableList(actID);
        this.ALL_UUIDS = Collections.unmodifiableList(allID);
    }

    public void markDirty(@Nonnull List<Integer> questIDs) // Only marks quests dirty for the immediate participating player
    {
        QuestCache qc = PLAYER.getCapability(CapabilityProviderQuestCache.CAP_QUEST_CACHE, null);
        if (qc != null) questIDs.forEach(qc::markQuestDirty);
    }

    public void markDirtyParty(@Nonnull List<Integer> questIDs) // Marks quests as dirty for the entire (active) party
    {
        if (ACTIVE_PLAYERS.size() <= 0 || questIDs.size() <= 0) return;
        ACTIVE_PLAYERS.forEach((value) -> {
            QuestCache qc = value.getCapability(CapabilityProviderQuestCache.CAP_QUEST_CACHE, null);
            if (qc != null) questIDs.forEach(qc::markQuestDirty);
        });
    }

    @Nonnull
    public int[] getSharedQuests() // Returns an array of all quests which one or more participants have unlocked
    {
        List<int[]> playerQuestLists = new ArrayList<>(ACTIVE_PLAYERS.size());
        for (var player : ACTIVE_PLAYERS) {
            QuestCache qc = player.getCapability(CapabilityProviderQuestCache.CAP_QUEST_CACHE, null);
            if (qc != null) {
                playerQuestLists.add(qc.getActiveQuests());
            }
        }
        if (playerQuestLists.isEmpty()) {
            return new int[0];
        }
        else if (playerQuestLists.size() == 1) {
            // No need to merge
            return playerQuestLists.get(0);
        }
        PartyQuestMerger merger = new PartyQuestMerger(playerQuestLists);
        return merger.getSharedQuests();
    }

    public PartyInventory getPartyInventory() {
        if (inventory == null) {
            inventory = new PartyInventory(PLAYER, ACTIVE_PLAYERS);
        }
        return inventory;
    }

}
