package betterquesting.questing.tasks;

import betterquesting.NBTUtil;
import betterquesting.api.enums.EnumLogic;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.IItemTask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.client.gui2.editors.tasks.GuiEditTaskRetrieval;
import betterquesting.client.gui2.tasks.PanelTaskRetrieval;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.party.PartyInventory;
import betterquesting.questing.tasks.factory.FactoryTaskRetrieval;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.IntStream;

public class TaskRetrieval implements ITaskInventory, IItemTask {

    private static final boolean DEFAULT_PARTIAL_MATCH = true;
    private static final boolean DEFAULT_IGNORE_NBT = false;
    private static final boolean DEFAULT_CONSUME = false;
    private static final boolean DEFAULT_GROUP_DETECT = false;
    private static final boolean DEFAULT_AUTO_CONSUME = false;
    private static final EnumLogic DEFAULT_ENTRY_LOGIC = EnumLogic.AND;
    private final Set<UUID> completeUsers = new TreeSet<>();
    public final NonNullList<BigItemStack> requiredItems = NonNullList.create();
    private final TreeMap<UUID, int[]> userProgress = new TreeMap<>();
    public boolean partialMatch = DEFAULT_PARTIAL_MATCH;
    public boolean ignoreNBT = DEFAULT_IGNORE_NBT;
    public boolean consume = DEFAULT_CONSUME;
    public boolean groupDetect = DEFAULT_GROUP_DETECT;
    public boolean autoConsume = DEFAULT_AUTO_CONSUME;
    public EnumLogic entryLogic = DEFAULT_ENTRY_LOGIC;

    @Override
    public String getUnlocalisedName() {
        return BetterQuesting.MODID_STD + ".task.retrieval";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskRetrieval.INSTANCE.getRegistryName();
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
    public void onInventoryChange(@Nonnull DBEntry<IQuest> quest, @Nonnull ParticipantInfo pInfo) {
        if (!consume || autoConsume) {
            detect(pInfo, quest);
        }
    }

    @Override
    public void detect(ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        if (isComplete(pInfo.UUID)) {
            return;
        }
        boolean updated = false;

        // TODO: rewrite progress retrieval/updating
        // List of (player uuid, [progress per required item])
        final List<Tuple<UUID, int[]>> progress = getBulkProgress(consume ? Collections.singletonList(pInfo.UUID) : pInfo.ALL_UUIDS);
        if (!consume) {
            if (groupDetect) // Reset all detect progress
            {
                progress.forEach((value) -> Arrays.fill(value.getSecond(), 0));
            } else {
                for (int i = 0; i < requiredItems.size(); i++) {
                    final int r = requiredItems.get(i).stackSize;
                    for (Tuple<UUID, int[]> value : progress) {
                        int n = value.getSecond()[i];
                        if (n != 0 && n < r) {
                            value.getSecond()[i] = 0;
                            updated = true;
                        }
                    }
                }
            }
        }

        IItemHandler playerInv = consume ? new PlayerInvWrapper(pInfo.PLAYER.inventory) : EmptyHandler.INSTANCE;
        PartyInventory partyInv = pInfo.getPartyInventory();

        for (int reqI = 0, reqSize = requiredItems.size(); reqI < reqSize; reqI++) {
            BigItemStack rStack = requiredItems.get(reqI);

            int partyStackCount = partyInv.getItemCountFor(rStack, consume, ignoreNBT, partialMatch);
            if (partyStackCount <= 0) continue;

            // Theoretically this could work in consume mode for parties but the priority order and manual submission code would need changing
            for (Tuple<UUID, int[]> value : progress) {
                // Skip if already fulfilled
                if (value.getSecond()[reqI] >= rStack.stackSize) continue;

                int reqRemaining = rStack.stackSize - value.getSecond()[reqI];
                int progressAmount = Math.min(reqRemaining, partyStackCount);
                if (consume) {
                    value.getSecond()[reqI] += consumeRequired(rStack, playerInv, pInfo.PLAYER, progressAmount);
                }
                else {
                    value.getSecond()[reqI] += progressAmount;
                }

                updated = true;
            }
        }

        if (updated) {
            setBulkProgress(progress);
        }
        // Reuse progress
        checkAndComplete(pInfo, quest, updated, progress);
    }

    private int consumeRequired(BigItemStack req, IItemHandler inv, EntityPlayer player, int amountToConsume) {
        int remaining = amountToConsume;
        int totalConsumed = 0;
        // Just scan the whole inventory, this shouldn't be called often
        for (int slot = 0, numSlots = inv.getSlots(); slot < numSlots; slot++) {
            ItemStack simulated = inv.extractItem(slot, remaining, true);
            // Must match and be extractable
            if (simulated.isEmpty() || !ItemComparison.BigStackMatch(req, simulated, ignoreNBT, partialMatch)) {
                continue;
            }

            int toExtract = Math.min(simulated.getCount(), remaining);
            ItemStack extracted = inv.extractItem(slot, toExtract, false);
            if (!extracted.isEmpty()) {
                int amountExtracted = extracted.getCount();
                totalConsumed += amountExtracted;
                remaining -= amountExtracted;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        if (totalConsumed > 0) {
            player.openContainer.detectAndSendChanges();
        }
        return totalConsumed;
    }

    private void checkAndComplete(ParticipantInfo pInfo, DBEntry<IQuest> quest, boolean resync) {
        checkAndComplete(pInfo, quest, resync, getBulkProgress(consume ? Collections.singletonList(pInfo.UUID) : pInfo.ALL_UUIDS));
    }

    private void checkAndComplete(ParticipantInfo pInfo, DBEntry<IQuest> quest, boolean resync, List<Tuple<UUID, int[]>> progress) {
        boolean updated = resync;

        for (Tuple<UUID, int[]> value : progress) {
            int count = 0;
            for (int j = 0; j < requiredItems.size(); j++) {
                if (value.getSecond()[j] >= requiredItems.get(j).stackSize)
                    count++;
            }
            if (!entryLogic.getResult(count, requiredItems.size()))
                continue;

            updated = true;

            if (consume) {
                setComplete(value.getFirst());
            } else {
                progress.forEach((pair) -> setComplete(pair.getFirst()));
                break;
            }
        }

        if (updated) {
            if (consume) {
                pInfo.markDirty(Collections.singletonList(quest.getID()));
            } else {
                pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
            }
        }
    }

    @Deprecated
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt, boolean reduce) {
        NBTUtil.setBoolean(nbt, "partialMatch", partialMatch, DEFAULT_PARTIAL_MATCH, reduce);
        NBTUtil.setBoolean(nbt, "ignoreNBT", ignoreNBT, DEFAULT_IGNORE_NBT, reduce);
        NBTUtil.setBoolean(nbt, "consume", consume, DEFAULT_CONSUME, reduce);
        NBTUtil.setBoolean(nbt, "groupDetect", groupDetect, DEFAULT_GROUP_DETECT, reduce);
        NBTUtil.setBoolean(nbt, "autoConsume", autoConsume, DEFAULT_AUTO_CONSUME, reduce);
        NBTUtil.setString(nbt, "entryLogic", entryLogic.name(), DEFAULT_ENTRY_LOGIC.name(), reduce);

        NBTTagList itemArray = new NBTTagList();
        for (BigItemStack stack : this.requiredItems) {
            itemArray.appendTag(JsonHelper.ItemStackToJson(stack, new NBTTagCompound(), reduce));
        }
        nbt.setTag("requiredItems", itemArray);

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        partialMatch = NBTUtil.getBoolean(nbt, "partialMatch", DEFAULT_PARTIAL_MATCH);
        ignoreNBT = NBTUtil.getBoolean(nbt, "ignoreNBT", DEFAULT_IGNORE_NBT);
        consume = NBTUtil.getBoolean(nbt, "consume", DEFAULT_CONSUME);
        groupDetect = NBTUtil.getBoolean(nbt, "groupDetect", DEFAULT_GROUP_DETECT);
        autoConsume = NBTUtil.getBoolean(nbt, "autoConsume", DEFAULT_AUTO_CONSUME);
        entryLogic = NBTUtil.getEnum(nbt, "entryLogic", EnumLogic.class, true, DEFAULT_ENTRY_LOGIC);

        requiredItems.clear();
        NBTTagList iList = nbt.getTagList("requiredItems", 10);
        for (int i = 0; i < iList.tagCount(); i++) {
            requiredItems.add(JsonHelper.JsonToItemStack(iList.getCompoundTagAt(i)));
        }
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

                int[] data = new int[requiredItems.size()];
                NBTTagList dNbt = pTag.getTagList("data", 3);
                for (int i = 0; i < data.length && i < dNbt.tagCount(); i++) // TODO: Change this to an int array. This is dumb...
                {
                    data[i] = dNbt.getIntAt(i);
                }

                userProgress.put(uuid, data);
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
                if (completeUsers.contains(uuid))
                    jArray.appendTag(new NBTTagString(uuid.toString()));

                int[] data = userProgress.get(uuid);
                if (data != null) {
                    NBTTagCompound pJson = new NBTTagCompound();
                    pJson.setString("uuid", uuid.toString());
                    NBTTagList pArray = new NBTTagList(); // TODO: Why the heck isn't this just an int array?!
                    for (int i : data) {
                        pArray.appendTag(new NBTTagInt(i));
                    }
                    pJson.setTag("data", pArray);
                    progArray.appendTag(pJson);
                }
            });
        } else {
            completeUsers.forEach((uuid) -> jArray.appendTag(new NBTTagString(uuid.toString())));

            userProgress.forEach((uuid, data) -> {
                NBTTagCompound pJson = new NBTTagCompound();
                pJson.setString("uuid", uuid.toString());
                NBTTagList pArray = new NBTTagList(); // TODO: Why the heck isn't this just an int array?!
                for (int i : data) {
                    pArray.appendTag(new NBTTagInt(i));
                }
                pJson.setTag("data", pArray);
                progArray.appendTag(pJson);
            });
        }

        nbt.setTag("completeUsers", jArray);
        nbt.setTag("userProgress", progArray);

        return nbt;
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
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskRetrieval(rect, this);
    }

    @Override
    public boolean canAcceptItem(UUID owner, DBEntry<IQuest> quest, ItemStack stack) {
        if (owner == null || stack == null || stack.isEmpty() || !consume || isComplete(owner) || requiredItems.size() <= 0) {
            return false;
        }

        int[] progress = getUsersProgress(owner);

        for (int j = 0; j < requiredItems.size(); j++) {
            BigItemStack rStack = requiredItems.get(j);

            if (progress[j] >= rStack.stackSize)
                continue;

            if (ItemComparison.StackMatch(rStack.getBaseStack(), stack, !ignoreNBT, partialMatch) || ItemComparison.OreDictionaryMatch(rStack.getOreIngredient(), rStack.GetTagCompound(), stack, !ignoreNBT, partialMatch)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public ItemStack submitItem(UUID owner, DBEntry<IQuest> quest, ItemStack input) {
        if (owner == null || input.isEmpty() || !consume || isComplete(owner))
            return input;

        ItemStack stack = input.copy();

        int[] progress = getUsersProgress(owner);
        boolean updated = false;

        for (int j = 0; j < requiredItems.size(); j++) {
            if (stack.isEmpty())
                break;

            BigItemStack rStack = requiredItems.get(j);

            if (progress[j] >= rStack.stackSize)
                continue;

            int remaining = rStack.stackSize - progress[j];

            if (ItemComparison.StackMatch(rStack.getBaseStack(), stack, !ignoreNBT, partialMatch) || ItemComparison.OreDictionaryMatch(rStack.getOreIngredient(), rStack.GetTagCompound(), stack, !ignoreNBT, partialMatch)) {
                int removed = Math.min(stack.getCount(), remaining);
                stack.shrink(removed);
                progress[j] += removed;
                updated = true;
                if (stack.isEmpty())
                    break;
            }
        }

        if (updated) {
            setUserProgress(owner, progress);

            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            EntityPlayerMP player = server == null ? null : server.getPlayerList().getPlayerByUUID(owner);

            if (player != null) {
                checkAndComplete(new ParticipantInfo(player), quest, true);
            } else {
                // It's implied to be a consume task so no need to lookup the party
                int count = (int) IntStream.range(0, requiredItems.size()).filter(j -> progress[j] >= requiredItems.get(j).stackSize).count();

                if (entryLogic.getResult(count, requiredItems.size()))
                    setComplete(owner);
            }
        }

        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest) {
        return new GuiEditTaskRetrieval(parent, quest, this);
    }

    private void setUserProgress(UUID uuid, int[] progress) {
        userProgress.put(uuid, progress);
    }

    public int[] getUsersProgress(UUID uuid) {
        int[] progress = userProgress.get(uuid);
        return progress == null || progress.length != requiredItems.size() ? new int[requiredItems.size()] : progress;
    }

    private List<Tuple<UUID, int[]>> getBulkProgress(@Nonnull List<UUID> uuids) {
        if (uuids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Tuple<UUID, int[]>> list = new ArrayList<>(uuids.size());
        for (var uuid : uuids) {
            list.add(new Tuple<>(uuid, getUsersProgress(uuid)));
        }
        return list;
    }

    private void setBulkProgress(@Nonnull List<Tuple<UUID, int[]>> list) {
        list.forEach((entry) -> setUserProgress(entry.getFirst(), entry.getSecond()));
    }

    @Override
    public List<String> getTextForSearch() {
        List<String> texts = new ArrayList<>();
        for (BigItemStack bigStack : requiredItems) {
            ItemStack stack = bigStack.getBaseStack();
            texts.add(stack.getDisplayName());
            if (bigStack.hasOreDict()) {
                texts.add(bigStack.getOreDict());
            }
        }
        return texts;
    }
}
