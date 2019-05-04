package ru.lionzxy.tplauncher.exceptions

import ru.lionzxy.tplauncher.utils.LocalizationHelper

class HeapSizeInvalidException(val heapSize: String) :
    RuntimeException(LocalizationHelper.getString("exception_heapsize", heapSize)) {

}