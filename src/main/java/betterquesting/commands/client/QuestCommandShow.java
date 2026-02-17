package betterquesting.commands.client;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.commands.QuestCommandBase;
import betterquesting.questing.QuestDatabase;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.server.permission.DefaultPermissionLevel;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class QuestCommandShow extends QuestCommandBase {

    public static final String PREFIX = "betterquesting.msg.share_quest:";
    public static final String VIEW = "betterquesting.msg.view_quest:";

    public static void sendTrigger(EntityPlayerSP player, int id) {
        player.sendChatMessage(PREFIX + id);
    }

    @Override
    public String getCommand() {
        return "show";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args) throws CommandException {
        if (sender instanceof EntityPlayerSP player && args.length == 2) {
            try {
                int questId = Integer.parseInt(args[1]);
                IQuest quest = QuestDatabase.INSTANCE.getValue(questId);
                if (quest == null) {
                    sender.sendMessage(new TextComponentTranslation("betterquesting.msg.share_quest_invalid", String.valueOf(questId)));
                } else {
                    if (QuestCache.isQuestShown(quest, QuestingAPI.getQuestingUUID(player), player)) {
                        sendTrigger(player, questId);
                    } else {
                        sender.sendMessage(new TextComponentTranslation("betterquesting.msg.share_quest_hover_text_failure"));
                    }
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(new TextComponentTranslation("betterquesting.msg.share_quest_invalid", args[1]));
            }
        }
    }

    @Override
    public String getUsageSuffix() {
        return "[<quest_id>]";
    }

    @Override
    public boolean validArgs(String[] args) {
        return args.length == 2;
    }

    @Override
    public List<String> autoComplete(MinecraftServer server, ICommandSender sender, String[] args) {
        return args.length == 2 ? QuestDatabase.INSTANCE.getEntries().stream().map(DBEntry::getID).map(Object::toString).collect(Collectors.toList()) : Collections.emptyList();
    }

    @Override
    public String getPermissionNode() {
        return "betterquesting.command.user.show";
    }

    @Override
    public DefaultPermissionLevel getPermissionLevel() {
        return DefaultPermissionLevel.ALL;
    }

    @Override
    public String getPermissionDescription() {
        return "Permission to execute command which shows the player a particular quest.";
    }

}
