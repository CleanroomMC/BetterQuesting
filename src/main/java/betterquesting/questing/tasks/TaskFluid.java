package betterquesting.questing.tasks;

import betterquesting.NBTUtil;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.IFluidTask;
import betterquesting.api.questing.tasks.IItemTask;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.client.gui2.tasks.PanelTaskFluid;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.party.PartyInventory;
import betterquesting.questing.tasks.factory.FactoryTaskFluid;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TaskFluid implements ITaskInventory, IFluidTask, IItemTask {

    private static final boolean DEFAULT_IGNORE_NBT = false;
    private static final boolean DEFAULT_CONSUME = false;
    private static final boolean DEFAULT_GROUP_DETECT = false;
    private static final boolean DEFAULT_AUTO_CONSUME = false;
    private final Set<UUID> completeUsers = new ObjectOpenHashSet<>();
    public final NonNullList<FluidStack> requiredFluids = NonNullList.create();
    public final Map<UUID, int[]> userProgress = new Object2ObjectOpenHashMap<>();
    //public boolean partialMatch = true; // Not many ideal ways of implementing this with fluid handlers
    public boolean ignoreNbt = DEFAULT_IGNORE_NBT;
    public boolean consume = DEFAULT_CONSUME;
    public boolean groupDetect = DEFAULT_GROUP_DETECT;
    public boolean autoConsume = DEFAULT_AUTO_CONSUME;
    private boolean progressChanged = false;

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskFluid.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.fluid";
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
        int updatedReqs = 0;
        PartyInventory partyInv = pInfo.getPartyInventory();

        boolean taskConsumes = consume;
        // The current progress so far for the player
        int[] currentProgress = taskConsumes ? getUserProgress(pInfo.UUID) : null;
        int reqSize = requiredFluids.size();
        // The progress to fill based on current PartyInventory snapshot
        int[] invProgress = new int[reqSize];

        for (int reqI = 0; reqI < reqSize; reqI++) {
            final FluidStack rStack = requiredFluids.get(reqI);

            var fluidHandlerContext = partyInv.getFluidHandlersFor(rStack, taskConsumes, ignoreNbt);
            if (fluidHandlerContext == PartyInventory.FluidMatchContext.EMPTY) continue;

            int reqRemaining = rStack.amount;
            if (taskConsumes) {
                // Account for already consumed progress
                reqRemaining -= currentProgress[reqI];
            }

            int progressAmount = Math.min(reqRemaining, fluidHandlerContext.drainableAmount());
            progressAmount = consumeRequired(rStack, progressAmount, fluidHandlerContext);
            if (progressAmount > 0) {
                invProgress[reqI] += progressAmount;
                // Allows the fluid detection to split across multiple requirements.
                fluidHandlerContext.shrink(rStack, progressAmount);
                updatedReqs++;
            }
        }

        if (updatedReqs > 0) {
            // Reset counts used for split stack detection
            partyInv.resetFluidAmounts(taskConsumes);
            // Update cached progress and check completion
            int[] updatedProgress = updateBulkProgress(invProgress, pInfo);
            checkAndComplete(pInfo, quest, updatedProgress);
        }
    }

    /**
     * Consume the given amount from the player's inventory if necessary.
     *
     * @param rStack          the required fluid stack
     * @param amountToConsume the amount to try to consume
     * @param context         the context with compatible fluid handlers
     * @return how much was actually consumed, or amountToConsume if this is not a consume task
     */
    private int consumeRequired(FluidStack rStack, int amountToConsume, PartyInventory.FluidMatchContext context) {
        // Theoretically this could work in consume mode for parties but the priority order and manual submission code would need changing
        if (!consume) return amountToConsume;

        FluidStack drain = new FluidStack(rStack.getFluid(), amountToConsume, ignoreNbt ? null : rStack.tag);
        int remaining = drain.amount;
        int totalDrained = 0;
        for (var indexedContainer : context.indexedFluidContainers()) {
            final IFluidHandlerItem handler = indexedContainer.handler(false);
            if (handler == null) continue;
            int numContainers = indexedContainer.stackCount();

            final FluidStack toDrain = drain.copy();
            // The context did the simulation, so do the actual drain.
            final FluidStack drained = handler.drain(toDrain, true);
            if (drained == null || drained.amount <= 0) continue;

            int itemsNeeded = MathHelper.ceil((double) remaining / drained.amount);
            int itemsToConsume = Math.min(numContainers, itemsNeeded);
            // Amount of items that were drained fully
            int amountFullDrained = drained.amount * itemsToConsume;

            if (amountFullDrained > remaining) {
                // Handle partial drain for last needed container
                int partialAmount = remaining % drained.amount;
                IFluidHandlerItem partialHandler = indexedContainer.handler(false);
                if (partialHandler != null) {
                    // Build the handler's state after partial drain
                    FluidStack toDrainPartial = drain.copy();
                    toDrainPartial.amount = partialAmount;
                    FluidStack drainedPartial = partialHandler.drain(toDrainPartial, true);
                    if (drainedPartial != null && drainedPartial.amount > 0) {
                        // Update the single, partially drained container
                        indexedContainer.updateFluidContainer(partialHandler, 1, consume);
                        totalDrained += drainedPartial.amount;
                        remaining -= drainedPartial.amount;
                        // Partial drain succeeded and updated, so don't count it with the other full drains
                        amountFullDrained -= drained.amount;
                        itemsToConsume--;
                    }
                }
            }

            // Make sure to update the inventory and cached container (fully drained ones only)
            indexedContainer.updateFluidContainer(handler, itemsToConsume, consume);
            totalDrained += amountFullDrained;
            remaining -= amountFullDrained;
            if (remaining <= 0) {
                break;
            }
        }
        return totalDrained;
    }

    @Nonnull
    private int[] updateBulkProgress(int[] playerProgress, ParticipantInfo pInfo) {
        int[] updatedProgress = updateUserProgress(pInfo.UUID, playerProgress);
        if (!consume) {
            // Update all other party member's progress with playerProgress
            for (UUID uuid : pInfo.ALL_UUIDS) {
                if (uuid == pInfo.UUID) continue;
                updateUserProgress(uuid, playerProgress);
            }
        }
        return updatedProgress;
    }

    /**
     * Update the task progress for given user
     *
     * @param userUUID      the user's id
     * @param progressIn    the progress to merge, treated as immutable
     * @return the updated task progress
     */
    private int[] updateUserProgress(UUID userUUID, int[] progressIn) {
        return userProgress.merge(userUUID, progressIn, (existingProgress, progressToMerge) -> {
            // Somehow the existing progress doesn't reflect current requirements, only use new progress.
            if (existingProgress.length != requiredFluids.size()) {
                progressChanged = true;
                return progressToMerge;
            }
            // Group-detect requires all requirements to be met within this single detection.
            if (groupDetect) {
                progressChanged = true;
                return progressToMerge;
            }

            for (int i = 0; i < existingProgress.length; i++) {
                // Skip if already fulfilled
                if (existingProgress[i] >= requiredFluids.get(i).amount) continue;

                if (consume) {
                    // Make sure we keep the progress that has already consumed stuff before
                    existingProgress[i] += progressToMerge[i];
                    progressChanged = true;
                }
                else if (existingProgress[i] != progressToMerge[i]) {
                    // Otherwise the progressIn overwrites the current progress
                    existingProgress[i] = progressToMerge[i];
                    progressChanged = true;
                }
            }
            return existingProgress;
        });
    }

    @Nonnull
    public int[] getUserProgress(UUID uuidIn) {
        return userProgress.compute(uuidIn, (uuid, progress) ->
                progress == null || progress.length != requiredFluids.size() ? new int[requiredFluids.size()] : progress);
    }

    /**
     * Check if the detected progress allows completion of the task's quest.
     * @param pInfo the participant's info
     * @param quest the quest to possibly complete
     * @param progress the updated progress
     */
    private void checkAndComplete(ParticipantInfo pInfo, DBEntry<IQuest> quest, int[] progress) {
        if (progressChanged) {
            progressChanged = false;
            var questID = Collections.singletonList(quest.getID());
            if (consume) {
                pInfo.markDirty(questID);
            }
            else {
                pInfo.markDirtyParty(questID);
            }
        }
        // Check all requirements are complete
        for (int i = 0; i < progress.length; i++) {
            if (progress[i] < requiredFluids.get(i).amount) {
                return;
            }
        }

        if (consume) {
            setComplete(pInfo.UUID);
        }
        else {
            pInfo.ALL_UUIDS.forEach(this::setComplete);
        }
    }

    /**
     * Check if the given progress for a single player would complete the quest.
     * @param uuid the UUID of the player
     * @param progress the updated progress
     */
    private void checkAndComplete(UUID uuid, int[] progress) {
        // Check all requirements are complete
        for (int i = 0; i < progress.length; i++) {
            if (progress[i] < requiredFluids.get(i).amount) {
                return;
            }
        }

        setComplete(uuid);
    }

    /* NBT handling */

    @Deprecated
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt, boolean reduce) {
        //json.setBoolean("partialMatch", partialMatch);
        NBTUtil.setBoolean(nbt, "ignoreNBT", ignoreNbt, DEFAULT_IGNORE_NBT, reduce);
        NBTUtil.setBoolean(nbt, "consume", consume, DEFAULT_CONSUME, reduce);
        NBTUtil.setBoolean(nbt, "groupDetect", groupDetect, DEFAULT_GROUP_DETECT, reduce);
        NBTUtil.setBoolean(nbt, "autoConsume", autoConsume, DEFAULT_AUTO_CONSUME, reduce);

        NBTTagList itemArray = new NBTTagList();
        for (FluidStack stack : this.requiredFluids) {
            itemArray.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        nbt.setTag("requiredFluids", itemArray);

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        //partialMatch = json.getBoolean("partialMatch");
        ignoreNbt = NBTUtil.getBoolean(nbt, "ignoreNBT", DEFAULT_IGNORE_NBT);
        consume = NBTUtil.getBoolean(nbt, "consume", DEFAULT_CONSUME);
        groupDetect = NBTUtil.getBoolean(nbt, "groupDetect", DEFAULT_GROUP_DETECT);
        autoConsume = NBTUtil.getBoolean(nbt, "autoConsume", DEFAULT_AUTO_CONSUME);

        requiredFluids.clear();
        NBTTagList fList = nbt.getTagList("requiredFluids", 10);
        for (int i = 0; i < fList.tagCount(); i++) {
            requiredFluids.add(JsonHelper.JsonToFluidStack(fList.getCompoundTagAt(i)));
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

                int[] data = new int[requiredFluids.size()];
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
                if (completeUsers.contains(uuid)) {
                    jArray.appendTag(new NBTTagString(uuid.toString()));
                }

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
            // Ensure consistent order when writing
            TreeSet<UUID> sortedCompleteUsers = new TreeSet<>(completeUsers);
            TreeMap<UUID, int[]> sortedProgress = new TreeMap<>(userProgress);

            sortedCompleteUsers.forEach((uuid) -> jArray.appendTag(new NBTTagString(uuid.toString())));

            sortedProgress.forEach((uuid, data) -> {
                NBTTagCompound pJson = new NBTTagCompound();
                pJson.setString("uuid", uuid.toString());
                NBTTagList pArray = new NBTTagList(); // TODO: Why the heck isn't this just an int array?!
                for (int i : data) pArray.appendTag(new NBTTagInt(i));
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
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskFluid(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen screen, DBEntry<IQuest> quest) {
        return null;
    }

    @Override
    public boolean canAcceptFluid(UUID owner, DBEntry<IQuest> quest, FluidStack fluid) {
        if (owner == null || fluid == null || fluid.getFluid() == null || !consume || isComplete(owner) || requiredFluids.size() <= 0) {
            return false;
        }

        int[] progress = getUserProgress(owner);

        for (int j = 0; j < requiredFluids.size(); j++) {
            FluidStack rStack = requiredFluids.get(j).copy();
            if (ignoreNbt) rStack.tag = null;
            if (progress[j] < rStack.amount && rStack.equals(fluid)) return true;
        }

        return false;
    }

    @Override
    public boolean canAcceptItem(UUID owner, DBEntry<IQuest> quest, ItemStack item) {
        if (owner == null || item == null || item.isEmpty() || !consume || isComplete(owner) || requiredFluids.size() <= 0) {
            return false;
        }

        IFluidHandlerItem handler = FluidUtil.getFluidHandler(item);

        if (handler == null) return false;

        for (IFluidTankProperties tank : handler.getTankProperties()) {
            if (!tank.canDrain()) continue;

            for (FluidStack rStack : requiredFluids) {
                if (rStack.equals(tank.getContents())) return true;
            }
        }

        return false;
    }

    @Override
    public FluidStack submitFluid(UUID owner, DBEntry<IQuest> quest, FluidStack fluid) {
        return submitFluidInternal(owner, quest, fluid, true);
    }

    private FluidStack submitFluidInternal(UUID owner, DBEntry<IQuest> quest, FluidStack fluidIn, boolean doFill) {
        if (owner == null || fluidIn == null || fluidIn.amount <= 0 || !consume || isComplete(owner) || requiredFluids.size() <= 0) {
            return fluidIn;
        }

        FluidStack fluid = fluidIn.copy();

        // Direct reference to value in userProgress
        int[] progress = getUserProgress(owner);
        boolean updated = false;

        for (int j = 0; j < requiredFluids.size(); j++) {
            FluidStack rStack = requiredFluids.get(j);

            if (progress[j] >= rStack.amount) continue;

            int remaining = rStack.amount - progress[j];

            if (rStack.isFluidEqual(fluid)) {
                int removed = Math.min(fluid.amount, remaining);
                progress[j] += removed;
                fluid.amount -= removed;
                updated = true;

                if (fluid.amount <= 0) {
                    fluid = null;
                    break;
                }
            }
        }

        if (updated && doFill) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            EntityPlayerMP player = server == null ? null : server.getPlayerList().getPlayerByUUID(owner);

            if (player != null) {
                checkAndComplete(new ParticipantInfo(player), quest, progress);
            } else {
                checkAndComplete(owner, progress);
            }
        }

        return fluid;
    }

    @Override
    public ItemStack submitItem(UUID owner, DBEntry<IQuest> quest, ItemStack input) {
        if (owner == null || input.isEmpty() || !consume || isComplete(owner)) return input;

        ItemStack item = input.splitStack(1); // Prevents issues with stack filling/draining

        IFluidHandlerItem handler = FluidUtil.getFluidHandler(item);
        if (handler == null) return item;

        boolean hasDrained = false;

        for (IFluidTankProperties tank : handler.getTankProperties()) {
            if (!tank.canDrain() || tank.getContents() == null || !tank.canDrainFluidType(tank.getContents())) continue;

            // Figure out how much of this fluid is left to submit to the task
            FluidStack remaining = submitFluidInternal(owner, quest, tank.getContents().copy(), false);
            FluidStack drain = tank.getContents().copy();
            drain.amount -= remaining == null ? 0 : remaining.amount;

            if (drain.amount <= 0) continue;

            // Attempt drain of remaining amount and submit to task progress
            submitFluidInternal(owner, quest, handler.drain(drain, true), true);
            hasDrained = true;
        }

        return hasDrained ? handler.getContainer() : item;
    }

    @Override
    public List<String> getTextForSearch() {
        List<String> texts = new ArrayList<>();
        for (FluidStack fluid : requiredFluids) {
            texts.add(fluid.getLocalizedName());
            texts.add(fluid.getUnlocalizedName());
        }
        return texts;
    }
}
