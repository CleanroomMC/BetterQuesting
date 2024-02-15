package betterquesting.client.gui;

import betterquesting.core.ModReference;
import betterquesting.handlers.ConfigHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiBQConfig extends GuiConfig {
    public GuiBQConfig(GuiScreen parent) {
        super(parent, getCategories(ConfigHandler.config), ModReference.MODID, false, false, ModReference.NAME);
    }

    private static List<IConfigElement> getCategories(Configuration config) {
        List<IConfigElement> cats = new ArrayList<>();
        config.getCategoryNames().forEach((s) -> cats.add(new ConfigElement(config.getCategory(s))));
        return cats;
    }
}
