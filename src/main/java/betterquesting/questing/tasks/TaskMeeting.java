package betterquesting.questing.tasks;

import betterquesting.NBTUtil;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.client.gui2.editors.tasks.GuiEditTaskMeeting;
import betterquesting.client.gui2.tasks.PanelTaskMeeting;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.tasks.factory.FactoryTaskMeeting;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TaskMeeting implements ITaskTickable {

    private static final String DEFAULT_ENTITY = "minecraft:villager";
    private static final int DEFAULT_RANGE = 4;
    private static final int DEFAULT_AMOUNT = 1;
    private static final boolean DEFAULT_IGNORE_NBT = true;
    private static final boolean DEFAULT_SUBTYPES = true;
    private static final boolean DEFAULT_OPTIONAL = false;
    private final Set<UUID> completeUsers = new TreeSet<>();

    public String idName = DEFAULT_ENTITY;
    public int range = DEFAULT_RANGE;
    public int amount = DEFAULT_AMOUNT;
    public boolean ignoreNBT = DEFAULT_IGNORE_NBT;
    public boolean subtypes = DEFAULT_SUBTYPES;
    public boolean optional = DEFAULT_OPTIONAL;

    /**
     * NBT representation of the intended target. Used only for NBT comparison checks
     */
    public NBTTagCompound targetTags = new NBTTagCompound();

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskMeeting.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.meeting";
    }

    @Override
    public boolean isComplete(UUID uuid) {
        return completeUsers.contains(uuid);
    }

    @Override
    public void setComplete(UUID uuid) {
        completeUsers.add(uuid);
    }

    @Override
    public void resetUser(@Nullable UUID uuid) {
        if (uuid == null) {
            completeUsers.clear();
        } else {
            completeUsers.remove(uuid);
        }
    }

    @Override
    public void tickTask(@Nonnull ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        if (pInfo.PLAYER.ticksExisted % 60 == 0) detect(pInfo, quest);
    }

    @Override
    public void detect(@Nonnull ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        if (!pInfo.PLAYER.isEntityAlive()) return;

        ResourceLocation targetID = new ResourceLocation(idName);
        Class<? extends Entity> target = EntityList.getClass(targetID);
        if (target == null) return;

        List<Entity> list = pInfo.PLAYER.world.getEntitiesWithinAABBExcludingEntity(pInfo.PLAYER, pInfo.PLAYER.getEntityBoundingBox().expand(range, range, range));

        int n = 0;

        for (Entity entity : list) {
            Class<? extends Entity> subject = entity.getClass();
            ResourceLocation subjectID = EntityList.getKey(subject);

            if (subjectID == null) {
                continue;
            } else if (subtypes && !target.isAssignableFrom(subject)) {
                continue; // This is not the intended target or sub-type
            } else if (!subtypes && !subjectID.equals(targetID)) {
                continue; // This isn't the exact target required
            }

            if (!ignoreNBT) {
                NBTTagCompound subjectTags = new NBTTagCompound();
                entity.writeToNBTOptional(subjectTags);
                if (!ItemComparison.CompareNBTTag(targetTags, subjectTags, true)) continue;
            }

            if (++n >= amount) {
                pInfo.ALL_UUIDS.forEach((uuid) -> {
                    if (!isComplete(uuid)) setComplete(uuid);
                });
                pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
                return;
            }
        }
    }

    @Deprecated
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound json, boolean reduce) {
        json.setString("target", idName);
        NBTUtil.setString(json, "target", idName, DEFAULT_ENTITY, reduce);
        NBTUtil.setInteger(json, "range", range, DEFAULT_RANGE, reduce);
        NBTUtil.setInteger(json, "amount", amount, DEFAULT_AMOUNT, reduce);
        NBTUtil.setBoolean(json, "subtypes", subtypes, DEFAULT_SUBTYPES, reduce);
        NBTUtil.setBoolean(json, "ignoreNBT", ignoreNBT, DEFAULT_IGNORE_NBT, reduce);
        NBTUtil.setTag(json, "targetNBT", targetTags, reduce);
        NBTUtil.setBoolean(json, "optional", optional, DEFAULT_OPTIONAL, reduce);

        return json;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        idName = NBTUtil.getString(nbt, "target", DEFAULT_ENTITY);
        range = NBTUtil.getInteger(nbt, "range", DEFAULT_RANGE);
        amount = NBTUtil.getInteger(nbt, "amount", DEFAULT_AMOUNT);
        subtypes = NBTUtil.getBoolean(nbt, "subtypes", DEFAULT_SUBTYPES);
        ignoreNBT = NBTUtil.getBoolean(nbt, "ignoreNBT", DEFAULT_IGNORE_NBT);
        targetTags = nbt.getCompoundTag("targetNBT");
        optional = NBTUtil.getBoolean(nbt, "optional", DEFAULT_OPTIONAL);
    }

    @Override
    public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, @Nullable List<UUID> users) {
        NBTTagList jArray = new NBTTagList();

        completeUsers.forEach((uuid) -> {
            if (users == null || users.contains(uuid)) jArray.appendTag(new NBTTagString(uuid.toString()));
        });

        nbt.setTag("completeUsers", jArray);

        return nbt;
    }

    @Override
    public void readProgressFromNBT(NBTTagCompound nbt, boolean merge) {
        if (!merge) completeUsers.clear();
        NBTTagList cList = nbt.getTagList("completeUsers", 8);
        for (int i = 0; i < cList.tagCount(); i++) {
            try {
                completeUsers.add(UUID.fromString(cList.getStringTagAt(i)));
            } catch (Exception e) {
                BetterQuesting.logger.log(Level.ERROR, "Unable to load UUID for task", e);
            }
        }
    }

    /**
     * Returns a new editor screen for this Reward type to edit the given data
     */
    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest) {
        return new GuiEditTaskMeeting(parent, quest, this);
    }

    @Override
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskMeeting(rect, this);
    }

    @Override
    public boolean ignored(UUID uuid) {
        return optional;
    }

    @Override
    public List<String> getTextForSearch() {
        return Collections.singletonList(idName);
    }
}
