package ru.lionzxy.tplauncher.utils

import ru.lionzxy.tplauncher.log.Logger
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor

class DebugMonitoring : IProgressMonitor {
    private var currentProgress = 0
    private var maxLen = 0

    override fun setProgress(progress: Int) {
        currentProgress = progress
        Logger.d("Monitor", "progress $progress/$maxLen")
    }

    override fun setMax(len: Int) {
        Logger.d("Monitor", "max $len")
        maxLen = len
    }

    override fun incrementProgress(amount: Int) {
        setProgress(currentProgress + amount)
    }

    override fun setStatus(status: String?) {
        Logger.d("Monitor", "status $status")
    }

}
