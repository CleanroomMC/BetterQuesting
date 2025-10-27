package betterquesting.api.questing;

import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.storage.IDatabase;
import betterquesting.api2.storage.INBTPartial;
import betterquesting.api2.storage.INBTProgress;
import betterquesting.api2.utils.ParticipantInfo;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public interface IQuestDatabase extends IDatabase<IQuest>, INBTPartial<NBTTagList, Integer>, INBTProgress<NBTTagList> {
    IQuest createNew(int id);

    /**
     * Variant of {@link #bulkLookup(int...)} that caches the lookup of shared quests.
     * @param pInfo the participant to get shared quests from
     * @return the (cached) database entries for the shared quests
     */
    List<DBEntry<IQuest>> bulkLookupShared(@Nonnull ParticipantInfo pInfo);

    /**
     * Invalidate the cached result from {@link #bulkLookupShared(ParticipantInfo)} for a given participant.
     * @param participantId the participant's id
     */
    void invalidateBulkCache(@Nonnull UUID participantId);
}
