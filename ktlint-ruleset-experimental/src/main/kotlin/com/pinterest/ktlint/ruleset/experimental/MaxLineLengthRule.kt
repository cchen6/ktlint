package com.pinterest.ktlint.ruleset.experimental


import com.pinterest.ktlint.core.KtLint
import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType
import com.pinterest.ktlint.core.ast.ElementType.ARROW
import com.pinterest.ktlint.core.ast.ElementType.CLASS_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.CLOSING_QUOTE
import com.pinterest.ktlint.core.ast.ElementType.COLON
import com.pinterest.ktlint.core.ast.ElementType.COMMA
import com.pinterest.ktlint.core.ast.ElementType.ELSE_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.EQ
import com.pinterest.ktlint.core.ast.ElementType.FIELD_IDENTIFIER
import com.pinterest.ktlint.core.ast.ElementType.FOR_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.FUN_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.GT
import com.pinterest.ktlint.core.ast.ElementType.IDENTIFIER
import com.pinterest.ktlint.core.ast.ElementType.IF
import com.pinterest.ktlint.core.ast.ElementType.IF_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.LBRACE
import com.pinterest.ktlint.core.ast.ElementType.LBRACKET
import com.pinterest.ktlint.core.ast.ElementType.LPAR
import com.pinterest.ktlint.core.ast.ElementType.LT
import com.pinterest.ktlint.core.ast.ElementType.OPEN_QUOTE
import com.pinterest.ktlint.core.ast.ElementType.OROR
import com.pinterest.ktlint.core.ast.ElementType.RBRACE
import com.pinterest.ktlint.core.ast.ElementType.RBRACKET
import com.pinterest.ktlint.core.ast.ElementType.RPAR
import com.pinterest.ktlint.core.ast.ElementType.VAL_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.VAR_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.WHILE_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.WHITE_SPACE
import com.pinterest.ktlint.core.ast.nextLeaf
import com.pinterest.ktlint.core.ast.prevLeaf
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin

class MaxLineLengthRule : Rule("max-line-rule"), Rule.Modifier.RestrictToRootLast {
    private var maxLineLength = -1
    private var indentSize = -1
    private var moreIndent = 0
    private var numMoreIndent = 0
    private var autoCorrectCalled = false

    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
    ) {
        val editorConfig = node.getUserData(KtLint.EDITOR_CONFIG_USER_DATA_KEY)!!
        maxLineLength = editorConfig.maxLineLength
        indentSize = editorConfig.indentSize
        if (maxLineLength <= 0 || indentSize < 0) return

        var offset = 0
        val lines = node.text.split("\n")

        for (line in lines) {
            var ln = String(CharArray(moreIndent) { ' ' }) + line.substring(0, line.length)
            if (numMoreIndent > 0) {
                numMoreIndent -= 1
                if (numMoreIndent == 0) moreIndent = 0
            }

            var len = ln.length
            if (ln.length > maxLineLength) {
                if (autoCorrect) {
                    emit(offset, "Exceeded max line length ($maxLineLength)", true)
                    len = ParseLine(ln, offset, node)
                } else {
                    emit(offset, "Exceeded max line length ($maxLineLength)", !autoCorrectCalled)
                }
            }

            offset += len + 1
        }

        if (autoCorrect) autoCorrectCalled = true
    }

    private fun ParseLine(ln: String, offset: Int, node: ASTNode) : Int {
        var trimmed = 0
        val elm = GetLastLeafInLine(node, offset + ln.length - 2)
        if (elm.nextLeaf() == null) {
            (elm as LeafPsiElement).insertTextAfterMe("\n")
        }

        if (elm.elementType == WHITE_SPACE) {
            trimmed = elm.textLength
            (elm as LeafPsiElement).rawRemove()
        }

        val line = ln.substring(0, ln.length - trimmed)
        if (line.length <= maxLineLength) return line.length

        var el = HasCommentAtEnd(line, offset, node)
        if (el != null) return FormatComment(line, offset,el, node)

        el = HasRBraceAtEnd(line, offset, node)
        if (el != null) {
            val len = FormatRBraceAtEnd(line, offset, el.startOffset - offset, node)
            if (len > 0) return len
        }

        el = HasKeyWordFromEnd(node, LBRACE, offset, line.length, line)
        if (el != null) {
            val len = FormatLBrace(line, offset, el.startOffset - offset, node)
            if (len > 0) return len
        }

        el = HasKeyWord(node, CLASS_KEYWORD, offset, 0, line)
        if (el != null) el = IsClass(el)
        if (el == null) el = HasKeyWord(node, FUN_KEYWORD, offset, 0, line)
        if (el != null) {
            val keyOffset =  el.startOffset - offset
            return FormatParameters(line, offset, el.elementType, keyOffset, node)
        }

        el = HasKeyWord(node, IF_KEYWORD, offset, 0, line)
        if (el == null) el = HasKeyWord(node, WHILE_KEYWORD, offset, 0, line)
        if (el != null) {
            val keyOffset =  el.startOffset - offset
            val len = FormatControlFlow(line, offset, el.elementType, keyOffset, node)
            if (len > 0) return len
        }

        el = HasKeyWord(node, FOR_KEYWORD, offset, 0, line)
        if (el != null) return FormatForLoop(line, offset, el.startOffset - offset, node)

        el = HasKeyWord(node, EQ, offset, 0, line)
        if (el != null) {
            val len = FormatEQ(ln, offset, el.startOffset - offset, node)
            if (len > 0) return len
        }

        val andEl = HasKeyWord(node, ElementType.ANDAND, offset, 0, line)
        val orEl = HasKeyWord(node, ElementType.OROR, offset, 0, line)
        if (andEl != null || orEl != null) {
            if (andEl != null) {
                if (orEl != null) {
                    el = if (andEl.startOffset < orEl.startOffset) andEl else orEl
                } else {
                    el = andEl
                }
            } else {
                el = orEl!!
            }
            return  FormatControlFlow(line, offset, el.elementType, el.startOffset - offset, node)
        }

        el = HasKeyWord(node, COMMA, offset, 0, line)
        if (el != null) {
            var len = FormatComma(ln, offset, el.startOffset - offset, node)
            if (len > 0) return len
        }
        el = HasKeyWord(node, LPAR, offset, 0, line)
        if (el != null) {
            var len = FormatLPAR(ln, offset, el.startOffset - offset, node)
            if (len > 0) return len
        }

        return line.length
    }

    private fun FormatComment(ln: String, offset: Int, el: ASTNode, node: ASTNode) : Int {
        var endEl = GetLastLeafInLine(node, el.startOffset).nextLeaf()!!
        var idx = ln.lastIndexOf(el.text)
        var firstEl = node.psi.findElementAt(offset)!!.node
        var indentLen = IndentLength(ln, 0)
        if (el.startOffset - offset <= indentLen) return ln.length // whole line is comment

        var indent = String(CharArray(indentLen) { ' ' })
        var newText = firstEl.text.substring(0, FirstNotMatchChar(firstEl.text, 0, '\n'))
        newText += "\n" + indent + el.text + '\n' + indent

        (el as LeafPsiElement).rawRemove()
        ParseLine(ln.substring(0, idx), offset, node)
        (firstEl as LeafPsiElement).insertTextBeforeMe(newText)

        return endEl.startOffset - offset
    }

    private fun FormatRBraceAtEnd(ln: String, offset: Int, idx: Int, node: ASTNode) : Int {
        var endEl = GetLastLeafInLine(node, offset + idx).nextLeaf()!!
        var rBraceIdx = ln.lastIndexOf('}')
        var indent = String(CharArray(IndentLength(ln, 0)) { ' ' })
        var nextEl = node.psi.findElementAt(offset + rBraceIdx + 1)!!.node
        while (nextEl.elementType != RBRACE) nextEl = nextEl.prevLeaf()

        if (nextEl.prevLeaf()!! is PsiWhiteSpace) nextEl = nextEl.prevLeaf()

        var text = node.text.substring(offset, nextEl.startOffset)
        (nextEl as LeafPsiElement).insertTextBeforeMe("\n" + indent)
        FormatLBrace(text, offset, idx, node)

        return endEl.startOffset - offset
    }

    private fun FormatLBrace(ln: String, offset: Int, keyOffset:Int, node: ASTNode) : Int {
        var endEl = GetLastLeafInLine(node, offset + keyOffset).nextLeaf()!!
        var indentLen = IndentLength(ln, 0)
        var el = node.psi.findElementAt(offset + keyOffset)!!.node.nextLeaf()!!
        var arrowEl = HasKeyWord(node, ARROW, offset, el.startOffset - offset, ln)
        if (arrowEl != null) el = arrowEl.nextLeaf()!!
        if (el.startOffset == endEl.startOffset ||
            el.nextLeaf()!!.startOffset == endEl.startOffset
        ) {
            return 0   // "{" at end
        }

        var rOffset = el.startOffset
        var indent = String(CharArray(indentLen + indentSize) { ' ' })
        var rbEl = HasKeyWord(node, RBRACE, offset, rOffset - offset, ln)

        if (rbEl != null) {
            var lpEl = HasKeyWordFromEnd(node, LBRACE, offset, rbEl.startOffset - offset, null)
            if (lpEl != null && lpEl.startOffset - offset <= keyOffset) {
                var headIndent = String(CharArray(indentLen) { ' ' })
                (rbEl as LeafPsiElement).insertTextBeforeMe("\n" + headIndent)
            }
        }

        (el as LeafPsiElement).insertTextBeforeMe("\n" + indent)
        ParseLine(node.text.substring(rOffset, endEl.startOffset), rOffset, node)
        ParseLine(node.text.substring(offset, rOffset), offset, node)

        return endEl.startOffset - offset
    }

    private fun ParametersToLines(
        node: ASTNode,
        startEl: ASTNode,
        endEl: ASTNode,
        keyWord: IElementType,
        token: IElementType,
        indentLen: Int,
        newLine: Boolean
    ) : Int {
        var lParNum = 0
        var indent = String(CharArray(indentLen + indentSize) { ' ' })
        if (keyWord == COMMA && !newLine) {
            indent = String(CharArray(indentLen) { ' ' })
        }

        var sText = ""
        if (newLine) {
            sText = "\n" + indent
        } else {
            if (keyWord == COLON) sText = " "
        }
        var el = startEl
        if (el.elementType == LPAR) el = el.nextLeaf()!!
        var sEl = el
        var hasComma = false
        var ltNum = 0
        while (el.startOffset < endEl.startOffset) {
            if (el.elementType == token && lParNum == 0) break
            when (el.elementType) {
                LPAR, LBRACKET -> lParNum += 1
                RPAR, RBRACKET -> lParNum -= 1
                LT -> ltNum += 1
                GT -> ltNum -= 1
                COMMA -> if (lParNum == 0) {
                    if (ltNum != 0) {
                        if (HasKeyWord(startEl, IF, sEl.startOffset, 0, null) != null) ltNum = 0
                    }
                    if (ltNum == 0) {
                        (sEl as LeafPsiElement).insertTextBeforeMe(sText)
                        sText = "\n" + indent
                        sEl = el.nextLeaf()!!
                        hasComma = true
                    }
                }
            }

            el = el.nextLeaf()!!
        }

        var offset = el.startOffset

        var forceNewLine = hasComma || (newLine && token != RPAR) || keyWord == LPAR
        if (!forceNewLine &&
            startEl.elementType == LPAR
            && !(startEl.prevLeaf()!!.textContains('\n'))
        ) {
            forceNewLine = true
        }
        if (forceNewLine) {
            (sEl as LeafPsiElement).insertTextBeforeMe(sText)
            offset = el.startOffset
            if (el.elementType == token && lParNum == 0) {
                if (el.prevLeaf()!! is PsiWhiteSpace) (el.prevLeaf() as LeafPsiElement).rawRemove()
                (el as LeafPsiElement).insertTextBeforeMe("\n" + String(CharArray(indentLen) { ' ' }))
            }
        }

        return offset
    }

    private fun FormatParameters(
        ln: String,
        offset: Int,
        keyWord: IElementType,
        keyOffset: Int,
        node: ASTNode
    ) : Int {
        var lastEl = GetLastLeafInLine(node, keyOffset + offset)
        var indentLen = IndentLength(ln, 0)
        var startIdx = -1
        var colonIdx = -1
        if (keyWord != COMMA) {
            startIdx = ln.indexOf('(', keyOffset)
            colonIdx = ln.indexOf(':', keyOffset)
            while (colonIdx >= 0 && ln[colonIdx + 1] == ':') { // double colons
                colonIdx = ln.indexOf(':', colonIdx + 2)
            }
        }

        var newLine = true
        var startOffset = offset
        var endEl = lastEl.nextLeaf()!!
        if (startIdx > 0 && (colonIdx < 0 || startIdx < colonIdx)) {
            var sEl = HasKeyWord(node, LPAR, offset, 0, null)!!
            if (sEl.nextLeaf()!!.elementType != RPAR) {
                startOffset = ParametersToLines(node, sEl, lastEl, keyWord, RPAR, indentLen, true)
            }
            newLine = false
        } else if (colonIdx > 0) {
            newLine = true
        } else {
            var hasPar = false
            var sIdx = ln.lastIndexOf('(', keyOffset)
            if (sIdx >= 0) {
                var eIdx = ln.indexOf(')', sIdx + 1)
                if (eIdx > 0 && eIdx > keyOffset) hasPar = true
            }

            var sEl: ASTNode
            if (hasPar) {
                sEl = node.psi.findElementAt(offset + sIdx + 1)!!.node.prevLeaf()!!
                if (sEl.elementType == LPAR) sEl = sEl.nextLeaf()!!
            } else {
                sEl = node.psi.findElementAt(offset + keyOffset)!!.node.prevLeaf()!!
            }
            startOffset = ParametersToLines(node, sEl, lastEl, keyWord, RPAR, indentLen, hasPar)
        }

        var startEl = node.psi.findElementAt(startOffset + 1)!!.node
        if (colonIdx >= 0) {
            while (startEl.startOffset <= lastEl.startOffset && startEl.elementType != COLON) {
                startEl = startEl.nextLeaf()
            }
        }

        if (startEl.elementType == COLON && startEl.nextLeaf() != null) {
            ParametersToLines(node, startEl, lastEl, keyWord, LBRACE, indentLen, newLine)
        }

        return endEl.startOffset - offset
    }

    private fun FormatControlFlow(
        ln: String,
        offset: Int,
        keyWord: IElementType,
        keyOffset: Int,
        node: ASTNode
    ) : Int {
        var sEl = node.psi.findElementAt(offset + keyOffset)!!.node
        var preEl = sEl
        var extraIndent = 0
        var newLine = false
        var lpEl: ASTNode? = null
        when (keyWord) {
            IF_KEYWORD, WHILE_KEYWORD, LPAR -> {
                sEl = HasKeyWord(node, LPAR, sEl.startOffset, 0, null)!!.nextLeaf()
                extraIndent = indentSize
                if (keyWord == LPAR) newLine = true
            }
            ElementType.ANDAND, ElementType.OROR -> {
                sEl = sEl.nextLeaf()
                newLine = true
            }
        }
        var startIdx = sEl.startOffset
        var lastEl = GetLastLeafInLine(node, sEl.startOffset)
        var endEl = lastEl.nextLeaf()!!
        var indentLen = IndentLength(ln, 0)
        var indent = String(CharArray(indentLen + extraIndent) { ' ' })

        if (keyWord == IF_KEYWORD) {
            preEl = preEl.prevLeaf()
            if (preEl is PsiWhiteSpace ) preEl = preEl.prevLeaf()

            if (preEl.startOffset > offset) {
                when (preEl.elementType) {
                    LPAR, LBRACE, EQ, ARROW -> {
                        // Split to new line if '(', '{', "=", or "->" preceed to "if"
                        var splitOffset = preEl.startOffset + preEl.textLength
                        preEl = preEl.nextLeaf()

                        var sOffset = preEl.startOffset
                        (preEl as LeafPsiElement).insertTextBeforeMe("\n" + indent)

                        FormatCtrlNewLine(sOffset, IF_KEYWORD, sOffset, endEl.startOffset, node)
                        ParseLine(node.text.substring(offset, splitOffset), offset, node)

                        return endEl.startOffset - offset
                    }
                }
            }

            var el = HasKeyWord(node, ELSE_KEYWORD, offset, keyOffset, ln)
            if (el != null && el.startOffset > keyOffset + offset) {
                var rOffset = el.startOffset
                var headIndent = String(CharArray(indentLen) { ' ' })
                var nextEl = el.nextLeaf()!!

                if ((nextEl is PsiWhiteSpace) && nextEl.nextLeaf()!!.elementType == IF_KEYWORD) {
                    // if (...) ... else if (...) ...
                    (el as LeafPsiElement).insertTextBeforeMe("\n" + headIndent)
                    var text = node.text.substring(rOffset, endEl.startOffset)
                    var sOffset = nextEl.nextLeaf()!!.startOffset - rOffset
                    FormatControlFlow(text, rOffset, keyWord, sOffset, node)

                    text = node.text.substring(offset, rOffset)
                    rOffset = FormatControlFlow(text, offset, keyWord, keyOffset, node)
                    var elseEl = node.psi.findElementAt(offset + rOffset + 1)!!.node!!
                    (elseEl as LeafPsiElement).insertTextBeforeMe(" ")

                    return endEl.startOffset - offset
                }

                // if (...) ... else ...
                if (HasKeyWord(node, RBRACE, nextEl.startOffset, 0, null) != null ) {
                    return 0      // else  ... } should be handled already
                }

                var split = el
                (el as LeafPsiElement).insertTextBeforeMe("\n" + headIndent)
                (nextEl as LeafPsiElement).insertTextBeforeMe("\n" + indent)

                var sOffset = split.nextLeaf()!!.startOffset
                var endOffset = InsertRBrace(node, offset, sOffset - offset, headIndent, lastEl)
                var text: String
                if (endOffset > 0) {
                    text = node.text.substring(sOffset, endOffset)
                } else {
                    text = node.text.substring(sOffset, endEl.startOffset)
                }

                ParseLine(text, sOffset, node)

                text = node.text.substring(offset, rOffset)
                FormatControlFlow(text, offset, keyWord, keyOffset, node)

                el = split
                if (endOffset > 0) (split as LeafPsiElement).insertTextAfterMe(" {")

                if (el.text[0] != '\n') el = el.prevLeaf()
                (el as LeafPsiElement).insertTextBeforeMe(" ")

                return endEl.startOffset - offset
            }
        }

        var rbraceEl = HasRBraceAtEnd(ln, offset, node)
        if (rbraceEl != null) {
            FormatRBraceAtEnd(ln, offset, rbraceEl.startOffset - offset, node)
        }

        var rparEl = FindPairedBraces(sEl, LPAR, RPAR, lastEl)
        if (rparEl != null) {
            var headIndent = String(CharArray(indentLen) { ' ' })
            var nextEl = rparEl.nextLeaf()!!
            if (nextEl is PsiWhiteSpace) nextEl = nextEl.nextLeaf()!!
            if (keyWord == IF_KEYWORD || keyWord == WHILE_KEYWORD) {
                // if ( ... ) ...
                if (nextEl.elementType != LBRACE && rparEl != lastEl) {
                    (rparEl.nextLeaf() as LeafPsiElement).insertTextBeforeMe("\n" + indent)

                    var sOffset = rparEl.nextLeaf()!!.startOffset
                    var endOffset = InsertRBrace(node, offset, sOffset - offset, headIndent, lastEl)
                    var text: String
                    if (endOffset > 0) {
                        text = node.text.substring(sOffset, endOffset)
                    } else {
                        text = node.text.substring(sOffset, endEl.startOffset)
                    }

                    ParseLine(text, sOffset, node)
                    ParseLine(node.text.substring(offset, sOffset), offset, node)

                    if (endOffset > 0) (rparEl as LeafPsiElement).insertTextAfterMe(" {")

                    return endEl.startOffset - offset
                }

                if (ln.length <= maxLineLength) {
                    // if line lenth <= maxLineLength, skip parameter list split
                    if (rparEl != lastEl && nextEl != lastEl) {
                        // nextEl.elementType == LBRACE and not at end
                        (nextEl.nextLeaf() as LeafPsiElement).insertTextBeforeMe("\n" + indent)
                    }

                    return endEl.startOffset - offset
                }
            } else {
                if (rparEl == lastEl) {
                    // ( .. && ...)
                    lpEl = HasKeyWord(node, LPAR, offset, 0, null)
                    indent = String(CharArray(indentLen + indentSize) { ' ' })
                } else if (nextEl.elementType != LBRACE) {
                    if (nextEl.elementType != ElementType.ANDAND &&
                        nextEl.elementType != ElementType.OROR
                    ) {
                        (rparEl.nextLeaf() as LeafPsiElement).insertTextBeforeMe("\n" + indent)
                        (lastEl as LeafPsiElement).insertTextAfterMe("\n" + headIndent)
                    }
                    else if (rparEl.startOffset - offset > keyOffset) {
                        // ( ... && .. ) && ...
                        lpEl = HasKeyWord(node, LPAR, offset, 0, null)
                        if (lpEl != null && (lpEl.startOffset < rparEl.startOffset)) {
                            sEl = lpEl
                            if (rparEl.startOffset - offset > maxLineLength) {
                                sEl = sEl.nextLeaf()
                                startIdx = sEl.startOffset
                                indent = String(CharArray(indentLen + indentSize) { ' ' })
                            } else {
                                newLine = false
                            }
                        }
                        else {
                            lpEl = null
                        }
                    }
                }
            }
        }

        var lPar = 0
        var el = sEl
        var prevIdx = startIdx
        var prevOffset = 0
        var ltNum = 0
        while (el!!.startOffset <= lastEl.startOffset && (el.elementType != RPAR || lPar > 0)) {
            when (el.elementType) {
                LPAR, LBRACKET -> lPar += 1
                RPAR, RBRACKET -> lPar -= 1
                LT -> ltNum += 1
                GT -> ltNum -= 1
                ElementType.ANDAND, ElementType.OROR -> {
                    if (lPar == 0) {
                        if (ltNum > 0) {
                            if (HasKeyWord(sEl, IF, startIdx, 0, null) != null) ltNum = 0
                        }
                        if (ltNum == 0) {
                            if (prevOffset > 0) {
                                FormatCtrlNewLine(prevOffset, LPAR, prevIdx, startIdx, node)
                            }

                            prevOffset = sEl.startOffset
                            if (newLine) {
                                (sEl as LeafPsiElement).insertTextBeforeMe("\n" + indent)
                            }

                            prevIdx = startIdx
                            sEl = el.nextLeaf()!!
                            startIdx = sEl.startOffset
                        }
                        newLine = true
                    }
                }
            }

            el = el.nextLeaf()!!
        }

        if (prevOffset == 0 && newLine && lpEl != null) {
            (lpEl.nextLeaf() as LeafPsiElement).insertTextBeforeMe("\n" + indent)
        }

        if (!sEl.textContains('\n')) {
            (sEl as LeafPsiElement).insertTextBeforeMe("\n" + indent)
        }

        var sOffset = el.startOffset
        if (el.elementType == RPAR && lPar == 0 && !el.prevLeaf()!!.textContains('\n')) {
            var headIndent = "\n" + String(CharArray(indentLen) { ' ' })
            (el as LeafPsiElement).insertTextBeforeMe(headIndent)
        }

        if (prevOffset > 0) {
            if (endEl.startOffset > sOffset) {
                FormatCtrlNewLine(sOffset, LPAR, sOffset, endEl.startOffset, node)
            }
            FormatCtrlNewLine(startIdx, LPAR, startIdx, sOffset, node)
        } else {
            FormatCtrlNewLine(prevIdx, LPAR, prevIdx, sOffset, node)
        }

        return endEl.startOffset - offset
    }

    private fun FormatCtrlNewLine(
        offset: Int,
        keyWord: IElementType,
        startIdx: Int,
        endIdx: Int,
        node: ASTNode
    ) : Int {
        var len = endIdx - startIdx
        var newKeyWord = keyWord
        var text = node.text.substring(startIdx, endIdx)
        var el = HasKeyWord(node, keyWord, offset, 0, null)
        var rEl = HasKeyWord(node, RPAR, offset, 0, null)

        if (rEl != null && rEl.prevLeaf()!!.textContains('\n')) {
            var andEl = HasKeyWord(node, ElementType.ANDAND, offset, 0, null)
            var orEl = HasKeyWord(node, ElementType.OROR, offset, 0, null)
            if (andEl == null && orEl == null) {
                if (el == null || len <= maxLineLength) return offset
            } else {
                if (andEl == null) {
                    el = orEl!!
                } else if (orEl == null) {
                    el = andEl
                } else {
                    el = if (andEl.startOffset < orEl.startOffset) andEl else orEl
                }

                if (el.nextLeaf()!!.startOffset >= endIdx && len <= maxLineLength) {
                    return offset
                }

                newKeyWord = OROR
            }
        }  else if (el == null || len <= maxLineLength)  {
            return offset
        }

        var keyOffset = if (newKeyWord == LPAR) 0 else el.startOffset - offset
        return FormatControlFlow(text, offset, newKeyWord, keyOffset, node)
    }

    private fun FormatForLoop(ln: String, offset: Int, keyOffset: Int, node: ASTNode) : Int {
        var lastEl = GetLastLeafInLine(node, offset + keyOffset)
        var endEl = lastEl.nextLeaf()!!
        var indentLen = IndentLength(ln, 0)
        var indent = String(CharArray(indentLen + indentSize) { ' ' })
        var el = node.psi.findElementAt(offset + keyOffset)!!.node
        var sEl = HasKeyWord(node, LPAR, el.startOffset, 0, null)!!.nextLeaf()!!
        var rparEl = FindPairedBraces(sEl, LPAR, RPAR, lastEl)
        if (rparEl != null) {
            var headIndent = String(CharArray(indentLen) { ' ' })
            var nextEl = rparEl.nextLeaf()!!
            if (nextEl is PsiWhiteSpace) nextEl = nextEl.nextLeaf()!!
            if (nextEl.elementType != LBRACE && rparEl != lastEl) {
                // for ( ... ) ...
                (rparEl.nextLeaf() as LeafPsiElement).insertTextBeforeMe("\n" + indent)

                var sOffset = rparEl.nextLeaf()!!.startOffset
                var endOffset = InsertRBrace(node, offset, sOffset - offset, headIndent, lastEl)
                var text: String
                if (endOffset > 0) {
                    text = node.text.substring(sOffset, endOffset)
                } else {
                    text = node.text.substring(sOffset, endEl.startOffset)
                }

                ParseLine(text, sOffset, node)
                ParseLine(node.text.substring(offset, sOffset), offset, node)

                if (endOffset > 0) (rparEl as LeafPsiElement).insertTextAfterMe(" {")

                return endEl.startOffset - offset
            }
        }
        var sOffset = offset + keyOffset + 3
        var stepEl = HasKeyWord(node, "step", sOffset)
        if (stepEl != null) {
            (stepEl as LeafPsiElement).insertTextBeforeMe(" \n" + indent)
        }

        var downToEl = HasKeyWord(node, "downTo", sOffset)
        if (downToEl != null) {
            (downToEl as LeafPsiElement).insertTextBeforeMe(" \n" + indent)
        }

        var untilEl = HasKeyWord(node, "until", sOffset)
        if (untilEl != null) {
            (untilEl as LeafPsiElement).insertTextBeforeMe(" \n" + indent)
        }

        var inEl = HasKeyWord(node, ElementType.IN_KEYWORD, offset, sOffset - offset, null)
        if (inEl != null) {
            (inEl as LeafPsiElement).insertTextBeforeMe(" \n" + indent)
        }

        if (rparEl != null) {
            var headIndent = String(CharArray(indentLen) { ' ' })
            (rparEl as LeafPsiElement).insertTextBeforeMe(" \n" + headIndent)
        }

        return endEl.startOffset - offset
    }

    private fun FormatEQ(ln: String, offset: Int, keyOffset: Int, node: ASTNode) : Int {
        var endEl = GetLastLeafInLine(node, offset + keyOffset).nextLeaf()!!
        var formatEQ = if (HasKeyWord(node, VAL_KEYWORD, offset, 0, ln) == null) false else true
        if (!formatEQ) {
            if (HasKeyWord(node, VAR_KEYWORD, offset, 0, ln) != null) formatEQ = true
        }

        if (!formatEQ) {
            var el = node.psi.findElementAt(offset + keyOffset)!!.node.prevLeaf()!!
            if (el is PsiWhiteSpace) el = el.prevLeaf()!!
            if (el.elementType == ElementType.IDENTIFIER) {
                el = el.prevLeaf()!!
                if (el.textContains('\n') ||
                    (el is PsiWhiteSpace && el.prevLeaf()!!.prevLeaf()!!.textContains('\n'))
                ) {
                    formatEQ = true
                }
            }
        }

        if (formatEQ) {
            //handle cases: var a = ..., val a =  or a = ...
            var sEl = node.psi.findElementAt(offset + keyOffset)!!.node.nextLeaf()!!
            var rOffset = sEl.startOffset
            var indentLen = IndentLength(ln, 0)
            var indent = String(CharArray(indentLen + indentSize) { ' ' })
            (sEl as LeafPsiElement).insertTextBeforeMe("\n" + indent)

            ParseLine(node.text.substring(rOffset, endEl.startOffset), rOffset, node)
            var len = endEl.startOffset - offset
            var lastEl = endEl.prevLeaf()!!
            if (lastEl is PsiWhiteSpace) lastEl = lastEl.prevLeaf()!!
            if (lastEl.elementType == LPAR) {
                var nextEndEl = GetLastLeafInLine(node, offset + len + 1).nextLeaf()!!
                var nextEl = if (endEl is PsiWhiteSpace) endEl.nextLeaf() else endEl
                var extraIndent = String(CharArray(indentSize) { ' ' })
                (nextEl as LeafPsiElement).insertTextBeforeMe(extraIndent)
                numMoreIndent = 1

                nextEl = if (nextEndEl is PsiWhiteSpace) nextEndEl.nextLeaf() else nextEndEl
                if (nextEl!!.elementType == RPAR) {
                    (nextEl as LeafPsiElement).insertTextBeforeMe(extraIndent)
                    numMoreIndent += 1
                }
                moreIndent = indentSize
            }

            return len
        }

        return 0
    }

    private fun FormatComma(ln: String, offset: Int, keyOffset:Int, node: ASTNode) : Int {
        var lastEl = GetLastLeafInLine(node, offset + keyOffset)
        var endEl = lastEl.nextLeaf()!!
        var el = node.psi.findElementAt(offset + keyOffset)!!.node.nextLeaf()!!
        if (el == endEl) return 0   // comma is at end of line

        var lparEl = HasKeyWordFromEnd(node, LPAR, offset + keyOffset, 0, null)
        if (lparEl != null && lparEl.startOffset < keyOffset + offset) {
            var rparEl = FindPairedBraces(lparEl.nextLeaf()!!, LPAR, RPAR, lastEl)
            if (rparEl != null && rparEl.startOffset > keyOffset + offset) {
                return FormatParameters(ln, offset, COMMA, keyOffset, node)
            }
        }

        var sOffset = el.startOffset
        var indentLen = IndentLength(ln, 0)
        (el as LeafPsiElement).insertTextBeforeMe("\n" + String(CharArray(indentLen) { ' ' }))

        var text = node.text.substring(sOffset, endEl.startOffset)
        ParseLine(text, sOffset - offset, node)

        return endEl.startOffset - offset
    }

    private fun FormatLPAR(ln: String, offset: Int, keyOffset:Int, node: ASTNode) : Int {
        var lastEl = GetLastLeafInLine(node, offset + keyOffset)
        var sEl = node.psi.findElementAt(offset + keyOffset)!!.node
        var rparEl = FindPairedBraces(sEl.nextLeaf()!!, LPAR, RPAR, lastEl)
        if (rparEl != null && sEl.nextLeaf()!!.elementType != rparEl.elementType) {
            return FormatParameters(ln, offset, LPAR, keyOffset, node)
        }

        return 0
    }

    private fun HasCommentAtEnd(ln: String, offset: Int, node: ASTNode): ASTNode? {
        val el = node.psi.findElementAt(offset + ln.length - 1)!!.node
        return if (el is PsiComment) el else null
    }

    private fun HasRBraceAtEnd(ln: String, offset: Int, node: ASTNode): ASTNode? {
        val keys = setOf(LPAR, LBRACE, RPAR, CLOSING_QUOTE, OPEN_QUOTE)
        var el = node.psi.findElementAt(offset + ln.length)!!.node
        if (el.textContains('\n')) el = el.prevLeaf()!!
        while (!el.textContains('\n') &&
            el.elementType != RBRACE &&
            !(el.elementType in keys)
        ) {
            el = el.prevLeaf()
        }

        if (el.elementType == RPAR) {
            el = el.prevLeaf()!!
            if (el is PsiWhiteSpace) el = el.prevLeaf()!!
        }
        if (el.elementType != RBRACE) return null

        var rNum = 0
        el = el.prevLeaf()
        while (!el.textContains('\n') && (el.elementType != LBRACE || rNum > 0)) {
            when (el.elementType) {
                LBRACE -> rNum -= 1
                RBRACE -> rNum += 1
            }
            el = el.prevLeaf()
        }

        if (!el.textContains('\n') && el.elementType == LBRACE && rNum == 0) {
            return el
        }

        return null
    }

    private fun HasKeyWord(
        node: ASTNode,
        keyWord: IElementType,
        offset: Int,
        idx: Int,
        text:String?
    ) : ASTNode? {
        var el = node.psi.findElementAt(offset + idx)!!.node
        if (el.textContains('\n')) el = el.nextLeaf()
        while (el.elementType != keyWord && !el.textContains('\n')) {
            el = el.nextLeaf()
        }

        if (el.elementType != keyWord || el.textContains('\n')) return null

        val keys = setOf(CLASS_KEYWORD, IF_KEYWORD, WHILE_KEYWORD, FOR_KEYWORD, COMMA)
        if (keyWord != LPAR && keyWord in keys) {
            var lastEl = GetLastLeafInLine(node, el.startOffset)
            var lparEl = HasKeyWordFromEnd(node, LPAR, offset, el.startOffset - offset, null)
            while (lparEl != null) {
                var rparEl = FindPairedBraces(lparEl.nextLeaf()!!, LPAR, RPAR, lastEl)
                if (rparEl == null ||
                    (lparEl.startOffset < el.startOffset && rparEl.startOffset > el.startOffset)
                ) {
                    return null
                }

                lparEl = HasKeyWordFromEnd(node, LPAR, offset, lparEl.prevLeaf()!!.startOffset - offset, null)
            }
        }

        if (text != null) {
            var str = text.substring(0, el.startOffset - offset)
            var count = str.toCharArray().filter { it == '"' }.count()
            if ((count and 1) == 1) return null
        }

        return el
    }

    private fun HasKeyWord(node: ASTNode, keyWord: String, offset: Int) : ASTNode? {
        var el = node.psi.findElementAt(offset)!!.node
        while (el.text != keyWord && !el.textContains('\n')) {
            el = el.nextLeaf()
        }

        return if (el.text == keyWord && !el.textContains('\n')) el else null
    }

    private fun HasKeyWordFromEnd(
        node: ASTNode,
        keyWord: IElementType,
        offset: Int,
        idx: Int,
        text: String?
    ) : ASTNode? {
        var el = node.psi.findElementAt(offset + idx)!!.node
        if (el.textContains('\n')) el = el.prevLeaf()
        while (el != null && el.elementType != keyWord && !el.textContains('\n')) {
            el = el.prevLeaf()
        }

        if (el == null || el.elementType != keyWord || el.textContains('\n')) return null

        if (text != null) {
            var str = text.substring(el.startOffset - offset, text.length)
            var count = str.toCharArray().filter { it == '"' }.count()
            if ((count and 1) == 1) return null
        }

        return el
    }

    private fun IsClass(el: ASTNode) : ASTNode? {
        var prevEl = el.prevLeaf()
        if (prevEl == null || prevEl.textContains('\n')) return el

        if (prevEl is PsiWhiteSpace) prevEl = prevEl.prevLeaf()!!
        val keys = setOf("data", "open", "enum", "sealed", "inline", "inner", "private", "public")

        return if (prevEl.text in keys) el else null
    }

    private fun FindPairedBraces(
        el: ASTNode,
        lToken: IElementType,
        rToken: IElementType,
        endEl: ASTNode
    ) : ASTNode? {
        var sEl = el
        var lNum = 0
        while (sEl.startOffset <= endEl.startOffset && (sEl.elementType != rToken || lNum > 0)) {
            if (sEl.elementType == lToken) {
                lNum += 1
            } else if (sEl.elementType == rToken) {
                lNum -= 1
            }

            sEl = sEl.nextLeaf()!!
        }

        return if (sEl.elementType == rToken && lNum == 0) sEl else null
    }

    private fun IndentLength(line: String, offset: Int) : Int {
        return FirstNotMatchChar(line, offset, ' ')
    }

    private fun FirstNotMatchChar(str: String, offset: Int, c: Char): Int {
        var idx = offset
        if (str[idx] == '\n') idx++
        while (str[idx] == c && idx < str.length) idx++

        return if (str[offset] == '\n') idx - 1 else idx
    }

    private fun GetLastLeafInLine(node: ASTNode, offset: Int) : ASTNode {
        var el = node.psi.findElementAt(offset)!!.node
        if (el.text[0] == '\n') return el.prevLeaf()!!
        while (el.nextLeaf() != null && node.text[el.nextLeaf()!!.startOffset] != '\n') {
            el = el.nextLeaf()
        }

        return el
    }

    private fun InsertRBrace(
        node: ASTNode,
        offset: Int,
        sOffset: Int,
        indent: String,
        lastEl: ASTNode
    ) : Int {
        var endOffset: Int
        var cEl = HasKeyWord(node, COMMA, offset, sOffset, null)
        while (cEl != null) {
            var lparEl = HasKeyWordFromEnd(node, LPAR, offset, cEl.startOffset - offset, null)
            if (lparEl == null) break

            var rparEl = FindPairedBraces(lparEl.nextLeaf()!!, LPAR, RPAR, lastEl)
            if (rparEl == null) return -1

            if (lparEl.startOffset > cEl.startOffset ||
                rparEl.startOffset < cEl.startOffset ||
                rparEl == lastEl
            ) {
                break
            }

            cEl = HasKeyWord(node, COMMA, offset, rparEl.startOffset - offset, null)
        }

        if (cEl == null) {
            endOffset = lastEl.nextLeaf()!!.startOffset
            (lastEl as LeafPsiElement).insertTextAfterMe("\n" + indent + "}")
        } else {
            endOffset = cEl.startOffset
            (cEl as LeafPsiElement).insertTextBeforeMe("\n" + indent + "}")
        }

        return endOffset
    }

    fun LeafElement.insertTextBeforeMe(text: String): LeafElement {
        return if (elementType == WHITE_SPACE) {
            rawReplaceWithText(text)
        } else {
            PsiWhiteSpaceImpl(text).also { w -> (psi as LeafElement).rawInsertBeforeMe(w) }
        }
    }

    fun LeafElement.insertTextAfterMe(text: String) : LeafElement {
        return if (elementType == WHITE_SPACE) {
            rawReplaceWithText(text)
        } else {
            PsiWhiteSpaceImpl(text).also { w -> (psi as LeafElement).rawInsertAfterMe(w) }
        }
    }
}
