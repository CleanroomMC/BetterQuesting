package betterquesting.questing.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.tasks.TaskOptionalFluid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskOptionalFluid implements IFactoryData<ITask, NBTTagCompound> {

    public static final FactoryTaskOptionalFluid INSTANCE = new FactoryTaskOptionalFluid();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BetterQuesting.MODID_STD + ":optional_fluid");
    }

    @Override
    public TaskOptionalFluid createNew() {
        return new TaskOptionalFluid();
    }

    @Override
    public TaskOptionalFluid loadFromData(NBTTagCompound json) {
        TaskOptionalFluid task = new TaskOptionalFluid();
        task.readFromNBT(json);
        return task;
    }
}
