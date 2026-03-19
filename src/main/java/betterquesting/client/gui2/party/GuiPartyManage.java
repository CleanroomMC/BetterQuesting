package betterquesting.client.gui2.party;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.enums.EnumPartyStatus;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.party.IParty;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import betterquesting.api2.client.gui.controls.PanelTextField;
import betterquesting.api2.client.gui.controls.filters.FieldFilterString;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.*;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelLine;
import betterquesting.api2.client.gui.panels.content.PanelPlayerPortrait;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.resources.textures.ItemTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.client.gui.themes.presets.PresetLine;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.core.BetterQuesting;
import betterquesting.network.handlers.NetPartyAction;
import betterquesting.questing.party.PartyManager;
import betterquesting.storage.LifeDatabase;
import betterquesting.storage.NameCache;
import betterquesting.storage.QuestSettings;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Keyboard;

import java.util.*;
import java.util.stream.Collectors;

public class GuiPartyManage extends GuiScreenCanvas implements IPEventListener, INeedsRefresh {
    private IParty party;
    private int partyID = -1;
    private PanelTextField<String> flName;
    private PanelVScrollBar scUserList;

    public GuiPartyManage(GuiScreen parent) {
        super(parent);
    }

    // 0 = INVITE, 1 = MEMBER, 2 = ADMIN, 3 = OWNER/OP
    private EnumPartyStatus getStatus(UUID id) {
        if (NameCache.INSTANCE.isOP(id)) return EnumPartyStatus.OWNER;
        EnumPartyStatus perm = party.getStatus(id);
        return perm == null ? EnumPartyStatus.MEMBER : perm; // Fallback (potentially exploitable I know)
    }

    @Override
    public void refreshGui() {
        UUID playerID = QuestingAPI.getQuestingUUID(mc.player);

        DBEntry<IParty> tmp = PartyManager.INSTANCE.getParty(playerID);

        if (tmp == null) {
            mc.displayGuiScreen(new GuiPartyCreate(parent));
            return;
        }

        party = tmp.getValue();
        partyID = tmp.getID();

        if (!flName.isFocused()) flName.setText(party.getProperties().getProperty(NativeProps.NAME));

        initPanel();
    }

    @Override
    public void initPanel() {
        super.initPanel();

        UUID playerID = QuestingAPI.getQuestingUUID(mc.player);

        DBEntry<IParty> tmp = PartyManager.INSTANCE.getParty(playerID);

        if (tmp == null) {
            mc.displayGuiScreen(new GuiPartyCreate(parent));
            return;
        }

        party = tmp.getValue();
        partyID = tmp.getID();

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);
        Keyboard.enableRepeatEvents(true);

        EnumPartyStatus status = getStatus(playerID);

        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0), PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        cvBackground.addPanel(new PanelButton(new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0), 0, QuestTranslation.translate("gui.back")));

        PanelTextBox txTitle = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 0, -32), 0), QuestTranslation.translate("betterquesting.title.party", party.getProperties().getProperty(NativeProps.NAME))).setAlignment(1);
        txTitle.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(txTitle);

        // Left side

        CanvasEmpty cvLeftHalf = new CanvasEmpty(new GuiTransform(GuiAlign.HALF_LEFT, new GuiPadding(16, 64, 8, 64), 0));
        cvBackground.addPanel(cvLeftHalf);

        PanelButtonStorage<String> btnLeave = new PanelButtonStorage<>(new GuiTransform(GuiAlign.MID_CENTER, -75, 32, 70, 16, 0), 3, QuestTranslation.translate("betterquesting.btn.party_leave"), mc.player.getGameProfile().getName());
        cvLeftHalf.addPanel(btnLeave);

        PanelButton btnInvite = new PanelButton(new GuiTransform(GuiAlign.MID_CENTER, 5, 32, 70, 16, 0), 2, QuestTranslation.translate("betterquesting.btn.party_invite"));
        cvLeftHalf.addPanel(btnInvite);
        btnInvite.setActive(status.ordinal() >= EnumPartyStatus.ADMIN.ordinal());

        if (flName == null)
            flName = new PanelTextField<>(new GuiTransform(GuiAlign.MID_CENTER, -75, -32, 134, 16, 0), party.getProperties().getProperty(NativeProps.NAME), FieldFilterString.INSTANCE);
        cvLeftHalf.addPanel(flName);
        flName.setActive(status.ordinal() >= EnumPartyStatus.OWNER.ordinal());

        PanelButton btnSetName = new PanelButton(new GuiTransform(GuiAlign.RIGHT_EDGE, 0, 0, 16, 16, 0), 4, "");
        cvLeftHalf.addPanel(btnSetName);
        btnSetName.getTransform().setParent(flName.getTransform());
        btnSetName.setIcon(PresetIcon.ICON_REFRESH.getTexture());
        btnSetName.setActive(status.ordinal() >= EnumPartyStatus.OWNER.ordinal());

        PanelTextBox txName = new PanelTextBox(new GuiTransform(GuiAlign.MID_CENTER, -75, -48, 134, 16, 0), QuestTranslation.translate("betterquesting.gui.name"));
        txName.setColor(PresetColor.TEXT_HEADER.getColor());
        cvLeftHalf.addPanel(txName);

        // Right side

        CanvasEmpty cvRightHalf = new CanvasEmpty(new GuiTransform(GuiAlign.HALF_RIGHT, new GuiPadding(8, 32, 16, 32), 0));
        cvBackground.addPanel(cvRightHalf);

        CanvasScrolling cvUserList = new CanvasScrolling(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 16, 8, 0), 0));
        cvRightHalf.addPanel(cvUserList);

        if (scUserList == null)
            scUserList = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(0, 0, -8, 0), 0));
        cvRightHalf.addPanel(scUserList);
        scUserList.getTransform().setParent(cvUserList.getTransform());
        cvUserList.setScrollDriverY(scUserList);

        int elSize = RenderUtils.getStringWidth("...", fontRenderer);
        int cvWidth = cvUserList.getTransform().getWidth();
        boolean hardcore = QuestSettings.INSTANCE.getProperty(NativeProps.HARDCORE);
        ItemTexture txHeart = new ItemTexture(new BigItemStack(BetterQuesting.extraLife));

        int heightOffset = 0;

        // Iterate through the statuses in descending order
        for (EnumPartyStatus statusEntry : Arrays.asList(EnumPartyStatus.OWNER, EnumPartyStatus.ADMIN, EnumPartyStatus.MEMBER)) {
            List<UUID> members = party.getMembers().stream().filter(id -> getStatus(id).equals(statusEntry)).collect(Collectors.toList());

            if (members.isEmpty()) continue;

            String statusNameKey = switch (statusEntry) {
                case OWNER -> "betterquesting.gui.party_owner";
                case ADMIN -> "betterquesting.gui.party_admins";
                case MEMBER -> "betterquesting.gui.party_members";
            };

            PanelTextBox txStatusName = new PanelTextBox(new GuiRectangle(0, heightOffset, cvWidth, 16, 0), QuestTranslation.translate(statusNameKey)).setAlignment(1);
            txStatusName.setColor(PresetColor.TEXT_HEADER.getColor());
            cvUserList.addPanel(txStatusName);

            heightOffset += 16;

            for (UUID id : members) {
                String username = NameCache.INSTANCE.getName(id);
                boolean displayKickButton = statusEntry.ordinal() < status.ordinal() && playerID != id;
                boolean displayAdminButton = displayKickButton && status == EnumPartyStatus.OWNER;

                PanelPlayerPortrait pnPortrait = new PanelPlayerPortrait(new GuiRectangle(0, heightOffset, 32, 32, 0), id, username);
                cvUserList.addPanel(pnPortrait);

                int maxAllowedWidth = cvWidth - 32;
                if (displayKickButton) maxAllowedWidth -= 32;
                if (displayAdminButton) maxAllowedWidth -= 48;

                String shortenedName = RenderUtils.getStringWidth(username, fontRenderer) > maxAllowedWidth ? fontRenderer.trimStringToWidth(username, maxAllowedWidth - elSize) + "..." : username;
                PanelTextBox txMemName = new PanelTextBox(new GuiRectangle(32, heightOffset + 4, cvWidth - 32, 12, 0), shortenedName);
                txMemName.setColor(PresetColor.TEXT_MAIN.getColor());
                cvUserList.addPanel(txMemName);

                if (displayKickButton) {
                    PanelButtonStorage<String> btnKick = new PanelButtonStorage<>(new GuiRectangle(cvWidth - 32, heightOffset, 32, 32, 0), 3, QuestTranslation.translate("betterquesting.btn.party_kick"), username);
                    cvUserList.addPanel(btnKick);

                    if (displayAdminButton) {
                        var rect = new GuiRectangle(cvWidth - 80, heightOffset, 48, 32, 0);
                        if (statusEntry == EnumPartyStatus.MEMBER) {
                            cvUserList.addPanel(new PanelButtonStorage<>(rect, 5, QuestTranslation.translate("betterquesting.btn.party_promote"), username));
                        } else {
                            cvUserList.addPanel(new PanelButtonStorage<>(rect, 6, QuestTranslation.translate("betterquesting.btn.party_demote"), username));
                        }
                    }
                }

                PanelGeneric pnItem = new PanelGeneric(new GuiRectangle(32, heightOffset + 16, 16, 16, 0), txHeart);
                cvUserList.addPanel(pnItem);

                PanelTextBox txLives = new PanelTextBox(new GuiRectangle(48, heightOffset + 20, cvWidth - 48 - 32, 12, 0), " x " + (hardcore ? LifeDatabase.INSTANCE.getLives(id) : "∞"));
                txLives.setColor(PresetColor.TEXT_MAIN.getColor());
                cvUserList.addPanel(txLives);

                heightOffset += 32;
            }
        }

        scUserList.setActive(cvUserList.getScrollBounds().getHeight() > 0);

        // Divider

        IGuiRect ls0 = new GuiTransform(GuiAlign.TOP_CENTER, 0, 32, 0, 0, 0);
        ls0.setParent(cvBackground.getTransform());
        IGuiRect le0 = new GuiTransform(GuiAlign.BOTTOM_CENTER, 0, -32, 0, 0, 0);
        le0.setParent(cvBackground.getTransform());
        PanelLine paLine0 = new PanelLine(ls0, le0, PresetLine.GUI_DIVIDER.getLine(), 1, PresetColor.GUI_DIVIDER.getColor(), 1);
        cvBackground.addPanel(paLine0);
    }

    @Override
    public void onPanelEvent(PanelEvent event) {
        if (event instanceof PEventButton) {
            onButtonPress((PEventButton) event);
        }
    }

    @SuppressWarnings("unchecked")
    private void onButtonPress(PEventButton event) {
        IPanelButton btn = event.getButton();

        if (btn.getButtonID() == 0) // Exit
        {
            mc.displayGuiScreen(this.parent);
        } else if (btn.getButtonID() == 2) // Invite
        {
            mc.displayGuiScreen(new GuiPartyInvite(this));
        } else if (btn.getButtonID() == 3 && btn instanceof PanelButtonStorage) // Kick/Leave
        {
            String id = ((PanelButtonStorage<String>) btn).getStoredValue();
            NBTTagCompound payload = new NBTTagCompound();
            payload.setInteger("action", 5);
            payload.setInteger("partyID", partyID);
            payload.setString("username", id);
            NetPartyAction.sendAction(payload);
        } else if (btn.getButtonID() == 4) // Change name
        {
            party.getProperties().setProperty(NativeProps.NAME, flName.getRawText());
            NBTTagCompound payload = new NBTTagCompound();
            payload.setInteger("action", 2);
            payload.setInteger("partyID", partyID);
            payload.setTag("data", party.writeProperties(new NBTTagCompound()));
            NetPartyAction.sendAction(payload);
        } else if (btn.getButtonID() == 5 && btn instanceof PanelButtonStorage) // Toggle permission level of user between ADMIN and MEMBER
        {
            String id = ((PanelButtonStorage<String>) btn).getStoredValue();
            NBTTagCompound payload = new NBTTagCompound();
            payload.setInteger("action", 6);
            payload.setInteger("partyID", partyID);
            payload.setString("username", id);
            NetPartyAction.sendAction(payload);
        } else if (btn.getButtonID() == 6 && btn instanceof PanelButtonStorage) // Toggle permission level of user between ADMIN and MEMBER
        {
            String id = ((PanelButtonStorage<String>) btn).getStoredValue();
            NBTTagCompound payload = new NBTTagCompound();
            payload.setInteger("action", 7);
            payload.setInteger("partyID", partyID);
            payload.setString("username", id);
            NetPartyAction.sendAction(payload);
        }
    }
}
