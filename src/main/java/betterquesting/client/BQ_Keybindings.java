package betterquesting.client;

import betterquesting.core.ModReference;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

public class BQ_Keybindings {
    public static KeyBinding openQuests;
    public static KeyBinding backPage;

    public static void RegisterKeys() {
        openQuests = new KeyBinding("key.betterquesting.quests", Keyboard.KEY_GRAVE, ModReference.NAME);
        backPage = new KeyBinding("key.betterquesting.back", Keyboard.KEY_BACK, ModReference.NAME);

        ClientRegistry.registerKeyBinding(openQuests);
        ClientRegistry.registerKeyBinding(backPage);
    }
}
