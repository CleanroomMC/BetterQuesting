package betterquesting.client.gui2.editors;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument.DefaultDocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.core.BetterQuesting;
import betterquesting.core.ModReference;
import betterquesting.questing.QuestDatabase;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

public class TextEditorFrame extends JFrame {

    private static final int initialRowCount = 30;
    private static final int defaultColumns = 60;
    private static BufferedImage logoCache = null;

    // This is NOT a cache.
    // This contains windows that is open.
    // This makes it possible to show and remain multiple editor windows.
    // questID -> TextEditorFrame
    private static final IntObjectMap<TextEditorFrame> open = new IntObjectHashMap<>();

    public static @Nullable TextEditorFrame get(int questID) {
        return open.get(questID);
    }

    public static TextEditorFrame getOrCreate(int questID, String title, String name, String description) {
        if (open.containsKey(questID))
            return open.get(questID);
        TextEditorFrame frame = new TextEditorFrame(questID, title, name, description);
        open.put(questID, frame);
        return frame;
    }

    private final int questID;
    private final JTextField nameText;
    private final JButton close;
    private final JTextArea descText;

    private @Nullable GuiQuestDescEditor gui = null;

    private boolean valueChangedByGuiScreen = false;
    private boolean byTopCornerCloseButton = true;

    private TextEditorFrame(int questID, String title, String name, String description) {
        super("Better Questing Text Editor | " + title);
        this.questID = questID;

        initLogoCache();

        if (logoCache != null)
            setIconImage(logoCache);

        setResizable(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                if (byTopCornerCloseButton) {
                    if (gui == null) {
                        cancel();
                    } else {
                        gui.cancel();
                    }
                } else {
                    open.remove(questID);
                    setVisible(false);
                    dispose();
                }
            }

        });

        JPanel wholePanel = new JPanel();
        wholePanel.setLayout(new BoxLayout(wholePanel, BoxLayout.Y_AXIS));

        JPanel editorPanel = add(wholePanel, new JPanel());
        editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.X_AXIS));

        JPanel buttonPanel = add(editorPanel, new JPanel());
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(new TitledBorder(BorderFactory.createLineBorder(Color.GRAY, 1, true),
                                               QuestTranslation.translate("betterquesting.gui.formatting_buttons")));
        addAllFormattingCodes(buttonPanel);

        JPanel textPanel = add(editorPanel, new JPanel());
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        nameText = add(textPanel, new JTextField(name, defaultColumns));
        nameText.setBorder(new TitledBorder(BorderFactory.createLineBorder(Color.GRAY, 1, true), QuestTranslation.translate("betterquesting.gui.name")));
        nameText.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        nameText.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (gui == null)
                    return;
                if (valueChangedByGuiScreen)
                    return;
                gui.removeName(e.getOffset(), e.getLength());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                if (gui == null)
                    return;
                if (valueChangedByGuiScreen)
                    return;
                Document doc = e.getDocument();
                String text;
                try {
                    text = doc.getText(e.getOffset(), e.getLength());
                } catch (BadLocationException ex) {
                    return;
                }
                gui.insertName(e.getOffset(), text);
            }

            @Override
            public void changedUpdate(DocumentEvent arg0) {

            }

        });
        UndoHelper.addUndoHelper(nameText);

        descText = new JTextArea(description, initialRowCount, defaultColumns);
        JScrollPane scroll = add(textPanel, new JScrollPane(descText, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
        scroll.setRowHeaderView(new LineNumberView(descText));
        descText.setBorder(new TitledBorder(BorderFactory.createLineBorder(Color.GRAY, 1, true), QuestTranslation.translate("betterquesting.gui.description")));
        descText.setLineWrap(true);
        descText.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (gui == null)
                    return;
                if (valueChangedByGuiScreen)
                    return;
                gui.removeDesc(e.getOffset(), e.getLength());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                if (gui == null)
                    return;
                if (valueChangedByGuiScreen)
                    return;
                Document doc = e.getDocument();
                String text;
                try {
                    text = doc.getText(e.getOffset(), e.getLength());
                } catch (BadLocationException ex) {
                    return;
                }
                gui.insertDesc(e.getOffset(), text);
            }

            @Override
            public void changedUpdate(DocumentEvent arg0) {

            }

        });
        UndoHelper.addUndoHelper(descText);

        JPanel footerPanel = add(wholePanel, new JPanel());
        footerPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));
        JButton cancel = add(footerPanel, new JButton(QuestTranslation.translate("gui.cancel")));
        cancel.setMinimumSize(new Dimension(90, 0));
        cancel.addActionListener(this::cancelClicked);
        close = add(footerPanel, new JButton(QuestTranslation.translate("betterquesting.btn.edit_name_desc.just_close")));
        close.setMinimumSize(new Dimension(90, 0));
        close.addActionListener(this::closeClicked);
        close.setToolTipText(QuestTranslation.translate("betterquesting.tooltip.edit_name_desc.just_close_window"));
        JButton done = add(footerPanel, new JButton(QuestTranslation.translate("gui.done")));
        done.addActionListener(this::doneClicked);
        done.setMinimumSize(new Dimension(90, 0));

        setContentPane(wholePanel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void setGui(@Nullable GuiQuestDescEditor gui) {
        this.gui = gui;
        close.setEnabled(gui != null);
    }

    /**
     * Close the window.
     */
    public void close() {
        byTopCornerCloseButton = false;
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        byTopCornerCloseButton = true;
    }

    public String getName() { return nameText.getText(); }

    public String getDesc() { return descText.getText(); }

    /**
     * Set the quest description.
     *
     * @param desc The description.
     */
    public void setDesc(String desc) {
        SwingUtilities.invokeLater(() -> {
            valueChangedByGuiScreen = true;
            int caretPos = descText.getCaretPosition();
            descText.setText(desc);
            descText.setCaretPosition(caretPos);
            valueChangedByGuiScreen = false;
        });
    }

    private void cancelClicked(ActionEvent event) {
        if (gui != null) {
            gui.cancel();
        } else {
            cancel();
        }
    }

    private void closeClicked(ActionEvent event) {
        if (gui != null) {
            gui.removeWindow();
        } else {
            close();
        }
    }

    private void doneClicked(ActionEvent event) {
        if (gui != null) {
            gui.saveAndClose();
        } else {
            saveAndClose();
        }
    }

    private void cancel() {
        close();
    }

    private void saveAndClose() {
        IQuest quest = QuestDatabase.INSTANCE.getValue(questID);
        quest.setProperty(NativeProps.NAME, getName().trim());
        quest.setProperty(NativeProps.DESC, getDesc());
        GuiQuestEditor.sendChanges(questID);
        close();
    }

    private void addAllFormattingCodes(JPanel panel) {
        for (TextFormatting value : TextFormatting.values()) {
            JButton btn = new JButton(value.getFriendlyName());
            btn.setToolTipText(value.toString());
            btn.addActionListener(addFormattingCode(value));
            btn.setMinimumSize(new Dimension(80, 20));
            btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            add(panel, btn);
        }
    }

    private ActionListener addFormattingCode(TextFormatting textFormatting) {
        return event -> {
            descText.insert(textFormatting.toString(), descText.getCaretPosition());
        };
    }

    private static void initLogoCache() {
        if (logoCache == null) {
            try (InputStream stream = Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(new ResourceLocation(ModReference.MODID, "textures/items/quest_book.png"))
                    .getInputStream()) {
                logoCache = ImageIO.read(stream);
            } catch (Exception ex) {
                BetterQuesting.logger.error(ex);
            }
        }
    }

    private static <T extends Component> T add(JPanel panel, T component) {
        panel.add(component);
        return component;
    }

    private static class UndoHelper {

        public static final String ACTION_KEY_UNDO = "undo";
        public static final String ACTION_KEY_REDO = "redo";
        private final UndoManager undoManager = new UndoManager();

        public static void addUndoHelper(JTextComponent textComponent) {
            new UndoHelper(textComponent);
        }

        private UndoHelper(JTextComponent textComponent) {
            ActionMap amap = textComponent.getActionMap();
            InputMap imap = textComponent.getInputMap();
            if (amap.get(ACTION_KEY_UNDO) == null) {
                UndoAction undoAction = new UndoAction();
                amap.put(ACTION_KEY_UNDO, undoAction);
                imap.put((KeyStroke) undoAction.getValue(Action.ACCELERATOR_KEY), ACTION_KEY_UNDO);
            }
            if (amap.get(ACTION_KEY_REDO) == null) {
                RedoAction redoAction = new RedoAction();
                amap.put(ACTION_KEY_REDO, redoAction);
                imap.put((KeyStroke) redoAction.getValue(Action.ACCELERATOR_KEY), ACTION_KEY_REDO);
            }
            textComponent.getDocument().addDocumentListener(new DocListener());
        }

        class UndoAction extends AbstractAction {

            UndoAction() {
                super("Undo(U)");
                putValue(MNEMONIC_KEY, (int) 'U');
                putValue(SHORT_DESCRIPTION, "Undo");
                putValue(LONG_DESCRIPTION, "Undo");
                putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke('Z', InputEvent.CTRL_DOWN_MASK));
            }

            public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            }

        }

        class RedoAction extends AbstractAction {

            RedoAction() {
                super("Redo(R)");
                putValue(MNEMONIC_KEY, (int) 'R');
                putValue(SHORT_DESCRIPTION, "Redo");
                putValue(LONG_DESCRIPTION, "Redo");
                putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke('Y', InputEvent.CTRL_DOWN_MASK));
            }

            public void actionPerformed(ActionEvent e) {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                }
            }

        }

        private class DocListener implements DocumentListener {

            public void insertUpdate(DocumentEvent e) {
                if (e instanceof DefaultDocumentEvent de) {
                    undoManager.addEdit(de);
                }
            }

            public void removeUpdate(DocumentEvent e) {
                if (e instanceof DefaultDocumentEvent de) {
                    undoManager.addEdit(de);
                }
            }

            public void changedUpdate(DocumentEvent e) {
                // Nothing to do.
            }

        }

    }

    private static class LineNumberView extends JPanel {

        private static final int LINE_NUMBER_MARGIN = 5; // Left and right padding of the line numbers
        private final JTextArea textArea;

        public LineNumberView(JTextArea textArea) {
            this.textArea = textArea;
            textArea.getDocument().addDocumentListener(new DocumentListener() {

                @Override
                public void insertUpdate(DocumentEvent e) {
                    repaint();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    repaint();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    /* not needed */
                }

            });
            textArea.addComponentListener(new ComponentAdapter() {

                @Override
                public void componentResized(ComponentEvent e) {
                    revalidate();
                    repaint();
                }

            });
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setOpaque(true);
            EventQueue.invokeLater(() -> {
                Insets i = textArea.getMargin();
                setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.GRAY),
                                                             BorderFactory.createEmptyBorder(i.top, LINE_NUMBER_MARGIN, i.bottom, LINE_NUMBER_MARGIN - 1)));
                setBackground(textArea.getBackground());
            });
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fontMetrics = textArea.getFontMetrics(textArea.getFont());
            return new Dimension(getComponentWidth(fontMetrics), textArea.getHeight());
        }

        @Override
        protected void paintComponent(Graphics g) {

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(textArea.getBackground());
                Rectangle clip = g2.getClipBounds();
                g2.fillRect(clip.x, clip.y, clip.width, clip.height);

                Font font = textArea.getFont();
                g2.setFont(font);

                FontMetrics fontMetrics = g2.getFontMetrics(font);
                int fontAscent = fontMetrics.getAscent();
                int fontDescent = fontMetrics.getDescent();
                int fontLeading = fontMetrics.getLeading();
                int fontHeight = fontMetrics.getHeight();

                int componentWidth = getComponentWidth(fontMetrics);
                int rMargin = getInsets().right;
                int base = clip.y;
                int start = getLineAtPoint(base);
                int end = getLineAtPoint(base + clip.height);
                int y = textArea.getBorder().getBorderInsets(textArea).top;
                y += start * fontHeight;

                g2.setColor(getForeground());
                for (int line = start; line <= end;) {
                    y += fontAscent;
                    // Skip auto-wrapped lines
                    if (line <= getLineAtPoint(y)) {
                        String text = Integer.toString(line + 1);
                        // right alignment
                        int x = componentWidth - rMargin - fontMetrics.stringWidth(text);
                        g2.drawString(text, x, y);
                        line++;
                    }
                    y += fontDescent + fontLeading;
                }
            } finally {
                g2.dispose();
            }
        }

        private int getLineAtPoint(int y) {
            Element root = textArea.getDocument().getDefaultRootElement();
            int pos = textArea.viewToModel(new Point(0, y));
            return root.getElementIndex(pos);
        }

        private int getComponentWidth(FontMetrics fontMetrics) {
            int maxDigits = Math.max(2, Integer.toString(textArea.getLineCount()).length());
            Insets insets = getInsets();
            return maxDigits * fontMetrics.stringWidth("0") + insets.left + insets.right;
        }

    }

}
