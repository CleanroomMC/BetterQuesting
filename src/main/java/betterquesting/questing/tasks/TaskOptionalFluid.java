package betterquesting.questing.tasks;

import betterquesting.core.BetterQuesting;
import betterquesting.questing.tasks.factory.FactoryTaskOptionalFluid;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;

public class TaskOptionalFluid extends TaskFluid {

    @Override
    public String getUnlocalisedName() {
        return BetterQuesting.MODID_STD + ".task.optional_fluid";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskOptionalFluid.INSTANCE.getRegistryName();
    }

    @Override
    public boolean ignored(UUID uuid) {
        return true;
    }
}
