// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.vfs.readText
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.LOG
import org.jetbrains.kotlin.psi.KtCodeFragment

class CodeFragmentCodegenException(val reason: Throwable) : Exception()

internal fun reportErrorWithAttachments(
    executionContext: ExecutionContext,
    codeFragment: KtCodeFragment,
    e: CodeFragmentCodegenException
) {
    val evaluationContext = executionContext.evaluationContext
    val projectName = evaluationContext.project.name
    val suspendContext = evaluationContext.suspendContext

    val file = suspendContext.activeExecutionStack?.topFrame?.sourcePosition?.file
    val fileContents = file?.readText()

    val sessionName = suspendContext.debugProcess.session.sessionName

    val debuggerContext = """
            project: $projectName
            session: $sessionName
            location: ${suspendContext.location} at ${file?.path}
        """.trimIndent()

    val suspendStackTrace = suspendContext.thread?.frames()?.joinToString(System.lineSeparator()) {
        val location = it.location()
        "${location.method()} at line ${location.lineNumber()}"
    }

    val attachments = buildList {
        add(Attachment("debugger_context.txt", debuggerContext).apply { isIncluded = true })
        add(Attachment("code_fragment.txt", codeFragment.text).apply { isIncluded = true })
        suspendStackTrace?.let {
            add(Attachment("suspend_stack_trace.txt", it).apply { isIncluded = true })
        }
        fileContents?.let {
            add(Attachment("opened_file_contents.txt", it).apply { isIncluded = true })
        }
    }

    LOG.error(
        "Error when compiling code fragment with IR evaluator. Details in attachments.",
        RuntimeExceptionWithAttachments(e.reason, *attachments.toTypedArray())
    )
}