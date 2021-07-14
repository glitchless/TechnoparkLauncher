package ru.lionzxy.tplauncher.services.modpack

import com.squareup.anvil.annotations.ContributesBinding
import ru.lionzxy.tplauncher.di.AppScope
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import ru.lionzxy.tplauncher.services.config.ConfigProvider
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class MinecraftModpackServiceImpl @Inject constructor(
    private val configProvider: ConfigProvider
) : MinecraftModpackService {
    override fun getCurrentModpack(): MinecraftModpack {
        return configProvider.config.currentModpack
    }

    override fun setCurrentModpack(modpack: MinecraftModpack) {
        configProvider.writeToConfig {
            currentModpack = modpack
        }
    }

}
