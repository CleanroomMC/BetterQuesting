package betterquesting.storage;

import betterquesting.api.properties.IPropertyContainer;
import betterquesting.api.properties.IPropertyListener;
import betterquesting.api.properties.IPropertyReducible;
import betterquesting.api.properties.IPropertyType;
import betterquesting.api2.storage.INBTSaveLoad;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PropertyContainer implements IPropertyContainer, INBTSaveLoad<NBTTagCompound> {
    private final NBTTagCompound nbtInfo = new NBTTagCompound();
    // For reducing nbt
    // To hold nbt values if the properties are not used (ex: the addon is temporarily removed), we cache and use only used properties to reduce nbt.
    private final BiMap<ResourceLocation, IPropertyType<?>> id2PropertyMap = HashBiMap.create(); // property.getKey() -> property
    @SuppressWarnings("UnstableApiUsage")
    private final Multimap<ResourceLocation, IPropertyListener<?>> propListeners = MultimapBuilder.hashKeys().arrayListValues().build();

    @Override
    public synchronized <T> T getProperty(IPropertyType<T> prop) {
        if (prop == null) return null;

        return getProperty(prop, prop.getDefault());
    }

    @Override
    public synchronized <T> T getProperty(IPropertyType<T> prop, T def) {
        if (prop == null) return def;

        id2PropertyMap.put(prop.getKey(), prop);
        NBTTagCompound jProp = getDomain(prop.getKey());

        if (!jProp.hasKey(prop.getKey().getPath())) return def;

        return prop.readValue(jProp.getTag(prop.getKey().getPath()));
    }

    @Override
    public synchronized boolean hasProperty(IPropertyType<?> prop) {
        if (prop == null) return false;
        id2PropertyMap.put(prop.getKey(), prop);
        return getDomain(prop.getKey()).hasKey(prop.getKey().getPath());
    }

    @Override
    public synchronized void removeProperty(IPropertyType<?> prop) {
        if (prop == null) return;
        id2PropertyMap.put(prop.getKey(), prop);
        NBTTagCompound jProp = getDomain(prop.getKey());

        if (!jProp.hasKey(prop.getKey().getPath())) return;

        jProp.removeTag(prop.getKey().getPath());

        if (jProp.isEmpty()) nbtInfo.removeTag(prop.getKey().getNamespace());
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized <T> void setProperty(IPropertyType<T> prop, T value) {
        if (prop == null || value == null) return;
        id2PropertyMap.put(prop.getKey(), prop);
        NBTTagCompound dom = getDomain(prop.getKey());

        if (propListeners.containsKey(prop.getKey())) {
            for (IPropertyListener<?> listener : propListeners.get(prop.getKey())) {
                ((IPropertyListener<T>) listener).propertyChanged(prop, value);
            }
        }

        dom.setTag(prop.getKey().getPath(), prop.writeValue(value));
        nbtInfo.setTag(prop.getKey().getNamespace(), dom);
    }

    @Override
    public synchronized void removeAllProps() {
        List<String> keys = new ArrayList<>(nbtInfo.getKeySet());
        for (String key : keys) nbtInfo.removeTag(key);
    }

    public synchronized <T> void addPropertyListener(IPropertyType<T> prop, IPropertyListener<T> listener) {
        propListeners.put(prop.getKey(), listener);
    }

    @Deprecated
    @Override
    public synchronized NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public synchronized NBTTagCompound writeToNBT(NBTTagCompound nbt, boolean reduce) {
        if (reduce) {
            NBTTagCompound reducedNbtInfo = new NBTTagCompound();
            for (String namespace : nbtInfo.getKeySet()) {
                NBTTagCompound dom = nbtInfo.getCompoundTag(namespace);
                NBTTagCompound reducedDom = new NBTTagCompound();
                for (String key : dom.getKeySet()) {
                    IPropertyType<?> prop = id2PropertyMap.get(new ResourceLocation(namespace, key));
                    NBTBase tag = dom.getTag(key);
                    if (prop != null && Objects.equals(prop.getDefault(), prop.readValue(tag))) continue;
                    if (prop instanceof IPropertyReducible<?> reducible) tag = reducible.reduceNBT(tag);
                    reducedDom.setTag(key, tag);
                }
                if (!reducedDom.isEmpty()) reducedNbtInfo.setTag(namespace, reducedDom);
            }
            nbt.merge(reducedNbtInfo);
        } else {
            nbt.merge(nbtInfo);
        }
        return nbt;
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound nbt) {
        removeAllProps();
        nbtInfo.merge(nbt);

        // TODO: FIX CASING <- ???
        /*List<String> keys = new ArrayList<>(nbtInfo.getKeySet());
        for(nbt)
        {

        }*/
    }

    private NBTTagCompound getDomain(ResourceLocation res) {
        return nbtInfo.getCompoundTag(res.getNamespace());
    }
}
