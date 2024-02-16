package betterquesting.importers.ftbq;

import betterquesting.api.client.importers.IImporter;
import betterquesting.api.enums.EnumLogic;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.*;
import betterquesting.api.questing.IQuest.RequirementType;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.storage.IDatabaseNBT;
import betterquesting.core.BetterQuesting;
import betterquesting.importers.ftbq.FTBEntry.FTBEntryType;
import betterquesting.importers.ftbq.converters.rewards.FtbqRewardCommand;
import betterquesting.importers.ftbq.converters.rewards.FtbqRewardItem;
import betterquesting.importers.ftbq.converters.rewards.FtbqRewardXP;
import betterquesting.importers.ftbq.converters.tasks.*;
import betterquesting.questing.tasks.TaskCheckbox;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;

public class FTBQQuestImporter implements IImporter {
    public static final FTBQQuestImporter INSTANCE = new FTBQQuestImporter();
    private static final FileFilter FILTER = new FTBQFileFIlter();

    private static final HashMap<String, Function<NBTTagCompound, ITask[]>> taskConverters = new HashMap<>();
    private static final HashMap<String, Function<NBTTagCompound, IReward[]>> rewardConverters = new HashMap<>();

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.importer.ftbq_quest.name";
    }

    @Override
    public String getUnlocalisedDescription() {
        return "bq_standard.importer.ftbq_quest.desc";
    }

    @Override
    public FileFilter getFileFilter() {
        return FILTER;
    }

    @Override
    public void loadFiles(IQuestDatabase questDB, IQuestLineDatabase lineDB, File[] files) {
        for (File f : files) {
            if (f == null || f.getParent() == null)
                continue;
            NBTTagCompound indexNBT = readFile(f);
            startImport(questDB, lineDB, indexNBT, f.getParentFile(), f.getName().toLowerCase().endsWith(".snbt"));
        }
    }

    // NOTE: FTBQ shares IDs between multiple object types (WHY?!). Check type before use
    private final HashMap<String, FTBEntry> ID_MAP = new HashMap<>();

    @Nullable
    private static NBTTagCompound readFile(File file) {
        if (file.getName().toLowerCase().endsWith(".snbt")) {
            return SNBTReader.read(file);
        } else {
            try (FileInputStream chFis = new FileInputStream(file)) {
                return CompressedStreamTools.readCompressed(chFis);
            } catch (Exception e) {
                BetterQuesting.logger.error("Failed to import file entry: " + file, e);
                return null;
            }
        }
    }
    private void startImport(IQuestDatabase questDB, IQuestLineDatabase lineDB, NBTTagCompound tagIndex, File folder, boolean isSnbt) {
        String[] indexIDs; // Read out the chapter index names
        if (isSnbt) {
            //String
            NBTTagList tagList = tagIndex.getTagList("index", 8);
            indexIDs = new String[tagList.tagCount()];
            int i = 0;
            for (NBTBase index : tagList) {
                indexIDs[i] = ((NBTTagString) index).getString();
                i++;
            }
        } else {
            int[] indices = tagIndex.getIntArray("index");
            indexIDs = new String[indices.length];
            for (int i = 0; i < indices.length; i++) {
                indexIDs[i] = String.format("%1$08x", indices[i]);
            }
        }
        ID_MAP.clear();

        // Cleanup from previous imports
        requestQuestIcon(null);
        requestChapterIcon(null);

        HashMap<IQuest, Pair<String[], Boolean>> parentMap = new HashMap<>();//quest->(dependencies,hideLine)

        BetterQuesting.logger.info("Found " + indexIDs.length + " quest chapter(s) to import");
        for (String id : indexIDs) {

            File qlFolder = new File(folder, id);
            if (!qlFolder.exists() || !qlFolder.isDirectory())
                continue;
            File[] contents = qlFolder.listFiles();
            if (contents == null)
                continue;

            int lineID = lineDB.nextID();
            IQuestLine questLine = lineDB.createNew(lineID);
            ID_MAP.put(id, new FTBEntry(lineID, questLine, FTBEntryType.LINE));

            // File order may but valid icon providing quest before the chapter file so we request early
            requestChapterIcon(questLine);

            for (File questFile : contents) {
                if (!questFile.getName().toLowerCase().endsWith(".nbt") && !questFile.getName().toLowerCase().endsWith(".snbt"))
                    continue; // No idea why this file is in here

                NBTTagCompound qTag = readFile(questFile);
                if (qTag == null)
                    continue;

                // === CHAPTER INFO ===

                if (questFile.getName().equalsIgnoreCase("chapter.nbt") || questFile.getName().equalsIgnoreCase("chapter.snbt")) {
                    questLine.setProperty(NativeProps.NAME, qTag.getString("title"));
                    NBTTagList desc = qTag.getTagList("description", 8);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < desc.tagCount(); i++) {
                        sb.append(desc.getStringTagAt(i));
                        if (i + 1 < desc.tagCount())
                            sb.append("\n");
                    }
                    questLine.setProperty(NativeProps.DESC, sb.toString());
                    if (qTag.hasKey("icon")) {
                        // We're not even going to try and make an equivalent dynamic icon (although BQ could support it later)
                        BigItemStack icoStack = FTBQUtils.convertItem(qTag.getTag("icon"));
                        if (!icoStack.getBaseStack().isEmpty())
                            questLine.setProperty(NativeProps.ICON, icoStack);
                        requestChapterIcon(null);
                    }
                    continue;
                }

                requestQuestIcon(null);

                // === QUEST DATA ===

                String hexID = questFile.getName().substring(0, questFile.getName().length() - (isSnbt ? ".snbt".length() : ".nbt".length()));
                int questID = questDB.nextID();
                IQuest quest = questDB.createNew(questID);
                IQuestLineEntry qle = questLine.createNew(questID);
                ID_MAP.put(hexID, new FTBEntry(questID, quest, FTBEntryType.QUEST)); // Add this to the weird ass ID mapping
                if (qTag.hasKey("title", 8)) {
                    quest.setProperty(NativeProps.NAME, qTag.getString("title"));
                } else {
                    quest.setProperty(NativeProps.NAME, hexID);
                    NBTTagList taskList = qTag.getTagList("tasks", 10);
                    if (taskList.tagCount() >= 1) {
                        NBTTagCompound compoundTag = taskList.getCompoundTagAt(0);
                        if (compoundTag.hasKey("title"))
                            quest.setProperty(NativeProps.NAME, compoundTag.getString("title"));
                    }
                }
                NBTTagList desc = qTag.getTagList("text", 8);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < desc.tagCount(); i++) {
                    sb.append(desc.getStringTagAt(i));
                    if (i + 1 < desc.tagCount())
                        sb.append("\n");
                }
                quest.setProperty(NativeProps.DESC, sb.toString());
                quest.setProperty(NativeProps.VISIBILITY, EnumQuestVisibility.ALWAYS);

                if (qTag.hasKey("icon")) {
                    // We're not even going to try and make an equivalent dynamic icon (although BQ could support it later)
                    BigItemStack icoStack = FTBQUtils.convertItem(qTag.getTag("icon"));
                    if (!icoStack.getBaseStack().isEmpty())
                        quest.setProperty(NativeProps.ICON, icoStack);
                    requestQuestIcon(null);
                } else {
                    requestQuestIcon(quest);
                    NBTTagList taskList = qTag.getTagList("tasks", 10);
                    if (taskList.tagCount() >= 1) {
                        NBTTagCompound compoundTag = taskList.getCompoundTagAt(0);
                        if (compoundTag.hasKey("icon")) {
                            BigItemStack iconStack = FTBQUtils.convertItem(compoundTag.getTag("icon"));
                            if (!iconStack.getBaseStack().isEmpty()) {
                                quest.setProperty(NativeProps.ICON, iconStack);
                                requestQuestIcon(null);
                            }
                        }
                    }
                }

                // Fun Fact: FTBQ used to have a hard limit of -25 to +25 for it's quest coordinates even if you try forcing it higher
                // Update: It's now infinite, uses double floating point percision, isn't grid snapped, and has size. Progress!
                double iconSize = 24;
                double gridSize = 32;
                double size = qTag.hasKey("size") ? qTag.getDouble("size") : 1D;
                size *= iconSize;
                qle.setSize(MathHelper.ceil(size), MathHelper.ceil(size));
                qle.setPosition(MathHelper.ceil(qTag.getDouble("x") * gridSize - (gridSize / 2D)), MathHelper.ceil(qTag.getDouble("y") * gridSize - (gridSize / 2D)));

                // === PARENTING INFO ===

                String[] depKeys = null;
                if (isSnbt) {
                    if (qTag.hasKey("dependencies", 9)) {
                        NBTTagList tagList = qTag.getTagList("dependencies", 8);
                        depKeys = new String[tagList.tagCount()];
                        for (int i = 0; i < tagList.tagCount(); i++) {
                            depKeys[i] = tagList.getStringTagAt(i);
                        }
                    }
                } else {
                    int[] depend = null; // Seriously?! WTF is with this format swapping names and datatypes?!
                    if (qTag.hasKey("dependencies", 11))
                        depend = qTag.getIntArray("dependencies");
                    if (qTag.hasKey("dependency", 3))
                        depend = new int[]{qTag.getInteger("dependency")};
                    if (depend != null && depend.length > 0) {
                        depKeys = new String[depend.length];
                        for (int d = 0; d < depend.length; d++) {
                            depKeys[d] = String.format("%1$08x", depend[d]);
                        }
                    }
                }

                if (depKeys != null) {
                    boolean hideDependencies = false;
                    if (qTag.hasKey("hide_dependency_lines")) {
                        if (qTag.getBoolean("hide_dependency_lines"))
                            hideDependencies = true;
                    }

                    if (qTag.hasKey("dependency_requirement")) {
                        switch (qTag.getString("dependency_requirement")){
                            case "all_completed":{
                                quest.setProperty(NativeProps.LOGIC_QUEST, EnumLogic.AND);
                                break;
                            }
                            case "one_completed":{
                                quest.setProperty(NativeProps.LOGIC_QUEST, EnumLogic.OR);
                                break;
                            }
                            // BetterQuesting has no "started" options.
                            case "all_started":{
                                quest.setProperty(NativeProps.LOGIC_QUEST, EnumLogic.AND);
                                break;
                            }
                            case "one_started":{
                                quest.setProperty(NativeProps.LOGIC_QUEST, EnumLogic.OR);
                                break;
                            }
                        }
                    }

                    parentMap.put(quest, Pair.of(depKeys, hideDependencies));
                }

                // === IMPORT TASKS ===

                NBTTagList taskList = qTag.getTagList("tasks", 10);
                for (int i = 0; i < taskList.tagCount(); i++) {
                    NBTTagCompound taskTag = taskList.getCompoundTagAt(i);
                    String tType = taskTag.getString("type");
                    if (!taskConverters.containsKey(tType)) {
                        BetterQuesting.logger.warn("Unsupported FTBQ task \"" + tType + "\"! Skipping...");
                        continue;
                    }

                    ITask[] tsks = taskConverters.get(tType).apply(taskTag);

                    if (tsks != null && tsks.length > 0) {
                        IDatabaseNBT<ITask, NBTTagList, NBTTagList> taskReg = quest.getTasks();
                        for (ITask t : tsks) {
                            taskReg.add(taskReg.nextID(), t);
                        }
                    }
                }
                if (quest.getProperty(NativeProps.NAME).equals(hexID)) {
                    quest.getTasks().getEntries().stream().findFirst().flatMap(entry -> Optional.ofNullable(entry.getValue().getTextForSearch())).flatMap(list -> list.stream().findFirst()).ifPresent(text -> quest.setProperty(NativeProps.NAME, text));
                }

                // === IMPORT REWARDS ===

                NBTTagList rewardList = qTag.getTagList("rewards", 10);
                for (int i = 0; i < rewardList.tagCount(); i++) {
                    NBTTagCompound rewTag = rewardList.getCompoundTagAt(i);
                    String rType = rewTag.getString("type");
                    if (!rewardConverters.containsKey(rType)) {
                        BetterQuesting.logger.warn("Unsupported FTBQ reward \"" + rType + "\"! Skipping...");
                        continue;
                    }

                    IReward[] tsks = rewardConverters.get(rType).apply(rewTag);

                    if (tsks != null && tsks.length > 0) {
                        IDatabaseNBT<IReward, NBTTagList, NBTTagList> rewardReg = quest.getRewards();
                        for (IReward t : tsks) {
                            rewardReg.add(rewardReg.nextID(), t);
                        }
                    }
                }

                if (qTag.hasKey("or_tasks")) {
                    if (qTag.getBoolean("or_tasks"))
                        quest.setProperty(NativeProps.LOGIC_TASK, EnumLogic.OR);
                }

                if (iconQuest != null) {
                    iconQuest.setProperty(NativeProps.ICON, new BigItemStack(Items.NETHER_STAR));
                    iconQuest = null;
                }
            }

            if (iconChapter != null) {
                iconChapter.setProperty(NativeProps.ICON, new BigItemStack(ItemStack.EMPTY));
                iconChapter = null;
            }
        }

        // === PARENTING SET ===

        for (Entry<IQuest, Pair<String[], Boolean>> entry : parentMap.entrySet()) {
            List<Integer> qIDs = new ArrayList<>();

            for (String key : entry.getValue().getLeft()) {
                FTBEntry type = ID_MAP.get(key);

                if (type == null) {
                    BetterQuesting.logger.warn("Unable to find quest dependency '" + key + "'");
                    continue;
                } else if (type.type == FTBEntryType.VAR)
                    continue;

                if (type.type == FTBEntryType.QUEST) {
                    qIDs.add(questDB.getID((IQuest) type.obj));
                } else if (type.type == FTBEntryType.LINE) {
                    for (DBEntry<IQuestLineEntry> qle : ((IQuestLine) type.obj).getEntries()) {
                        qIDs.add(qle.getID());
                    }
                }
            }

            int[] preReq = new int[qIDs.size()];
            for (int i = 0; i < qIDs.size(); i++) {
                preReq[i] = qIDs.get(i);
            }
            entry.getKey().setRequirements(preReq);
            if (entry.getValue().getRight()) {
                for (int req : preReq) {
                    entry.getKey().setRequirementType(req, RequirementType.IMPLICIT);
                }
            }
        }
    }

    private static IQuest iconQuest;
    private static IQuestLine iconChapter;

    private static void requestQuestIcon(IQuest quest) {
        iconQuest = quest;
    }

    private static void requestChapterIcon(IQuestLine chapter) {
        iconChapter = chapter;
    }

    public static void provideQuestIcon(BigItemStack stack) {
        if (stack == null)
            return;

        if (iconQuest != null) {
            iconQuest.setProperty(NativeProps.ICON, stack);
            iconQuest = null; // Request fufilled
        }

        if (iconChapter != null) {
            iconChapter.setProperty(NativeProps.ICON, stack);
            iconChapter = null; // Request fufilled
        }
    }

    public static void provideChapterIcon(BigItemStack stack) {
        if (iconChapter != null && stack != null) {
            iconChapter.setProperty(NativeProps.ICON, stack);
            iconChapter = null; // Request fufilled
        }
    }

    static {
        taskConverters.put("item", new FtbqTaskItem()::convertTask);
        taskConverters.put("fluid", new FtbqTaskFluid()::convertTask);
        taskConverters.put("forge_energy", new FtbqTaskEnergy()::converTask);
        taskConverters.put("xp", new FtbqTaskXP()::convertTask);
        taskConverters.put("dimension", new FtbqTaskDimension()::converTask);
        taskConverters.put("stat", new FtbqTaskStat()::convertTask);
        taskConverters.put("kill", new FtbqTaskKill()::convertTask);
        taskConverters.put("location", new FtbqTaskLocation()::convertTask);
        taskConverters.put("checkmark", tag -> new ITask[]{new TaskCheckbox()});
        taskConverters.put("advancement", new FtbqTaskAdvancement()::converTask);

        rewardConverters.put("item", new FtbqRewardItem()::convertTask);
        rewardConverters.put("xp", new FtbqRewardXP(false)::convertTask);
        rewardConverters.put("xp_levels", new FtbqRewardXP(true)::convertTask);
        rewardConverters.put("command", new FtbqRewardCommand()::convertReward);
    }
}
