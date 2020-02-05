package com.pinterest.ktlint.LineFormatter

class LineSyntaxCheck {

    fun HasCommentAtEnd(ln: String) : Int {
        var commentIdx = ln.lastIndexOf("//")
        if (commentIdx > 0 && ln.takeLast(1) != "}") return commentIdx

        if (ln.takeLast(2) == "*/") {
            commentIdx = ln.lastIndexOf("/*")
            if (commentIdx > 0) return commentIdx
        }

        return -1
    }

    fun HasCurlyBracesAtEnd(ln : String) : Int {
        var txt = if (ln.takeLast(1) == ")") ln.dropLast(1).trimEnd() else ln.trimEnd()
        if (txt.takeLast(1) != "}") return -1

        var idx = txt.length - 2
        var leftNum = 0
        while ((txt[idx] != '{' || leftNum > 0) && idx >= 0) {
            when (txt[idx]) {
                '}' -> leftNum += 1
                '{' -> leftNum -= 1
            }
            idx -= 1
        }

        return idx
    }

    fun HasKeyWord(ln: String, key: String, leftBrace: Boolean = false) : Int {
        var idx = ln.indexOf(key)
        var keyLen = key.length
        if (idx == 0 || (idx > 0 && ln[idx - 1] == ' ')) {
            if (idx + keyLen < ln.length) {
                if (ln[idx + keyLen] == ' ') return idx
                if (leftBrace && ln[idx + keyLen] == '(') return idx
            }
        }

        return -1
    }
}
