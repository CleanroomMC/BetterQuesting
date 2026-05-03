package betterquesting.questing.tasks;

import betterquesting.NBTUtil;
import betterquesting.NbtBlockType;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.client.gui2.tasks.PanelTaskInteractItem;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.tasks.factory.FactoryTaskInteractItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TaskInteractItem implements ITask {

    private static final boolean DEFAULT_PARTIAL_MATCH = true;
    private static final boolean DEFAULT_IGNORE_NBT = false;
    private static final boolean DEFAULT_USE_MAIN_HAND = true;
    private static final boolean DEFAULT_USE_OFFHAND = true;
    private static final boolean DEFAULT_ON_INTERACT = true;
    private static final boolean DEFAULT_ON_HIT = false;
    private static final int DEFAULT_REQUIRED = 1;
    private static final boolean DEFAULT_OPTIONAL = false;
    private final Set<UUID> completeUsers = new TreeSet<>();
    private final TreeMap<UUID, Integer> userProgress = new TreeMap<>();

    public BigItemStack targetItem = new BigItemStack(Items.AIR);
    public final NbtBlockType targetBlock = new NbtBlockType(Blocks.AIR);
    public boolean partialMatch = DEFAULT_PARTIAL_MATCH;
    public boolean ignoreNBT = DEFAULT_IGNORE_NBT;
    public boolean useMainHand = DEFAULT_USE_MAIN_HAND;
    public boolean useOffHand = DEFAULT_USE_OFFHAND;
    public boolean onInteract = DEFAULT_ON_INTERACT;
    public boolean onHit = DEFAULT_ON_HIT;
    public int required = DEFAULT_REQUIRED;
    public boolean optional = DEFAULT_OPTIONAL;

    @Override
    public String getUnlocalisedName() {
        return BetterQuesting.MODID_STD + ".task.interact_item";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskInteractItem.INSTANCE.getRegistryName();
    }

    public void onInteract(ParticipantInfo pInfo, DBEntry<IQuest> quest, EnumHand hand, ItemStack item, IBlockState state, BlockPos pos, boolean isHit) {
        if ((!onHit && isHit) || (!onInteract && !isHit)) return;
        if ((!useMainHand && hand == EnumHand.MAIN_HAND) || (!useOffHand && hand == EnumHand.OFF_HAND)) return;

        if (targetBlock.b != Blocks.AIR) {
            if (state.getBlock() == Blocks.AIR) return;
            TileEntity tile = state.getBlock().hasTileEntity(state) ? pInfo.PLAYER.world.getTileEntity(pos) : null;
            NBTTagCompound tags = tile == null ? null : tile.writeToNBT(new NBTTagCompound());

            int tmpMeta = (targetBlock.m < 0 || targetBlock.m == OreDictionary.WILDCARD_VALUE) ? OreDictionary.WILDCARD_VALUE : state.getBlock().getMetaFromState(state);
            boolean oreMatch = targetBlock.oreDict.length() > 0 && OreDictionary.getOres(targetBlock.oreDict).contains(new ItemStack(state.getBlock(), 1, tmpMeta));

            if ((!oreMatch && (state.getBlock() != targetBlock.b || (targetBlock.m >= 0 && state.getBlock().getMetaFromState(state) != targetBlock.m))) || !ItemComparison.CompareNBTTag(targetBlock.tags, tags, true)) {
                return;
            }
        }

        // Check that we're interacting using the correct item (if one is needed)
        if (targetItem.getBaseStack().getItem() != Items.AIR) {
            if (!ItemComparison.BigStackMatch(targetItem, item, ignoreNBT, partialMatch)) {
                return;
            }
        }

        final List<Tuple<UUID, Integer>> progress = getBulkProgress(pInfo.ALL_UUIDS);

        progress.forEach((value) -> {
            if (isComplete(value.getFirst())) return;
            int np = Math.min(required, value.getSecond() + 1);
            setUserProgress(value.getFirst(), np);
            if (np >= required) setComplete(value.getFirst());
        });

        pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
    }

    @Override
    public void detect(ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        final List<Tuple<UUID, Integer>> progress = getBulkProgress(pInfo.ALL_UUIDS);

        progress.forEach((value) -> {
            if (value.getSecond() >= required) setComplete(value.getFirst());
        });

        pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
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
            userProgress.clear();
        } else {
            completeUsers.remove(uuid);
            userProgress.remove(uuid);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskInteractItem(rect, this);
    }

    @Override
    @Nullable
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest) {
        return null;
    }

    @Override
    public void readProgressFromNBT(NBTTagCompound nbt, boolean merge) {
        if (!merge) {
            completeUsers.clear();
            userProgress.clear();
        }

        NBTTagList cList = nbt.getTagList("completeUsers", 8);
        for (int i = 0; i < cList.tagCount(); i++) {
            try {
                completeUsers.add(UUID.fromString(cList.getStringTagAt(i)));
            } catch (Exception e) {
                BetterQuesting.logger.log(Level.ERROR, "Unable to load UUID for task", e);
            }
        }

        NBTTagList pList = nbt.getTagList("userProgress", 10);
        for (int n = 0; n < pList.tagCount(); n++) {
            try {
                NBTTagCompound pTag = pList.getCompoundTagAt(n);
                UUID uuid = UUID.fromString(pTag.getString("uuid"));
                userProgress.put(uuid, pTag.getInteger("value"));
            } catch (Exception e) {
                BetterQuesting.logger.log(Level.ERROR, "Unable to load user progress for task", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, @Nullable List<UUID> users) {
        NBTTagList jArray = new NBTTagList();
        NBTTagList progArray = new NBTTagList();

        if (users != null) {
            users.forEach((uuid) -> {
                if (completeUsers.contains(uuid)) jArray.appendTag(new NBTTagString(uuid.toString()));

                Integer data = userProgress.get(uuid);
                if (data != null) {
                    NBTTagCompound pJson = new NBTTagCompound();
                    pJson.setString("uuid", uuid.toString());
                    pJson.setInteger("value", data);
                    progArray.appendTag(pJson);
                }
            });
        } else {
            completeUsers.forEach((uuid) -> jArray.appendTag(new NBTTagString(uuid.toString())));

            userProgress.forEach((uuid, data) -> {
                NBTTagCompound pJson = new NBTTagCompound();
                pJson.setString("uuid", uuid.toString());
                pJson.setInteger("value", data);
                progArray.appendTag(pJson);
            });
        }

        nbt.setTag("completeUsers", jArray);
        nbt.setTag("userProgress", progArray);

        return nbt;
    }

    @Deprecated
    @Override
    public synchronized NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public synchronized NBTTagCompound writeToNBT(NBTTagCompound nbt, boolean reduce) {
        nbt.setTag("item", targetItem.writeToNBT(new NBTTagCompound(), reduce));
        nbt.setTag("block", targetBlock.writeToNBT(new NBTTagCompound(), reduce));
        NBTUtil.setBoolean(nbt, "ignoreNbt", ignoreNBT, DEFAULT_IGNORE_NBT, reduce);// Not "ignoreNBT"!!
        NBTUtil.setBoolean(nbt, "partialMatch", partialMatch, DEFAULT_PARTIAL_MATCH, reduce);
        NBTUtil.setBoolean(nbt, "allowMainHand", useMainHand, DEFAULT_USE_MAIN_HAND, reduce);
        NBTUtil.setBoolean(nbt, "allowOffHand", useOffHand, DEFAULT_USE_OFFHAND, reduce);
        NBTUtil.setInteger(nbt, "requiredUses", required, DEFAULT_REQUIRED, reduce);
        NBTUtil.setBoolean(nbt, "onInteract", onInteract, DEFAULT_ON_INTERACT, reduce);
        NBTUtil.setBoolean(nbt, "onHit", onHit, DEFAULT_ON_HIT, reduce);
        NBTUtil.setBoolean(nbt, "optional", optional, DEFAULT_OPTIONAL, reduce);
        return nbt;
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound nbt) {
        targetItem = new BigItemStack(nbt.getCompoundTag("item"));
        targetBlock.readFromNBT(nbt.getCompoundTag("block"));
        ignoreNBT = NBTUtil.getBoolean(nbt, "ignoreNbt", DEFAULT_IGNORE_NBT);
        partialMatch = NBTUtil.getBoolean(nbt, "partialMatch", DEFAULT_PARTIAL_MATCH);
        useMainHand = NBTUtil.getBoolean(nbt, "allowMainHand", DEFAULT_USE_MAIN_HAND);
        useOffHand = NBTUtil.getBoolean(nbt, "allowOffHand", DEFAULT_USE_OFFHAND);
        required = NBTUtil.getInteger(nbt, "requiredUses", DEFAULT_REQUIRED);
        onInteract = NBTUtil.getBoolean(nbt, "onInteract", DEFAULT_ON_INTERACT);
        onHit = NBTUtil.getBoolean(nbt, "onHit", DEFAULT_ON_HIT);
        optional = NBTUtil.getBoolean(nbt, "optional", DEFAULT_OPTIONAL);
    }

    private void setUserProgress(UUID uuid, Integer progress) {
        userProgress.put(uuid, progress);
    }

    public int getUsersProgress(UUID uuid) {
        Integer n = userProgress.get(uuid);
        return n == null ? 0 : n;
    }

    private List<Tuple<UUID, Integer>> getBulkProgress(@Nonnull List<UUID> uuids) {
        if (uuids.size() <= 0) return Collections.emptyList();
        List<Tuple<UUID, Integer>> list = new ArrayList<>();
        uuids.forEach((key) -> list.add(new Tuple<>(key, getUsersProgress(key))));
        return list;
    }

    @Override
    public boolean ignored(UUID uuid) {
        return optional;
    }

    @Override
    public List<String> getTextForSearch() {
        List<String> texts = new ArrayList<>();
        if (targetBlock.getItemStack() != null) {
            texts.add(targetBlock.getItemStack().getBaseStack().getDisplayName());
        }
        if (targetItem != null) {
            texts.add(targetItem.getBaseStack().getDisplayName());
        }
        return texts;
    }
}
