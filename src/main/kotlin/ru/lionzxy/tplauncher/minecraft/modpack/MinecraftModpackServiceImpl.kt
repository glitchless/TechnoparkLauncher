package ru.lionzxy.tplauncher.minecraft.modpack

import com.squareup.anvil.annotations.ContributesBinding
import ru.lionzxy.tplauncher.di.AppScope
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class MinecraftModpackServiceImpl @Inject constructor() : MinecraftModpackService {
    override fun getCurrentModpack(): MinecraftModpack {
        TODO("Not yet implemented")
    }

    override fun setCurrentModpack(modpack: MinecraftModpack) {
        TODO("Not yet implemented")
    }

}
