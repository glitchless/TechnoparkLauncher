package ru.lionzxy.tplauncher.config;


import kotlin.text.Regex;
import org.jetbrains.annotations.Nullable;
import ru.lionzxy.tplauncher.exceptions.HeapSizeInvalidException;

public class Settings {
    private String heapSize = null;
    private String customJavaParameter = null;
    private String commandPrefix = null;
    @Nullable
    private String javaLocation = null;
    private boolean isDebug = false;

    public Settings() {

    }

    public Settings(Settings settings) {
        this.heapSize = settings.heapSize;
        this.customJavaParameter = settings.customJavaParameter;
        this.commandPrefix = settings.commandPrefix;
        this.javaLocation = settings.javaLocation;
        this.isDebug = settings.isDebug;
    }

    public String getHeapSize() {
        if (heapSize == null) {
            heapSize = SettingsDefault.INSTANCE.getDefaultHeapSize();
        }
        return heapSize;
    }

    public void setHeapSize(String heapSize) {
        if (!new Regex("[0-9]*[G|g|M|m]").matches(heapSize)) {
            throw new HeapSizeInvalidException(heapSize);
        }
        this.heapSize = heapSize;
    }

    public String getCustomJavaParameter() {
        if (customJavaParameter == null) {
            customJavaParameter = SettingsDefault.INSTANCE.getDefaultJavaArguments();
        }
        return customJavaParameter;
    }

    public void setCustomJavaParameter(String customJavaParameter) {
        this.customJavaParameter = customJavaParameter;
    }

    public String getCommandPrefix() {
        if (commandPrefix == null) {
            commandPrefix = SettingsDefault.INSTANCE.getDefaultCommandPrefix();
        }
        return commandPrefix;
    }

    public void setCommandPrefix(String commandPrefix) {
        this.commandPrefix = commandPrefix;
    }

    @Nullable
    public String getJavaLocation() {
        if (javaLocation == null) {
            javaLocation = SettingsDefault.INSTANCE.getDefaultJavaLocation();
        }
        return javaLocation;
    }

    public void setJavaLocation(@Nullable String javaLocation) {
        this.javaLocation = javaLocation;
    }

    public boolean isDebug() {
        return isDebug;
    }

    public void setDebug(boolean debug) {
        isDebug = debug;
    }
}
