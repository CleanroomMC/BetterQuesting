package betterquesting.questing.rewards;

import betterquesting.NBTUtil;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.client.gui2.rewards.PanelRewardScoreboard;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.rewards.factory.FactoryRewardScoreboard;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.List;

public class RewardScoreboard implements IReward {

    private static final String DEFAULT_TYPE = "dummy";
    private static final boolean DEFAULT_RELATIVE = true;
    public String score = "Reputation";
    public String type = DEFAULT_TYPE;
    public boolean relative = DEFAULT_RELATIVE;
    public int value = 1;

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryRewardScoreboard.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.reward.scoreboard";
    }

    @Override
    public boolean canClaim(EntityPlayer player, DBEntry<IQuest> quest) {
        return true;
    }

    @Override
    public void claimReward(EntityPlayer player, DBEntry<IQuest> quest) {
        Scoreboard board = player.getWorldScoreboard();

        ScoreObjective scoreObj = board.getObjective(score);

        if (scoreObj == null) {
            try {
                IScoreCriteria criteria = IScoreCriteria.INSTANCES.get(type);
                criteria = criteria != null ? criteria : new ScoreCriteria(score);
                scoreObj = board.addScoreObjective(score, criteria);
                scoreObj.setDisplayName(score);
            } catch (Exception e) {
                BetterQuesting.logger.log(Level.ERROR, "Unable to create score '" + score + "' for reward!", e);
            }
        }

        if (scoreObj == null || scoreObj.getCriteria().isReadOnly()) {
            return;
        }

        Score s = board.getOrCreateScore(player.getName(), scoreObj);

        if (relative) {
            s.increaseScore(value);
        } else {
            s.setScorePoints(value);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        score = nbt.getString("score");
        type = NBTUtil.getString(nbt, "type", DEFAULT_TYPE);
        value = nbt.getInteger("value");
        relative = NBTUtil.getBoolean(nbt, "relative", DEFAULT_RELATIVE);
    }

    @Deprecated
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt, boolean reduce) {
        nbt.setString("score", score);
        NBTUtil.setString(nbt, "type", type, DEFAULT_TYPE, reduce);
        nbt.setInteger("value", value);
        NBTUtil.setBoolean(nbt, "relative", relative, DEFAULT_RELATIVE, reduce);
        return nbt;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getRewardGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelRewardScoreboard(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getRewardEditor(GuiScreen screen, DBEntry<IQuest> quest) {
        return null;
    }

    @Override
    public List<String> getTextForSearch() {
        List<String> texts = new ArrayList<>();
        texts.add(score);
        texts.add(String.valueOf(value));
        return texts;
    }
}
