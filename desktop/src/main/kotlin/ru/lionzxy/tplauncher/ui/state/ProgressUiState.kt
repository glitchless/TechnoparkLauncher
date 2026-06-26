package ru.lionzxy.tplauncher.ui.state

/**
 * Transient download/launch progress published imperatively by the ViewModel.
 * Not derived from [LauncherState] — updated separately via MutableState.
 */
data class ProgressUiState(
    val status: String? = null,
    val value: Float = 0f,
)
