package ru.lionzxy.tplauncher.view.main

import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import ru.lionzxy.tplauncher.utils.runOnUi
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor

class ProgressDelegate : IProgressMonitor {
    var progressBar: ProgressBar? = null
    var progressText: Label? = null
    private var max: Int = 1
    private var currentProgress = 0


    override fun setMax(max: Int) {
        this.max = max
    }

    override fun setProgress(progress: Int) {
        currentProgress = progress
        notifyAboutProgress(currentProgress)
    }

    override fun setStatus(status: String?) {
        if (status == null) {
            return
        }
        notifyAboutStatue(status)
    }

    override fun incrementProgress(diff: Int) {
        setProgress(currentProgress + diff)
    }

    private fun notifyAboutProgress(progress: Int) {
        val currentMax = max.toDouble()
        runOnUi {
            if (progress == -1) {
                progressBar?.progress = ProgressBar.INDETERMINATE_PROGRESS
                return@runOnUi
            }
            progressBar?.progress = progress.toDouble() / currentMax
        }
    }

    private fun notifyAboutStatue(status: String) {
        runOnUi {
            progressText?.text = status
        }
    }
}