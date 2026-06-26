package ru.lionzxy.tplauncher.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.lionzxy.tplauncher.ui.state.ProgressUiState
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor

class ProgressMonitorBridge(private val flow: MutableStateFlow<ProgressUiState>) : IProgressMonitor {
    private var max = 1
    private var cur = 0
    override fun setMax(len: Int) { max = if (len <= 0) 1 else len }
    override fun setProgress(progress: Int) { cur = progress; emit() }
    override fun incrementProgress(amount: Int) { setProgress(cur + amount) }
    override fun setStatus(status: String?) { if (status != null) flow.update { it.copy(status = status) } } // null = no-op
    private fun emit() = flow.update { it.copy(value = if (cur == -1) -1f else cur.toFloat() / max) }
}
