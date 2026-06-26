package ru.lionzxy.tplauncher.exceptions

// Message inlined from strings_en_US.properties `exception_heapsize` (LocalizationHelper is
// dropped per spec §4). Byte-faithful to the original formatted text ("%s" -> heapSize).
class HeapSizeInvalidException(val heapSize: String) :
    RuntimeException("$heapSize invalid heap size. Correct: 3G or 1024M")
