package com.pinterest.ktlint.LineFormatter

import java.io.File
import java.io.FileWriter

fun LineFormatter(fileName: String, maxLen: Int, copy: Boolean = false) {
    var numLinesNotHandled = 0
    var inFile = File(fileName)

    if (!inFile.exists()) {
        println("File $fileName does not exist")
        return
    }

    if (copy) inFile.copyTo(File(fileName + ".org"), true)

    var lineParser = ParseFormatLine(maxLen)

    var txtList = arrayListOf<String>()
    inFile.readLines().forEach {
        var txt = lineParser.ParseLine(it)
        if (txt.contains("TODO: Reformat")) numLinesNotHandled++
        txtList.add(txt + "\n")
    }

    var outFile = FileWriter(fileName)
    txtList.forEach {
        outFile.write(it)
    }

    outFile.close()

    println("$numLinesNotHandled lines of $fileName need to be reformatted")
}
