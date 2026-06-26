package ru.lionzxy.tplauncher.utils

import sk.tomsik68.mclauncher.api.ui.IProgressMonitor

class EmptyMonitoring : IProgressMonitor {
    override fun setMax(len: Int) {

    }

    override fun setProgress(progress: Int) {
    }

    override fun setStatus(status: String?) {
    }

    override fun incrementProgress(amount: Int) {
    }
}