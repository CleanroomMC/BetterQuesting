package betterquesting.commands.admin;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.commands.QuestCommandBase;
import betterquesting.core.BetterQuesting;
import betterquesting.core.ModReference;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.legacy.ILegacyLoader;
import betterquesting.legacy.LegacyLoaderRegistry;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.network.handlers.NetSettingSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLine;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.storage.QuestSettings;
import com.google.common.base.Splitter;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.gson.JsonObject;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class QuestCommandDefaults extends QuestCommandBase {
    public static final String DEFAULT_FILE = "DefaultQuests";

    public static final String SETTINGS_FILE = "QuestSettings.json";

    public static final String QUEST_LINE_DIR = "QuestLines";
    public static final String QUEST_DIR = "Quests";
    public static final String NO_QUEST_LINE_DIRECTORY = "NoQuestLine";
    public static final String MULTI_QUEST_LINE_DIRECTORY = "MultipleQuestLine";

    public static final int FILE_NAME_MAX_LENGTH = 16;

    @Override
    public String getUsageSuffix() {
        return "[save|load|set] [file_name]";
    }

    @Override
    public boolean validArgs(String[] args) {
        return args.length == 2 || args.length == 3;
    }

    @Override
    public List<String> autoComplete(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "save", "load", "set");
        } else if (args.length == 3) {
            return Collections.singletonList("DefaultQuests");
        }

        return Collections.emptyList();
    }

    @Override
    public String getCommand() {
        return "default";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args) throws CommandException {
        String databaseName;
        File dataDir;
        // The location of the legacy single huge file.
        File legacyFile;

        if (args.length == 3 && !args[2].equalsIgnoreCase("DefaultQuests")) {
            databaseName = args[2];
            dataDir = new File(BQ_Settings.defaultDir, "saved_quests/" + args[2]);
            legacyFile = new File(BQ_Settings.defaultDir, "saved_quests/" + args[2] + ".json");
        } else {
            databaseName = null;
            dataDir = new File(BQ_Settings.defaultDir, DEFAULT_FILE);
            legacyFile = new File(BQ_Settings.defaultDir, DEFAULT_FILE + ".json");
        }

        if (args[1].equalsIgnoreCase("save")) {
            save(sender, databaseName, dataDir);
        } else if (args[1].equalsIgnoreCase("saveLegacy")) {
            saveLegacy(sender, databaseName, legacyFile);
        } else if (args[1].equalsIgnoreCase("load")) {
            load(sender, databaseName, dataDir, false);
        } else if (args[1].equalsIgnoreCase("loadLegacy")) {
            loadLegacy(sender, databaseName, dataDir, false);
        } else if (args[1].equalsIgnoreCase("set") && args.length == 3) {
            if (!dataDir.exists() && legacyFile.exists()) {
                setLegacy(sender, databaseName, legacyFile);
            } else {
                set(sender, databaseName, dataDir);
            }
        } else {
            throw getException(command);
        }
    }

    public static void save(@Nullable ICommandSender sender, String databaseName, File dataDir) {

        BiFunction<String, Integer, String> buildFileName =
                (name, id) -> {
                    String formattedName = removeChatFormatting(name).replaceAll("[^a-zA-Z0-9]", "");

                    if (formattedName.length() > FILE_NAME_MAX_LENGTH) {
                        formattedName = formattedName.substring(0, FILE_NAME_MAX_LENGTH);
                    }

                    if (!BQ_Settings.saveQuestsWithNames) {
                        return String.valueOf(id);
                    }
                    return String.format("%s-%s", formattedName, id);
                };


        File settingsFile = new File(dataDir, SETTINGS_FILE);
        if (dataDir.exists()) {
            if (!settingsFile.exists()) {
                QuestingAPI.getLogger().log(Level.ERROR, "Directory exists, but isn't a database\n{}", dataDir);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }

            try {
                FileUtils.deleteDirectory(dataDir);
            } catch (IOException e) {
                QuestingAPI.getLogger().log(Level.ERROR, "Failed to delete directory\n" + dataDir, e);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }
        }

        if (!dataDir.mkdirs()) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directory\n{}", dataDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
        // Don't write edit mode to json
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, false);
        NBTTagCompound settingsTag = QuestSettings.INSTANCE.writeToNBT(new NBTTagCompound());
        settingsTag.setString("format", BetterQuesting.FORMAT);
        JsonHelper.WriteToFile(settingsFile, NBTConverter.NBTtoJSON_Compound(settingsTag, new JsonObject(), true));
        // Turn on edit mode if it was on before
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);

        File questLineDir = new File(dataDir, QUEST_LINE_DIR);
        if (!questLineDir.exists() && !questLineDir.mkdirs()) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directories\n{}", questLineDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }
        ListMultimap<Integer, IQuestLine> questToQuestLineMultimap =
                MultimapBuilder.hashKeys().arrayListValues().build();

        for (DBEntry<IQuestLine> entry : QuestLineDatabase.INSTANCE.getEntries()) {
            int questLineId = entry.getID();
            IQuestLine questLine = entry.getValue();
            questLine.getEntries().forEach(quest -> questToQuestLineMultimap.put(quest.getID(), questLine));
            String questLineName = questLine.getProperty(NativeProps.NAME);
            String questLineNameTranslated = QuestTranslation.translate(questLineName);

            File questLineFile = new File(questLineDir, buildFileName.apply(questLineNameTranslated, questLineId) + ".json");
            NBTTagCompound questLineTag = questLine.writeToNBT(new NBTTagCompound(), null);
            JsonHelper.WriteToFile(questLineFile, NBTConverter.NBTtoJSON_Compound(questLineTag, new JsonObject(), true));
        }
        ;


        for (DBEntry<IQuest> entry : QuestDatabase.INSTANCE.getEntries()) {
            int questId = entry.getID();
            IQuest quest = entry.getValue();
            List<IQuestLine> questLines = questToQuestLineMultimap.get(questId);

            File questDir = new File(dataDir, QUEST_DIR);
            switch (questLines.size()) {
                case 0:
                    questDir = new File(questDir, NO_QUEST_LINE_DIRECTORY);
                    break;

                case 1:
                    IQuestLine questLine = questLines.get(0);
                    int questLineId = QuestLineDatabase.INSTANCE.getID(questLine);
                    String questLineName = questLine.getProperty(NativeProps.NAME);
                    String translatedLineName = QuestTranslation.translate(questLineName);
                    questDir = new File(questDir, buildFileName.apply(translatedLineName, questLineId));
                    break;
                default:
                    questDir = new File(questDir, MULTI_QUEST_LINE_DIRECTORY);

            }

            String questName = quest.getProperty(NativeProps.NAME);
            String translatedQuestName = QuestTranslation.translate(questName);

            File questFile = new File(questDir, buildFileName.apply(translatedQuestName, questId) + ".json");
            if (!questFile.exists() && !questFile.mkdirs()) {
                QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directories\n{}", questFile);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }

            NBTTagCompound questTag = quest.writeToNBT(new NBTTagCompound());
            JsonHelper.WriteToFile(questFile, NBTConverter.NBTtoJSON_Compound(questTag, new JsonObject(), true));
        }

        if (databaseName != null && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.save2", databaseName);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.save");
        }
    }

    public static void saveLegacy(@Nullable ICommandSender sender, String databaseName, File legacyFile) {
        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);

        NBTTagCompound base = new NBTTagCompound();

        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, false);
        base.setTag("questSettings", QuestSettings.INSTANCE.writeToNBT(new NBTTagCompound()));
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);
        base.setTag("questDatabase", QuestDatabase.INSTANCE.writeToNBT(new NBTTagList(), null));
        base.setTag("questLines", QuestLineDatabase.INSTANCE.writeToNBT(new NBTTagList(), null));
        base.setString("format", BetterQuesting.FORMAT);
        base.setString("build", ModReference.VERSION);
        JsonHelper.WriteToFile(legacyFile, NBTConverter.NBTtoJSON_Compound(base, new JsonObject(), true));

        if (databaseName != null && databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sender.sendMessage(new TextComponentTranslation("betterquesting.cmd.default.save2", databaseName + ".json"));
        } else {
            sender.sendMessage(new TextComponentTranslation("betterquesting.cmd.default.save"));
        }
    }

    public static void load(@Nullable ICommandSender sender, @Nullable String databaseName, File dataDir, boolean loadWorldSettings) {
        if (!dataDir.exists()) {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
            return;
        }

        Function<File, NBTTagCompound> readNbt =
                file -> NBTConverter.JSONtoNBT_Object(JsonHelper.ReadFromFile(file), new NBTTagCompound(), true);

        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
        boolean hardMode = QuestSettings.INSTANCE.getProperty(NativeProps.HARDCORE);
        NBTTagList jsonP = QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null);
        
        File settingsFile = new File(dataDir, SETTINGS_FILE);
        if (!settingsFile.exists()) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to find file\n{}", settingsFile);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }
        QuestSettings.INSTANCE.readFromNBT(readNbt.apply(settingsFile));
        File questLineDir = new File(dataDir, QUEST_LINE_DIR);
        NBTTagList questLineDatabase = new NBTTagList();
        List<File> sortedQuestLineFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(questLineDir.toPath())) {
            paths.filter(Files::isRegularFile).forEach(
                    path -> {
                        File questLineFile = path.toFile();
                        sortedQuestLineFiles.add(questLineFile);
                    }
            );
        } catch (IOException e) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to traverse directory\n" + questLineDir, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        if (!sortedQuestLineFiles.isEmpty()) {
            sortedQuestLineFiles.sort((file1, file2) -> {
                int id1 = Integer.parseInt(file1.getName().replaceFirst("[^0-9]+", ""));
                int id2 = Integer.parseInt(file2.getName().replaceFirst("[^0-9]+", ""));
                return id1 - id2;
            });
        }

        sortedQuestLineFiles.stream()
                .map(readNbt)
                .forEach(questLineDatabase::appendTag);

        QuestLineDatabase.INSTANCE.readFromNBT(questLineDatabase, false);

        File questDir = new File(dataDir, QUEST_DIR);
        try (Stream<Path> paths = Files.walk(questDir.toPath())) {
            paths.filter(Files::isRegularFile).forEach(
                    path -> {
                        File questFile = path.toFile();
                        NBTTagCompound questTag = readNbt.apply(questFile);
                        int questId = Integer.parseInt(questFile.getName().replaceFirst("[^0-9]+", ""));

                        if (questId < 0) {
                            return;
                        }

                        IQuest quest = new QuestInstance();
                        quest.readFromNBT(questTag);
                        QuestDatabase.INSTANCE.add(questId, quest);
                    }
            );
        } catch (IOException e) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to traverse directory\n" + questDir, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        if (!loadWorldSettings) {
            // Don't load world-specific settings, so restore them from the snapshot we took.
            QuestDatabase.INSTANCE.readProgressFromNBT(jsonP, false);
            QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);
            QuestSettings.INSTANCE.setProperty(NativeProps.HARDCORE, hardMode);
        }

        if (databaseName != null && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.load2", databaseName);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.load");
        }

        NetSettingSync.sendSync(null);
        NetQuestSync.quickSync(-1, true, true);
        NetChapterSync.sendSync(null, null);
        SaveLoadHandler.INSTANCE.markDirty();
    }

    public static void loadLegacy(@Nullable ICommandSender sender, @Nullable String databaseName, File legacyFile, boolean loadWorldSettings) {
        if (legacyFile.exists()) {
            boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
            boolean hardMode = QuestSettings.INSTANCE.getProperty(NativeProps.HARDCORE);

            NBTTagList jsonP = QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null);

            JsonObject j1 = JsonHelper.ReadFromFile(legacyFile);
            NBTTagCompound nbt1 = NBTConverter.JSONtoNBT_Object(j1, new NBTTagCompound(), true);

            ILegacyLoader loader = LegacyLoaderRegistry.getLoader(nbt1.hasKey("format", 8) ? nbt1.getString("format") : "0.0.0");

            if (loader == null) {
                QuestSettings.INSTANCE.readFromNBT(nbt1.getCompoundTag("questSettings"));
                QuestDatabase.INSTANCE.readFromNBT(nbt1.getTagList("questDatabase", 10), false);
                QuestLineDatabase.INSTANCE.readFromNBT(nbt1.getTagList("questLines", 10), false);
            } else {
                loader.readFromJson(j1);
            }

            if (!loadWorldSettings) {
                // Don't load world-specific settings, so restore them from the snapshot we took.
                QuestDatabase.INSTANCE.readProgressFromNBT(jsonP, false);
                QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);
                QuestSettings.INSTANCE.setProperty(NativeProps.HARDCORE, hardMode);
            }

            if (databaseName != null && !databaseName.equalsIgnoreCase("DefaultQuests")) {
                sendChatMessage(sender, "betterquesting.cmd.default.load2", databaseName + ".json");
            } else {
                sendChatMessage(sender, "betterquesting.cmd.default.load");
            }

            NetSettingSync.sendSync(null);
            NetQuestSync.quickSync(-1, true, true);
            NetChapterSync.sendSync(null, null);
            SaveLoadHandler.INSTANCE.markDirty();
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
        }
    }

    public static void set(@Nullable ICommandSender sender, String databaseName, File dataDir) {
        if (!dataDir.exists() || databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
            return;
        }

        File defDir = new File(BQ_Settings.defaultDir, DEFAULT_FILE);

        if (defDir.exists()) {
            try {
                FileUtils.deleteDirectory(defDir);
            } catch (IOException e) {
                QuestingAPI.getLogger().log(Level.ERROR, "Failed to delete directory\n" + defDir, e);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }
        }

        try {
            FileUtils.copyDirectory(dataDir, defDir);
        } catch (IOException e) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to copy directory\n" + dataDir, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        sendChatMessage(sender, "betterquesting.cmd.default.set", databaseName);
    }

    public static void setLegacy(@Nullable ICommandSender sender, String databaseName, File legacyFile) {
        if (legacyFile.exists() && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            File defFile = new File(BQ_Settings.defaultDir, DEFAULT_FILE + ".json");

            if (defFile.exists()) {
                defFile.delete();
            }

            JsonHelper.CopyPaste(legacyFile, defFile);

            sendChatMessage(sender, "betterquesting.cmd.default.set", databaseName);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
        }
    }
    @Override
    public String getPermissionNode() {
        return "betterquesting.command.admin.default";
    }

    @Override
    public DefaultPermissionLevel getPermissionLevel() {
        return DefaultPermissionLevel.OP;
    }

    @Override
    public String getPermissionDescription() {
        return "Permission to saves/loads the current quest database to/from the global default directory";
    }

    private static String removeChatFormatting(String string) {
        return string.replaceAll("§[0-9a-fk-or]", "");
    }

    /** Helper method that handles having null sender. */
    private static void sendChatMessage(
            @Nullable ICommandSender sender, String translationKey, Object... args) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(new TextComponentTranslation(translationKey, args));
    }
}
