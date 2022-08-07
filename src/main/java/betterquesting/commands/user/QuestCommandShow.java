package betterquesting.commands.user;

import betterquesting.api2.storage.DBEntry;
import betterquesting.client.gui2.GuiQuest;
import betterquesting.client.gui2.GuiQuestLines;
import betterquesting.commands.QuestCommandBase;
import betterquesting.questing.QuestDatabase;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.permission.DefaultPermissionLevel;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class QuestCommandShow extends QuestCommandBase {

    @Override
    public String getCommand() {
        return "show";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 2) {
            int questId = Integer.parseInt(args[1]);
            Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new GuiQuest(new GuiQuestLines(null), questId)));
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
        return args.length == 1 ? QuestDatabase.INSTANCE.getEntries().stream().map(DBEntry::getID).map(Object::toString).collect(Collectors.toList()) : Collections.emptyList();
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
