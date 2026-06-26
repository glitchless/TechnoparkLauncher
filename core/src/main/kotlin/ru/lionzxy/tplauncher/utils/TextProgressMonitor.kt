package ru.lionzxy.tplauncher.utils

import sk.tomsik68.mclauncher.api.ui.IProgressMonitor

class TextProgressMonitor(val format: String, val delegate: IProgressMonitor) : IProgressMonitor {
    var maxLen = 1
    var currentProgress = 1

    override fun setMax(len: Int) {
        this.maxLen = len
        delegate.setMax(len)
        invalidate()
    }

    override fun setProgress(progress: Int) {
        currentProgress = progress
        delegate.setProgress(progress)
        invalidate()
    }

    private fun invalidate() {
        if (maxLen < 1) {
            return
        }
        val proc = String.format("%.1f", (currentProgress.toFloat() / maxLen) * 100) + "%"
        val status = String.format(format, proc)
        setStatus(status)
    }

    override fun setStatus(status: String?) {
        delegate.setStatus(status)
    }

    override fun incrementProgress(amount: Int) {
        setProgress(currentProgress + amount)
    }

}
