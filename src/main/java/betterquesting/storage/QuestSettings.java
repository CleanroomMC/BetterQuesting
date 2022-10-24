package betterquesting.storage;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.IPropertyType;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.storage.IQuestSettings;
import betterquesting.core.BetterQuesting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class QuestSettings extends PropertyContainer implements IQuestSettings {
    public static final String EDIT_MODE = BetterQuesting.MODID + ":edit_mode";
    public static final QuestSettings INSTANCE = new QuestSettings();

    public QuestSettings() {
        this.setupProps();
    }

    public void setEditMode(boolean edit){
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        server.getEntityWorld().getGameRules().setOrCreateGameRule(EDIT_MODE, String.valueOf(edit));
    }

    public boolean getEditMode() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server.getEntityWorld().getGameRules().getBoolean(EDIT_MODE);
    }

    @Override
    public boolean canUserEdit(EntityPlayer player) {
        if (player == null) return false;
        return getEditMode() && NameCache.INSTANCE.isOP(QuestingAPI.getQuestingUUID(player));
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        this.setupProps();
    }

    @Override
    public void reset() {
        this.readFromNBT(new NBTTagCompound());
    }

    private void setupProps() {
        this.setupValue(NativeProps.PACK_NAME);
        this.setupValue(NativeProps.PACK_VER);

        this.setupValue(NativeProps.PARTY_ENABLE);
        this.setupValue(NativeProps.HARDCORE);
        this.setupValue(NativeProps.LIVES_DEF);
        this.setupValue(NativeProps.LIVES_MAX);

        this.setupValue(NativeProps.HOME_IMAGE);
        this.setupValue(NativeProps.HOME_ANC_X);
        this.setupValue(NativeProps.HOME_ANC_Y);
        this.setupValue(NativeProps.HOME_OFF_X);
        this.setupValue(NativeProps.HOME_OFF_Y);
    }

    private <T> void setupValue(IPropertyType<T> prop) {
        this.setupValue(prop, prop.getDefault());
    }

    private <T> void setupValue(IPropertyType<T> prop, T def) {
        this.setProperty(prop, this.getProperty(prop, def));
    }
}
