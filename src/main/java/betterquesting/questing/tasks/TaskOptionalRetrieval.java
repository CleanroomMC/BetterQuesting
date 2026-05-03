package betterquesting.questing.tasks;

import net.minecraft.nbt.NBTTagCompound;

@Deprecated
public class TaskOptionalRetrieval extends TaskRetrieval {

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        optional = true;
    }
}
