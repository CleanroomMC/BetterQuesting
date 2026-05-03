package betterquesting.questing.tasks;

import betterquesting.NBTUtil;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.client.gui2.tasks.PanelTaskLocation;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.tasks.factory.FactoryTaskLocation;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TaskLocation implements ITaskTickable {

    private static final String DEFAULT_STRUCTURE = "";
    private static final String DEFAULT_BIOME = "";
    private static final int DEFAULT_X = 0;
    private static final int DEFAULT_Y = 0;
    private static final int DEFAULT_Z = 0;
    private static final int DEFAULT_DIM = 0;
    private static final int DEFAULT_RANGE = -1;
    private static final boolean DEFAULT_VISIBLE = false;
    private static final boolean DEFAULT_HIDE_INFO = false;
    private static final boolean DEFAULT_INVERT = false;
    private static final boolean DEFAULT_TAXI_CAB = false;
    private static final boolean DEFAULT_OPTIONAL = false;
    private final Set<UUID> completeUsers = new TreeSet<>();
    public String name = "New Location";
    public String structure = DEFAULT_STRUCTURE;
    public String biome = DEFAULT_BIOME;
    public int x = DEFAULT_X;
    public int y = DEFAULT_Y;
    public int z = DEFAULT_Z;
    public int dim = DEFAULT_DIM;
    public int range = DEFAULT_RANGE;
    public boolean visible = DEFAULT_VISIBLE;
    public boolean hideInfo = DEFAULT_HIDE_INFO;
    public boolean invert = DEFAULT_INVERT;
    public boolean taxiCab = DEFAULT_TAXI_CAB;
    public boolean optional = DEFAULT_OPTIONAL;

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskLocation.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.location";
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
    public void resetUser(@Nullable UUID uuid) {
        if (uuid == null) {
            completeUsers.clear();
        } else {
            completeUsers.remove(uuid);
        }
    }

    @Override
    public void tickTask(@Nonnull ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        if (pInfo.PLAYER.ticksExisted % 100 == 0) internalDetect(pInfo, quest);
    }

    @Override
    public void detect(@Nonnull ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        internalDetect(pInfo, quest);
    }

    private void internalDetect(@Nonnull ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        if (!pInfo.PLAYER.isEntityAlive() || !(pInfo.PLAYER instanceof EntityPlayerMP)) return;

        EntityPlayerMP playerMP = (EntityPlayerMP) pInfo.PLAYER;

        boolean flag = false;

        if (playerMP.dimension == dim && (range <= 0 || getDistance(playerMP) <= range)) {
            if (!StringUtils.isNullOrEmpty(biome) && !new ResourceLocation(biome).equals(playerMP.getServerWorld().getBiome(playerMP.getPosition()).getRegistryName())) {
                if (!invert) return;
            } else if (!StringUtils.isNullOrEmpty(structure) && !playerMP.getServerWorld().getChunkProvider().isInsideStructure(playerMP.world, structure, playerMP.getPosition())) {
                if (!invert) return;
            } else if (visible && range > 0) // Do not do ray casting with infinite range!
            {
                Vec3d pPos = new Vec3d(playerMP.posX, playerMP.posY + playerMP.getEyeHeight(), playerMP.posZ);
                Vec3d tPos = new Vec3d(x, y, z);
                RayTraceResult mop = playerMP.world.rayTraceBlocks(pPos, tPos, false, true, false);

                if (mop == null || mop.typeOfHit != RayTraceResult.Type.BLOCK) {
                    flag = true;
                }
            } else {
                flag = true;
            }
        }

        if (flag != invert) {
            pInfo.ALL_UUIDS.forEach((uuid) -> {
                if (!isComplete(uuid)) setComplete(uuid);
            });
            pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
        }
    }

    private double getDistance(EntityPlayer player) {
        if (!taxiCab) {
            return player.getDistance(x, y, z);
        } else {
            BlockPos pPos = player.getPosition();
            return Math.abs(pPos.getX() - x) + Math.abs(pPos.getY() - y) + Math.abs(pPos.getZ() - z);
        }
    }

    @Deprecated
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt, boolean reduce) {
        nbt.setString("name", name);
        NBTUtil.setInteger(nbt, "posX", x, DEFAULT_X, reduce);
        NBTUtil.setInteger(nbt, "posY", y, DEFAULT_Y, reduce);
        NBTUtil.setInteger(nbt, "posZ", z, DEFAULT_Z, reduce);
        NBTUtil.setInteger(nbt, "dimension", dim, DEFAULT_DIM, reduce);
        NBTUtil.setString(nbt, "biome", biome, DEFAULT_BIOME, reduce);
        NBTUtil.setString(nbt, "structure", structure, DEFAULT_STRUCTURE, reduce);
        NBTUtil.setInteger(nbt, "range", range, DEFAULT_RANGE, reduce);
        NBTUtil.setBoolean(nbt, "visible", visible, DEFAULT_VISIBLE, reduce);
        NBTUtil.setBoolean(nbt, "hideInfo", hideInfo, DEFAULT_HIDE_INFO, reduce);
        NBTUtil.setBoolean(nbt, "invert", invert, DEFAULT_INVERT, reduce);
        NBTUtil.setBoolean(nbt, "taxiCabDist", taxiCab, DEFAULT_TAXI_CAB, reduce);
        NBTUtil.setBoolean(nbt, "optional", optional, DEFAULT_OPTIONAL, reduce);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        name = nbt.getString("name");
        x = NBTUtil.getInteger(nbt, "posX", DEFAULT_X);
        y = NBTUtil.getInteger(nbt, "posY", DEFAULT_Y);
        z = NBTUtil.getInteger(nbt, "posZ", DEFAULT_Z);
        dim = NBTUtil.getInteger(nbt, "dimension", DEFAULT_DIM);
        biome = NBTUtil.getString(nbt, "biome", DEFAULT_BIOME);
        structure = NBTUtil.getString(nbt, "structure", DEFAULT_STRUCTURE);
        range = NBTUtil.getInteger(nbt, "range", DEFAULT_RANGE);
        visible = NBTUtil.getBoolean(nbt, "visible", DEFAULT_VISIBLE);
        hideInfo = NBTUtil.getBoolean(nbt, "hideInfo", DEFAULT_HIDE_INFO);
        invert = NBTUtil.getBoolean(nbt, "invert", DEFAULT_INVERT) || nbt.getBoolean("invertDistance");
        taxiCab = NBTUtil.getBoolean(nbt, "taxiCabDist", DEFAULT_TAXI_CAB);
        optional = NBTUtil.getBoolean(nbt, "optional", DEFAULT_OPTIONAL);
    }

    @Override
    public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, @Nullable List<UUID> users) {
        NBTTagList jArray = new NBTTagList();

        completeUsers.forEach((uuid) -> {
            if (users == null || users.contains(uuid)) jArray.appendTag(new NBTTagString(uuid.toString()));
        });

        nbt.setTag("completeUsers", jArray);

        return nbt;
    }

    @Override
    public void readProgressFromNBT(NBTTagCompound nbt, boolean merge) {
        if (!merge) completeUsers.clear();
        NBTTagList cList = nbt.getTagList("completeUsers", 8);
        for (int i = 0; i < cList.tagCount(); i++) {
            try {
                completeUsers.add(UUID.fromString(cList.getStringTagAt(i)));
            } catch (Exception e) {
                BetterQuesting.logger.log(Level.ERROR, "Unable to load UUID for task", e);
            }
        }
    }

    @Override
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskLocation(rect, this);
    }

    @Override
    public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest) {
        return null;
    }

    @Override
    public boolean ignored(UUID uuid) {
        return optional;
    }

    @Override
    public List<String> getTextForSearch() {
        return Collections.singletonList(name);
    }
}
