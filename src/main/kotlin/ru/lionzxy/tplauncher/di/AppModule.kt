package ru.lionzxy.tplauncher.di

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import ru.lionzxy.tplauncher.view.main.states.IImplementState
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import javax.inject.Singleton

@Module
@ContributesTo(AppScope::class)
class AppModule(
    private val stateMachine: IImplementState,
    private val progressMonitor: IProgressMonitor
) {
    @Singleton
    @Provides
    fun provideStateMachine() = stateMachine

    @Singleton
    @Provides
    fun provideProgressMonitor() = progressMonitor
}
