package com.pinterest.ktlint.LineFormatter

class ParseFormatLine (length: Int) {
    var maxLen = length
    data class IntRef(var x: Int = -1)
    var lineChecker = LineSyntaxCheck()

    fun ParseLine(ln: String) : String {
        var txt = ln.trimEnd()
        if (txt.length <= maxLen) return txt

        var lineSyntaxCheck = LineSyntaxCheck()

        var commentIdx = lineSyntaxCheck.HasCommentAtEnd(txt)
        if (commentIdx > 0) return FormatComment(txt, commentIdx)

        var idx = lineSyntaxCheck.HasCurlyBracesAtEnd(txt)
        if (idx >= 0) return FormatCurlyBracesAtEnd(txt, idx)

        idx = lineSyntaxCheck.HasKeyWord(txt, "class")
        if (idx < 0) {
            idx = lineSyntaxCheck.HasKeyWord(txt, "fun")
        }
        if (idx >= 0) return FormatParameters(txt, idx)

        idx = lineSyntaxCheck.HasKeyWord(txt, "if", true)
        if (idx > 0) return FormatIf(txt, idx, 0)

        idx = lineSyntaxCheck.HasKeyWord(txt, "for", true)
        if (idx > 0) return FormatForLoop(txt, idx)

        //Format case like long list of passing paramerets
        idx = txt.indexOf('(')
        if (idx >= 0 && txt.lastIndexOf(')') > 0) return FormatParameters(txt, idx)

        var nonWhiteSpaceIdx = IndexOfFirstNonWhiteSpaceChar(txt)
        return String(CharArray(nonWhiteSpaceIdx) { ' ' }) + "//TODO: Reformat\n" + txt + "\n"
    }

    private fun IndexOfFirstNonWhiteSpaceChar(ln: String) : Int {
        for (item in ln.indices) {
            if (!ln[item].isWhitespace()) return item
        }

        return 0
    }

    private fun FormatComment(ln: String, idx: Int) : String {
        var nonWhiteSpaceIdx = IndexOfFirstNonWhiteSpaceChar(ln)
        var indent = String(CharArray(nonWhiteSpaceIdx) { ' ' })
        var txt = indent + ln.substring(idx, ln.length) + "\n" +
            ParseLine(ln.substring(0, idx))
        return txt
    }

    private fun FormatCurlyBracesAtEnd(ln: String, idx: Int) : String {
        var rightBraceIdx = ln.lastIndexOf('}')
        var firstLine = ParseLine(ln.substring(0, idx + 1)) + "\n"
        var nonWhiteSpaceIdx = IndexOfFirstNonWhiteSpaceChar(ln)
        var indent = String(CharArray(nonWhiteSpaceIdx + 4) { ' ' })
        var secondLine = ParseLine(indent +
            ln.substring(idx + 1, rightBraceIdx - 1).trim().trimEnd())

        return firstLine + secondLine + "\n" +
            ln.substring(0, nonWhiteSpaceIdx) +
            ln.substring(rightBraceIdx, ln.length)
    }

    private fun FormatParametersToLines(
        ln: String,
        idx: Int,
        matchChar: Char,
        indent: String,
        needNewLine : Boolean,
        lastIdx: IntRef
    ) : String {
        var startIdx = idx
        var endIdx = idx + 1
        var txt = ""
        var leftNum = 0
        var newLine = needNewLine
        var hasComma = false
        while ((ln[endIdx] != matchChar || leftNum > 0) && endIdx < ln.length) {
            when (ln[endIdx]) {
                '(' -> leftNum += 1
                ')' -> leftNum -= 1
                ',' -> if (leftNum == 0) {
                    if (newLine) txt += "\n" + indent else txt += " "
                    txt += ln.substring(startIdx, endIdx + 1).trim()
                    newLine = true
                    hasComma = true
                    startIdx = endIdx + 1
                }
            }
            endIdx += 1
        }

        if (hasComma) txt += "\n" + indent
        else if (matchChar != ')') txt += " "

        txt += ln.substring(startIdx, endIdx).trim()
        if (hasComma) txt += "\n"
        lastIdx.x = endIdx

        return txt
    }

    private fun FormatParameters(ln: String, idx: Int) : String {
        var nonWhiteSpaceIdx = IndexOfFirstNonWhiteSpaceChar(ln)
        var indent = String(CharArray(nonWhiteSpaceIdx + 4) { ' ' })
        var txt : String
        var moreIndent = false
        var oneSpace = true
        var startIdx = ln.indexOf('(', idx)
        var colonIdx = ln.indexOf(':', idx)
        if (startIdx > 0 && (colonIdx < 0 || startIdx < colonIdx)) {
            val lastIdx = IntRef()
            txt = ln.substring(0, startIdx + 1)
            var flTxt = FormatParametersToLines(ln, startIdx + 1, ')', indent, true, lastIdx)
            if (flTxt.indexOf(',') < 0) moreIndent = true
            txt += flTxt
            if (nonWhiteSpaceIdx > 0) txt += String(CharArray(nonWhiteSpaceIdx) { ' ' })
            txt += ")"
            startIdx = lastIdx.x + 1
        }
        else {
            if (colonIdx > 0) {
                txt = ln.substring(0, colonIdx).trimEnd()
                moreIndent = true
                startIdx = colonIdx
            }
            else { //shouldn't be here
                return "TODO: Reformat\n" + ln + "\n"
            }
        }

        if (startIdx < ln.length) {
            while (ln[startIdx].isWhitespace()) startIdx += 1
            if (ln[startIdx] == ':') {
                val lastIdx = IntRef()
                txt += " :"
                var flTxt = FormatParametersToLines(ln, startIdx + 1, '{', indent, moreIndent, lastIdx)
                if (flTxt.indexOf(',') > 0) {
                    oneSpace = false
                    moreIndent = true
                }
                txt += flTxt
                startIdx = lastIdx.x
            }

            if (moreIndent) {
                if (nonWhiteSpaceIdx > 0) txt += String(CharArray(nonWhiteSpaceIdx) { ' ' })
            } else {
                if (oneSpace) txt += " "
            }

            txt += ParseLine(ln.substring(startIdx))
        }

        return txt
    }

    private fun FormatIf(ln: String, idx: Int, extraIndent: Int) : String {
        var startIdx = ln.indexOf('(', idx)
        var endIdx = startIdx + 1
        var nonWhiteSpaceIdx = IndexOfFirstNonWhiteSpaceChar(ln)
        var indent = String(CharArray(nonWhiteSpaceIdx + extraIndent + 4) { ' ' })
        var txt = ln.substring(0, startIdx + 1)
        var hasLeftBrace = 0
        var newLine = false
        while ((ln[endIdx] != ')' || hasLeftBrace > 0) && endIdx < ln.length) {
            if (ln[endIdx] == '(') hasLeftBrace += 1
            else if (ln[endIdx] ==  ')') hasLeftBrace -= 1
            else if ((ln[endIdx] == '&' || ln[endIdx] == '|') &&  hasLeftBrace == 0) {
                if (ln[endIdx + 1] == '&' || ln[endIdx + 1] == '|') {
                    var flTxt = ""
                    var startLenth = 0
                    var moreIndent = 0
                    if (newLine) flTxt += "\n" + indent
                    else {
                        startLenth = txt.length
                        moreIndent = nonWhiteSpaceIdx + 4
                    }
                    flTxt += ln.substring(startIdx + 1, endIdx + 2).trim()
                    if (flTxt.length + startLenth >= maxLen) {
                        if (newLine) flTxt = indent + ln.substring(startIdx + 1, endIdx + 2).trim()
                        flTxt = FormatIf(flTxt, 0, moreIndent)
                    }
                    endIdx += 2
                    txt += flTxt
                    newLine = true
                    startIdx = endIdx + 1
                }
            }
            endIdx += 1
        }
        var fTxt = "\n" + indent + ln.substring(startIdx, endIdx).trim()
        if (fTxt.length >= maxLen) {
            var moreIndent = if (newLine) 0 else nonWhiteSpaceIdx + 4
            fTxt = FormatIf(fTxt, 0, moreIndent)
        }

        txt += fTxt + "\n" + String(CharArray(nonWhiteSpaceIdx + extraIndent) { ' ' }) + ln.substring(endIdx)

        return txt
    }

    private fun FormatForLoop(ln: String, idx: Int) : String {
        var inIdx = ln.indexOf(" in ", idx + 3)
        var downToIdx = ln.indexOf(" downTo ", idx + 3)
        var stepIdx = ln.indexOf(" step ", idx + 3)
        var endIdx = ln.lastIndexOf(')')
        var nonWhiteSpaceIdx = IndexOfFirstNonWhiteSpaceChar(ln)
        var indent = String(CharArray(nonWhiteSpaceIdx + 4) { ' ' })

        var startIdx = inIdx + 3
        var txt = ln.substring(0, inIdx + 3) + "\n"
        if (downToIdx > 0) {
            txt += indent + ln.substring(startIdx , downToIdx + 7).trim() + "\n"
            startIdx = downToIdx + 7
        }
        if (stepIdx > 0) {
            txt += indent + ln.substring(startIdx , stepIdx + 5).trim() + "\n"
            startIdx = stepIdx + 5
        }

        txt += indent + ln.substring(startIdx , endIdx).trim() + "\n"
        txt += String(CharArray(nonWhiteSpaceIdx) { ' ' }) + ln.substring(endIdx).trim()

        return txt
    }
}

