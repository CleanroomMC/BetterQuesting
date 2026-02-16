package betterquesting.questing.rewards;

import betterquesting.AdminExecute;
import betterquesting.NBTUtil;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.client.gui2.rewards.PanelRewardCommand;
import betterquesting.questing.rewards.factory.FactoryRewardCommand;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.FunctionObject;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.CommandBlockBaseLogic;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RewardCommand implements IReward {

    private static final String DEFAULT_TITLE = "bq_standard.reward.command";
    private static final String DEFAULT_DESC = "Run a command script";
    private static final boolean DEFAULT_VIA_PLAYER = false;
    private static final boolean DEFAULT_HIDE_ICON = true;
    private static final boolean DEFAULT_AS_SCRIPT = true;
    public String command = "#Script Comment\nsay Running reward script...\nsay @s Claimed a reward";
    public String title = DEFAULT_TITLE;
    public String desc = DEFAULT_DESC;
    public boolean viaPlayer = DEFAULT_VIA_PLAYER;
    public boolean hideIcon = DEFAULT_HIDE_ICON;
    public boolean asScript = DEFAULT_AS_SCRIPT;

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryRewardCommand.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return title;
    }

    @Override
    public boolean canClaim(EntityPlayer player, DBEntry<IQuest> quest) {
        return true;
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public void claimReward(final EntityPlayer player, DBEntry<IQuest> quest) {
        if (player.world.isRemote) return;

        UUID playerID = QuestingAPI.getQuestingUUID(player);

        // NOTE: These replacements are only kept for legacy reasons. Entity selectors are much more suitable and more powerful
        String tmp = command.replaceAll("VAR_NAME", player.getName());
        String finCom = tmp.replaceAll("VAR_UUID", playerID.toString());
        String[] comAry = finCom.split("\n");

        MinecraftServer server = player.world.getMinecraftServer();
        ICommandSender sender = viaPlayer ? new AdminExecute(player) : new RewardCommandSender(player);

        if (asScript) {
            // New functions don't support preceeding forward slash so we remove them on legacy commands
            for (int i = 0; i < comAry.length; i++)
                if (comAry[i].startsWith("/")) comAry[i] = comAry[i].replaceFirst("/", "");

            FunctionObject func = FunctionObject.create(server.getFunctionManager(), Arrays.asList(comAry));

            server.getFunctionManager().execute(func, sender);
        } else {
            server.getCommandManager().executeCommand(sender, finCom);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        command = nbt.getString("command");
        title = NBTUtil.getString(nbt, "title", DEFAULT_TITLE);
        desc = NBTUtil.getString(nbt, "description", DEFAULT_DESC);
        viaPlayer = NBTUtil.getBoolean(nbt, "viaPlayer", DEFAULT_VIA_PLAYER);
        hideIcon = NBTUtil.getBoolean(nbt, "hideBlockIcon", DEFAULT_HIDE_ICON);
        asScript = NBTUtil.getBoolean(nbt, "asScript", DEFAULT_AS_SCRIPT);
    }

    @Deprecated
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt, boolean reduce) {
        nbt.setString("command", command);
        NBTUtil.setString(nbt, "title", title, DEFAULT_TITLE, reduce);
        NBTUtil.setString(nbt, "description", desc, DEFAULT_TITLE, reduce);
        NBTUtil.setBoolean(nbt, "viaPlayer", viaPlayer, DEFAULT_VIA_PLAYER, reduce);
        NBTUtil.setBoolean(nbt, "hideBlockIcon", hideIcon, DEFAULT_HIDE_ICON, reduce);
        NBTUtil.setBoolean(nbt, "asScript", asScript, DEFAULT_AS_SCRIPT, reduce);
        return nbt;
    }

    @Override
    public IGuiPanel getRewardGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelRewardCommand(rect, this);
    }

    @Override
    public GuiScreen getRewardEditor(GuiScreen screen, DBEntry<IQuest> quest) {
        return null;
    }

    @Override
    public List<String> getTextForSearch() {
        return Collections.singletonList(command);
    }

    public static class RewardCommandSender extends CommandBlockBaseLogic {
        private final Entity entity;

        private RewardCommandSender(@Nonnull Entity entity) {
            this.entity = entity;
        }

        @Nonnull
        @Override
        public BlockPos getPosition() {
            return entity.getPosition();
        }

        @Nonnull
        @Override
        public Vec3d getPositionVector() {
            return entity.getPositionVector();
        }

        @Nonnull
        @Override
        public World getEntityWorld() {
            return entity.getEntityWorld();
        }

        @Override
        public Entity getCommandSenderEntity() {
            return entity;
        }

        @Override
        public void updateCommand() {
        }

        @Override
        public int getCommandBlockType() {
            return 0;
        }

        @Override
        public void fillInInfo(@Nonnull ByteBuf p_145757_1_) {
        }

        @Nonnull
        @Override
        public String getName() {
            return "BetterQuesting";
        }

        @Override
        public MinecraftServer getServer() {
            return entity.getServer();
        }
    }
}
