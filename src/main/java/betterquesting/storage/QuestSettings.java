package betterquesting.storage;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.IPropertyType;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.storage.IQuestSettings;
import betterquesting.core.BetterQuesting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public class QuestSettings extends PropertyContainer implements IQuestSettings {
    private static final String EDIT_MODE = BetterQuesting.MODID + ".edit_mode";
    public static final QuestSettings INSTANCE = new QuestSettings();

    public QuestSettings() {
        this.setupProps();
    }

    public void setEditMode(EntityPlayer player, boolean edit){
        if (player == null) return;
        NBTTagCompound playerData = player.getEntityData();
        NBTTagCompound data = playerData.hasKey(EntityPlayer.PERSISTED_NBT_TAG) ? playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG) : new NBTTagCompound();
        data.setBoolean(EDIT_MODE, edit);
        playerData.setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
    }

    public boolean getEditMode(EntityPlayer player){
        if (player == null) return false;
        NBTTagCompound playerData = player.getEntityData();
        NBTTagCompound data = playerData.hasKey(EntityPlayer.PERSISTED_NBT_TAG) ? playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG) : null;

        if (data == null)
            return false;

        return data.getBoolean(EDIT_MODE);
    }

    @Override
    public boolean canUserEdit(EntityPlayer player) {
        if (player == null) return false;
        return getEditMode(player) && NameCache.INSTANCE.isOP(QuestingAPI.getQuestingUUID(player));
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
