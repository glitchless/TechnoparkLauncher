package ru.lionzxy.tplauncher.downloader

import sk.tomsik68.mclauncher.api.ui.IProgressMonitor

interface IDownloader {
    fun init(progressMonitor: IProgressMonitor)
    fun download(progressMonitor: IProgressMonitor)
    fun shouldDownload(): Boolean
}