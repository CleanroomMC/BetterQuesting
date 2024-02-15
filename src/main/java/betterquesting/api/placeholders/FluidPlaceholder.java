package betterquesting.api.placeholders;

import betterquesting.core.ModReference;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

public class FluidPlaceholder extends Fluid {
    public static Fluid fluidPlaceholder = new FluidPlaceholder();

    public FluidPlaceholder() {
        super(ModReference.MODID + ".placeholder", new ResourceLocation(ModReference.MODID, "blocks/fluid_placeholder"), new ResourceLocation(ModReference.MODID, "blocks/fluid_placeholder"));
    }
}
