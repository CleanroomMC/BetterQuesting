package betterquesting.questing.party;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A snapshot of a party's inventories, accumulating counts of all item stacks.
 */
public class PartyInventory {
    /** The main player's collected inventory, keys are item ids. */
    private final Int2ObjectMap<List<IndexedItemStack>> playerStacks;
    /** The collapsed inventory of all party members, keys are item ids. */
    private final Int2ObjectMap<List<IndexedItemStack>> partyStacks;
    /** The main player's collected fluid containers. */
    private final List<IndexedFluidHandler> playerFluidContainers;
    /** The collected fluid containers of all party members. */
    private final List<IndexedFluidHandler> partyFluidContainers;

    public PartyInventory(EntityPlayer mainPlayer, List<EntityPlayer> party) {
        // Player inventories should usually all be the same size
        int maxSize = mainPlayer.inventory.mainInventory.size() * party.size();
        this.playerStacks = new Int2ObjectOpenHashMap<>(maxSize);
        this.playerFluidContainers = new ArrayList<>();
        if (party.size() <= 1) {
            this.partyStacks = this.playerStacks;
            this.partyFluidContainers = this.playerFluidContainers;
        }
        else {
            this.partyStacks = new Int2ObjectOpenHashMap<>(maxSize);
            this.partyFluidContainers = new ArrayList<>();
        }

        for (EntityPlayer player : party) {
            NonNullList<ItemStack> mainInventory = player.inventory.mainInventory;
            for (int i = 0; i < mainInventory.size(); i++) {
                ItemStack stack = mainInventory.get(i);
                if (stack.isEmpty()) continue;

                var indexedStack = new IndexedItemStack(stack, i, player.inventory);
                var fluidHandler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
                IndexedFluidHandler indexedFluidHandler = null;
                if (fluidHandler != null) {
                    indexedFluidHandler = new IndexedFluidHandler(fluidHandler, i, player.inventory);
                }

                int itemHash = getHashKey(stack);
                if (player == mainPlayer) {
                    var subStacks = playerStacks.get(itemHash);
                    if (subStacks == null) {
                        subStacks = new ArrayList<>();
                        playerStacks.put(itemHash, subStacks);
                    }
                    subStacks.add(indexedStack);
                    if (indexedFluidHandler != null) {
                        playerFluidContainers.add(indexedFluidHandler);
                    }
                }
                // Don't put duplicates in solo parties
                if (playerStacks != partyStacks) {
                    var subStacks = partyStacks.get(itemHash);
                    if (subStacks == null) {
                        subStacks = new ArrayList<>();
                        partyStacks.put(itemHash, subStacks);
                    }
                    subStacks.add(indexedStack);
                    if (indexedFluidHandler != null) {
                        partyFluidContainers.add(indexedFluidHandler);
                    }
                }
            }
        }
    }

    public static int getHashKey(ItemStack stack) {
        return Objects.requireNonNull(stack.getItem().getRegistryName()).hashCode();
    }

    /**
     * Get the combined count of an item stack in the party's inventory.
     * @param req the requirement stack
     * @param taskConsumes if true, will only count from the main player's inventory
     * @param ignoreNBT if matching should ignore NBT
     * @param partialMatch if partially matching NBT is allowed
     * @return the total amount of the stack the party has
     */
    public ItemMatchContext getItemCountFor(BigItemStack req, boolean taskConsumes, boolean ignoreNBT, boolean partialMatch) {
        var gatheredStacks = taskConsumes ? playerStacks : partyStacks;

        // The stacks matched by Item
        var subStacks = gatheredStacks.get(req.getHashKey());
        if (subStacks == null || subStacks.isEmpty()) {
            return ItemMatchContext.EMPTY;
        }

        // Collect matched stacks and total count
        var matchedStacks = new ArrayList<IndexedItemStack>();
        int count = 0;
        for (var indexedStack : subStacks) {
            ItemStack stack = indexedStack.stack;
            if (ItemComparison.BigStackMatch(req, stack, ignoreNBT, partialMatch)) {
                matchedStacks.add(indexedStack);
                count += indexedStack.count;
            }
        }
        return matchedStacks.isEmpty() ? ItemMatchContext.EMPTY : new ItemMatchContext(count, matchedStacks);
    }

    /**
     * Get all fluid handlers from the party's inventory that can handle the given fluid stack.
     * @param req the required fluid stack
     * @param taskConsumes if true, will only search the main player's inventory
     * @param ignoreNBT if matching should ignore NBT
     * @return the context wrapping the max fluid amount available and applicable fluid handlers
     */
    public FluidMatchContext getFluidHandlersFor(FluidStack req, boolean taskConsumes, boolean ignoreNBT) {
        var gatheredHandlers = taskConsumes ? playerFluidContainers : partyFluidContainers;
        if (gatheredHandlers.isEmpty()) {
            return FluidMatchContext.EMPTY;
        }

        int amount = 0;
        var handlers = new ArrayList<IndexedFluidHandler>();
        for (var indexedHandler : gatheredHandlers) {
            int numContainers = indexedHandler.handler.getContainer().getCount();
            // Even though we're simulating, make a defensive copy
            FluidStack toDrain = req.copy();
            if (ignoreNBT) {
                toDrain.tag = null;
            }
            toDrain.amount /= numContainers; // Must be a multiple of the stack size to drain evenly
            if (toDrain.amount <= 0) continue;

            // Simulate the drain
            FluidStack drained = indexedHandler.handler().drain(toDrain, false);
            if (drained == null || drained.amount <= 0) continue;

            int drainedAmount = drained.amount * numContainers; // Multiply back the number of containers drained
            amount += Math.min(indexedHandler.getAvailableAmount(drained), drainedAmount);
            handlers.add(indexedHandler);
        }
        return handlers.isEmpty() ? FluidMatchContext.EMPTY : new FluidMatchContext(amount, handlers);
    }

    /**
     * Update any cached item stacks with a corresponding fluid handler. Must be called after any non-simulated drains
     * of a fluid handler from the context.
     *
     * @param context the fluid match context
     * @param taskConsumes if true, will only update the main player's cached stacks
     * @see net.minecraftforge.fluids.FluidUtil#getFluidHandler(ItemStack) the contract this fulfills
     */
    public void updateFluidContainers(FluidMatchContext context, boolean taskConsumes) {
        // Consume tasks for parties aren't currently supported.
        if (!taskConsumes) return;

        int toUpdate = 0;
        for (List<IndexedItemStack> possibleStacks : playerStacks.values()) {
            for (IndexedItemStack iStack : possibleStacks) {
                for (IndexedFluidHandler iContainerStack : context.indexedFluidHandlers()) {
                    if (iContainerStack.slot == iStack.slot && iContainerStack.sourceInv == iStack.sourceInv) {
                        ItemStack container = iContainerStack.handler.getContainer();
                        if (container == iStack.stack) break;

                        // Update the player's inventory
                        iContainerStack.sourceInv.setInventorySlotContents(iStack.slot, container);
                        // As well as the cached stack
                        iStack.updateCachedStack(container);
                        toUpdate++;
                        break;
                    }
                }
                if (toUpdate >= context.indexedFluidHandlers.size()) return;
            }
        }
    }

    /**
     * Resets the cached counts for the party's stacks.
     * Call this after any call to {@link ItemMatchContext#shrink(int)}.
     * @param taskConsumes if true, will only reset the cached stacks for the main player
     */
    public void resetItemCounts(boolean taskConsumes) {
        var stacksToReset = taskConsumes ? playerStacks : partyStacks;
        for (var iStacks : stacksToReset.values()) {
            for (var iStack : iStacks) {
                iStack.resetCount();
            }
        }
    }

    /**
     * The fluid variant of {@link #resetItemCounts(boolean)}.
     * Call this after any call to {@link FluidMatchContext#shrink(FluidStack, int)}.
     * @param taskConsumes if true, will only reset the cached fluid amounts for the main player
     */
    public void resetFluidAmounts(boolean taskConsumes) {
        var handlersToReset = taskConsumes ? playerFluidContainers : partyFluidContainers;
        for (var iHandler : handlersToReset) {
            iHandler.resetAmounts();
        }
    }

    public static final class IndexedItemStack {
        /** The cached stack */
        private ItemStack stack;
        /**
         * The cached stack count.
         * This should always be resynced by the start of any item task detection (the caller is responsible to reset).
         */
        private int count;
        /** The slot index of the stack */
        private final int slot;
        /** The player inventory this stack belongs to */
        private final InventoryPlayer sourceInv;

        private IndexedItemStack(ItemStack stack, int slot, InventoryPlayer sourceInv) {
            this.stack = stack;
            this.slot = slot;
            this.sourceInv = sourceInv;
            this.count = stack.getCount();
        }

        public int slot() {
            return slot;
        }

        /** @see #updateFluidContainers(FluidMatchContext, boolean) */
        private void updateCachedStack(ItemStack stack) {
            this.stack = stack;
            resetCount();
        }

        private void shrink(int amount) {
            count -= amount;
        }

        /** Resync the cached count to the actual stack's count. Call this at the end of task detection if needed. */
        private void resetCount() {
            count = stack.getCount();
        }

        @Override
        public String toString() {
            return "IndexedItemStack[" +
                    "stack=" + stack + ", " +
                    "count=" + count + ", " +
                    "slot=" + slot + ", " +
                    "sourceInv=" + sourceInv + ']';
        }
    }

    @Desugar
    public record ItemMatchContext(int availableAmount, List<IndexedItemStack> indexedItemStacks) {
        public static final ItemMatchContext EMPTY = new ItemMatchContext(0, Collections.emptyList());

        /**
         * Shrink a total amount from the indexed stacks.
         * Call this for any detection of this item, even if it wasn't actually consumed.
         * @param amount the amount to shrink by
         */
        public void shrink(int amount) {
            int remaining = amount;
            for (var iStack : indexedItemStacks) {
                int amountShrunk = Math.min(iStack.count, remaining);
                iStack.shrink(amountShrunk);
                remaining -= amountShrunk;
                if (remaining <= 0) {
                    return;
                }
            }
        }
    }

    /**
     * A fluid handler associated with a fluid container belonging to a certain player's inventory
     */
    public static final class IndexedFluidHandler {
        /** The fluid handler */
        @Nonnull
        private final IFluidHandlerItem handler;
        /** The slot index of the handler's container */
        private final int slot;
        /** The player inventory this stack belongs to */
        private final InventoryPlayer sourceInv;
        /**
         * A list of fluid stacks and their amounts that were matched for this fluid handler.
         * This should always be empty at the start of any fluid task detection (the caller is responsible to reset).
         */
        @Nonnull
        private final List<FluidStack> cachedFluidAmounts;

        /**
         * @param handler   the fluid container's handler
         * @param slot      the associated container's slot in the inventory
         * @param sourceInv the player's inventory this fluid handler belongs to
         */
        public IndexedFluidHandler(@Nonnull IFluidHandlerItem handler, int slot, InventoryPlayer sourceInv) {
            this.handler = handler;
            this.slot = slot;
            this.sourceInv = sourceInv;
            cachedFluidAmounts = new ArrayList<>();
        }

        @Nonnull
        public IFluidHandlerItem handler() {
            return handler;
        }

        /**
         * Get the extractable amount of the given fluid stack from this fluid handler.
         */
        private int getAvailableAmount(FluidStack fluidIn) {
            FluidStack cached = null;
            // Look for the cached amount
            for (var fluid : cachedFluidAmounts) {
                if (fluid.isFluidEqual(fluidIn)) {
                    cached = fluid;
                    break;
                }
            }
            // Cache if not found
            if (cached == null) {
                cached = fluidIn;
                cachedFluidAmounts.add(cached);
            }

            return cached.amount;
        }

        private void shrink(FluidStack fluidToMatch, int amount) {
            for (var fluid : cachedFluidAmounts) {
                if (fluid.isFluidEqual(fluidToMatch)) fluid.amount -= amount;
            }
        }

        /** Reset the cached fluid amounts. Call this at the end of task detection if needed. */
        private void resetAmounts() {
            cachedFluidAmounts.clear();
        }

        @Override
        public String toString() {
            return "IndexedFluidHandler[" +
                    "handler=" + handler + ", " +
                    "slot=" + slot + ", " +
                    "sourceInv=" + sourceInv + ", " +
                    "cachedFluidAmounts=" + cachedFluidAmounts + ']';
        }
    }

    /**
     * Fluid stack context with available fluid amount and the matched slots of the applicable fluid handlers
     * @param drainableAmount total amount of the fluid that can be drained
     * @param indexedFluidHandlers applicable fluid handlers
     */
    @Desugar
    public record FluidMatchContext(int drainableAmount, List<IndexedFluidHandler> indexedFluidHandlers) {
        public static final FluidMatchContext EMPTY = new FluidMatchContext(0, Collections.emptyList());

        /**
         * Shrink a total amount of a given fluid from the indexed handlers.
         * Call this for any detection of this fluid, even if it wasn't actually consumed.
         * @param reqFluid the required fluid stack, only used for its fluid and tag
         * @param amount the amount to shrink by
         */
        public void shrink(FluidStack reqFluid, int amount) {
            int remaining = amount;
            FluidStack fluid = new FluidStack(reqFluid.getFluid(), amount, reqFluid.tag);
            for (var iHandler : indexedFluidHandlers) {
                int amountShrunk = Math.min(iHandler.getAvailableAmount(fluid), remaining);
                iHandler.shrink(fluid, amountShrunk);
                remaining -= amountShrunk;
                if (remaining <= 0) {
                    return;
                }
            }
        }
    }
}
