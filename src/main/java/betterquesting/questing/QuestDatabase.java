package betterquesting.questing;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.storage.RandomIndexDatabase;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.party.PartyManager;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class QuestDatabase extends RandomIndexDatabase<IQuest> implements IQuestDatabase {
    public static final QuestDatabase INSTANCE = new QuestDatabase();

    /** A cache for bulk lookups. The key is a party's UUID (each member has the same shared quests),
     * or if not in a party, the player's UUID.
     */
    private final Cache<UUID, List<DBEntry<IQuest>>> bulkCache = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES).build();

    @Override
    public synchronized IQuest createNew(int id) {
        IQuest quest = new QuestInstance();
        if (id >= 0) this.add(id, quest);
        return quest;
    }

    @Override
    public List<DBEntry<IQuest>> bulkLookupShared(@Nonnull ParticipantInfo pInfo) {
        var identifier = pInfo.PARTY_INSTANCE == null ? pInfo.UUID : pInfo.PARTY_INSTANCE.getValue().getID();
        try {
            return bulkCache.get(identifier,
                    () -> Collections.unmodifiableList(this.bulkLookup(pInfo.getSharedQuests())));
        } catch (ExecutionException e) {
            BetterQuesting.logger.error("Failed to get cached bulkLookup entries: {}", e);
            return this.bulkLookup(pInfo.getSharedQuests());
        }
    }

    @Override
    public void invalidateBulkCache(@Nonnull UUID participantId) {
        var party = PartyManager.INSTANCE.getParty(participantId);
        var identifier = party == null ? participantId : party.getValue().getID();
        bulkCache.invalidate(identifier);
    }

    @Override
    public synchronized boolean removeID(int id) {
        boolean success = super.removeID(id);
        if (success) for (DBEntry<IQuest> entry : getEntries()) removeReq(entry.getValue(), id);
        return success;
    }

    @Override
    public synchronized boolean removeValue(IQuest value) {
        int id = this.getID(value);
        if (id < 0) return false;
        boolean success = this.removeValue(value);
        if (success) for (DBEntry<IQuest> entry : getEntries()) removeReq(entry.getValue(), id);
        return success;
    }

    @Override
    public synchronized void reset() {
        super.reset();
        bulkCache.invalidateAll();
    }

    private void removeReq(IQuest quest, int id) {
        int[] orig = quest.getRequirements();
        if (orig.length <= 0) return;
        boolean hasRemoved = false;
        int[] rem = new int[orig.length - 1];
        for (int i = 0; i < orig.length; i++) {
            if (!hasRemoved && orig[i] == id) {
                hasRemoved = true;
                continue;
            } else if (!hasRemoved && i >= rem.length) break;

            rem[!hasRemoved ? i : (i - 1)] = orig[i];
        }

        if (hasRemoved) quest.setRequirements(rem);
    }

    @Deprecated
    @Override
    public synchronized NBTTagList writeToNBT(NBTTagList nbt, @Nullable List<Integer> subset) {
        return writeToNBT(nbt, subset, false);
    }

    @Override
    public synchronized NBTTagList writeToNBT(NBTTagList nbt, @Nullable List<Integer> subset, boolean reduce) {
        for (DBEntry<IQuest> entry : this.getEntries()) {
            if (subset != null && !subset.contains(entry.getID())) continue;
            NBTTagCompound jq = entry.getValue().writeToNBT(new NBTTagCompound(), reduce);
            if (subset != null && jq.isEmpty()) continue;
            jq.setInteger("questID", entry.getID());
            nbt.appendTag(jq);
        }

        return nbt;
    }

    @Override
    public synchronized void readFromNBT(NBTTagList nbt, boolean merge) {
        if (!merge) this.reset();

        for (int i = 0; i < nbt.tagCount(); i++) {
            NBTTagCompound qTag = nbt.getCompoundTagAt(i);

            int qID = qTag.hasKey("questID", 99) ? qTag.getInteger("questID") : -1;
            if (qID < 0) continue;

            IQuest quest = getValue(qID);
            if (quest == null) quest = this.createNew(qID);
            quest.readFromNBT(qTag);
        }
    }

    @Override
    public synchronized NBTTagList writeProgressToNBT(NBTTagList json, @Nullable List<UUID> users) {
        for (DBEntry<IQuest> entry : this.getEntries()) {
            NBTTagCompound jq = entry.getValue().writeProgressToNBT(new NBTTagCompound(), users);
            jq.setInteger("questID", entry.getID());
            json.appendTag(jq);
        }

        return json;
    }

    @Override
    public synchronized void readProgressFromNBT(NBTTagList json, boolean merge) {
        for (int i = 0; i < json.tagCount(); i++) {
            NBTTagCompound qTag = json.getCompoundTagAt(i);

            int qID = qTag.hasKey("questID", 99) ? qTag.getInteger("questID") : -1;
            if (qID < 0) continue;

            IQuest quest = getValue(qID);
            if (quest != null) quest.readProgressFromNBT(qTag, merge);
        }
    }
}
