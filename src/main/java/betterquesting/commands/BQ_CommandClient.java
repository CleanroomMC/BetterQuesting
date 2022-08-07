package betterquesting.commands;

import betterquesting.commands.client.QuestCommandShow;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BQ_CommandClient extends CommandBase {
    private final List<QuestCommandBase> coms = new ArrayList<>();

    public BQ_CommandClient() {
        coms.add(new QuestCommandShow());
    }

    @Override
    public String getName() {
        return "bq_client";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        StringBuilder txt = new StringBuilder();

        for (int i = 0; i < coms.size(); i++) {
            QuestCommandBase c = coms.get(i);
            txt.append("/bq_client ").append(c.getCommand());

            if (c.getUsageSuffix().length() > 0) {
                txt.append(" ").append(c.getUsageSuffix());
            }

            if (i < coms.size() - 1) {
                txt.append(", ");
            }
        }

        return txt.toString();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] strings, BlockPos pos) {
        if (strings.length == 1) {
            List<String> base = new ArrayList<>();
            for (QuestCommandBase c : coms) {
                base.add(c.getCommand());
            }
            return getListOfStringsMatchingLastWord(strings, base.toArray(new String[0]));
        } else if (strings.length > 1) {
            for (QuestCommandBase c : coms) {
                if (c.getCommand().equalsIgnoreCase(strings[0])) {
                    return c.autoComplete(server, sender, strings);
                }
            }
        }

        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new WrongUsageException(this.getUsage(sender));
        }

        for (QuestCommandBase c : coms) {
            if (c.getCommand().equalsIgnoreCase(args[0])) {
                if (c.validArgs(args)) {
                    c.runCommand(server, this, sender, args);
                    return;
                } else {
                    throw c.getException(this);
                }
            }
        }

        throw new WrongUsageException(this.getUsage(sender));
    }

    /**
     * Return whether the specified command parameter index is a username parameter.
     */
    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        if (args.length < 1) {
            return false;
        }

        for (QuestCommandBase c : coms) {
            if (c.getCommand().equalsIgnoreCase(args[0])) {
                return c.isArgUsername(args, index);
            }
        }

        return false;
    }

}
