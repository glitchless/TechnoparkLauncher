package ru.lionzxy.tplauncher.utils

import sk.tomsik68.mclauncher.api.ui.IProgressMonitor

class DebugMonitoring : IProgressMonitor {
    private var currentProgress = 0
    private var maxLen = 0

    override fun setProgress(progress: Int) {
        currentProgress = progress
        println("#setProgress $progress/$maxLen")
    }

    override fun setMax(len: Int) {
        println("#max $len")
        maxLen = len
    }

    override fun incrementProgress(amount: Int) {
        setProgress(currentProgress + amount)
    }

    override fun setStatus(status: String?) {
        println("#setStatus $status")
    }

}
