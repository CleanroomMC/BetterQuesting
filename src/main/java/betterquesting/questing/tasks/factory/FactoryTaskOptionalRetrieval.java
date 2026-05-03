package betterquesting.questing.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.tasks.TaskRetrieval;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskOptionalRetrieval implements IFactoryData<ITask, NBTTagCompound> {

    public static final FactoryTaskOptionalRetrieval INSTANCE = new FactoryTaskOptionalRetrieval();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BetterQuesting.MODID_STD + ":optional_retrieval");
    }

    @Override
    public TaskRetrieval createNew() {
        TaskRetrieval task = new TaskRetrieval();
        task.optional = true;
        return task;
    }

    @Override
    public TaskRetrieval loadFromData(NBTTagCompound json) {
        TaskRetrieval task = new TaskRetrieval();
        task.readFromNBT(json);
        task.optional = true;
        return task;
    }
}
