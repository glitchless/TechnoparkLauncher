package ru.lionzxy.tplauncher.di

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import ru.lionzxy.tplauncher.view.main.MainWindow
import javax.inject.Singleton

abstract class AppScope private constructor()

@ContributesTo(AppScope::class)
interface AppComponentInterface {
    fun inject(mainWindow: MainWindow)
}

@Singleton
@MergeComponent(AppScope::class)
interface AppComponent : AppComponentInterface
