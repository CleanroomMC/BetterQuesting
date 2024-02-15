package betterquesting.importers.ftbq.converters.tasks;

import betterquesting.api.placeholders.PlaceholderConverter;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.importers.ftbq.FTBQQuestImporter;
import betterquesting.questing.tasks.TaskFluid;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public class FtbqTaskFluid {
    public ITask[] convertTask(NBTTagCompound nbt) {
        String fName = nbt.getString("fluid");
        Fluid fluid = FluidRegistry.getFluid(fName);
        long amount;
        if (nbt.hasKey("amount"))
            amount = nbt.getLong("amount"); // Sigh... longs again. No matter, we'll just split them if they're too big
        else
            amount = Fluid.BUCKET_VOLUME;
        NBTTagCompound tag = !nbt.hasKey("tag", 10) ? null : nbt.getCompoundTag("tag"); // FTBQ doesn't support tags yet but we'll try supporting it in advance
        FluidStack stack = PlaceholderConverter.convertFluid(fluid, fName, 1, tag);

        TaskFluid task = new TaskFluid();

        long rem = amount;
        while (rem > 0) {
            int split = (int) (rem % Integer.MAX_VALUE);
            stack.amount = split;
            task.requiredFluids.add(stack.copy());
            rem -= split;
        }
        IFluidHandlerItem handler = new ItemStack(Items.BUCKET).getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        stack = stack.copy();
        stack.amount = Integer.MAX_VALUE;
        if (handler.fill(stack, true) > 0)
            FTBQQuestImporter.provideQuestIcon(new BigItemStack(handler.getContainer()));
        else
            FTBQQuestImporter.provideQuestIcon(new BigItemStack(Items.BUCKET));

        return new ITask[]{task};
    }
}
