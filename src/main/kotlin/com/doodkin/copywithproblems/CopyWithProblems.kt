package com.doodkin.copywithproblems

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import java.awt.datatransfer.StringSelection

class CopyWithProblems : AnAction() {

    override fun update(event: AnActionEvent) {
        // Get required data keys
        val project = event.project
        val editor = event.getData(CommonDataKeys.EDITOR)

        // Set visibility only in the case of
        // existing project, editor, and selection
        event.presentation.isEnabledAndVisible =
            project != null && editor != null && editor.selectionModel.hasSelection()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(editor!!.document)

        if (psiFile != null) {
            val document = editor.document
            val selectionModel = editor.selectionModel
            val startOffset = selectionModel.selectionStart
            val endOffset = selectionModel.selectionEnd

            val lineErrors: MutableMap<Int, MutableList<String>> = HashMap()

            DaemonCodeAnalyzerEx.processHighlights(
                document, project, null,
                startOffset, endOffset
            ) { highlightInfo: HighlightInfo ->
                val line = document.getLineNumber(highlightInfo.startOffset)
                val severity = highlightInfo.severity.myName
                val description = highlightInfo.description
                // Only process non-null descriptions and meaningful severities
                if (description != null && severity != "SYMBOL_TYPE_SEVERITY" && severity != "INFORMATION") {
                    val descriptionNoBr=description.replace("\r\n","   ").replace("\r","   ").replace("\n","   ")
                    val errorDescription = "$severity: $descriptionNoBr"
                    lineErrors.computeIfAbsent(line) { ArrayList() }.add(errorDescription)
                }
                true  // Continue processing highlights
            }

            val resultBuffer = StringBuilder()
            val startLine = document.getLineNumber(startOffset)
            val endLine = document.getLineNumber(endOffset)

            for (line in startLine..endLine) {
                val lineText = document.getText(
                    TextRange(
                        document.getLineStartOffset(line),
                        document.getLineEndOffset(line)
                    )
                )
                resultBuffer.append(lineText)
                lineErrors[line]?.let {
                    resultBuffer.append(" // ").append(it.asReversed().joinToString(" ; "))
                }
                resultBuffer.append('\n')
            }

            if (resultBuffer.isNotEmpty()) {
                ApplicationManager.getApplication().invokeLater {
                    val selection = StringSelection(resultBuffer.toString())
                    CopyPasteManager.getInstance().setContents(selection)
                }
            }
        }
    }
}