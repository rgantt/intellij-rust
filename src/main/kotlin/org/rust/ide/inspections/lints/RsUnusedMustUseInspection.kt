/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.ide.annotator.getFunctionCallContext
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.implLookupAndKnownItems
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type

private fun RsExpr.returnsStdResult(): Boolean {
    val (_, knownItems) = implLookupAndKnownItems
    val type = type as? TyAdt ?: return false
    return type.item == knownItems.Result
}

private class FixAddLetUnderscore : LocalQuickFix {
    override fun getFamilyName() = RsBundle.message("inspection.UnusedMustUse.FixAddLetUnderscore.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val stmt = descriptor.psiElement as RsExprStmt
        stmt.replace(RsPsiFactory(project).createLetDeclaration("_", stmt.expr))
    }
}

private class FixAddUnwrap : LocalQuickFix {
    override fun getFamilyName() = RsBundle.message("inspection.UnusedMustUse.FixAddUnwrap.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val stmt = descriptor.psiElement as RsExprStmt
        stmt.expr.replace(RsPsiFactory(project).createExpression("${stmt.expr.text}.unwrap()"))
    }
}

private class FixAddExpect(anchor: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(anchor) {
    override fun getFamilyName() = RsBundle.message("inspection.UnusedMustUse.FixAddExpect.family.name")
    override fun getText() = familyName

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val dotExpr = RsPsiFactory(project).createExpression("${startElement.text}.expect(\"\")")
        val newDotExpr = startElement.replace(dotExpr) as RsDotExpr
        val expectArgs = newDotExpr.methodCall?.valueArgumentList?.exprList
        val stringLiteral = expectArgs?.singleOrNull() as RsLitExpr
        val template = editor?.newTemplateBuilder(newDotExpr) ?: return
        val rangeWithoutQuotes = TextRange(1, stringLiteral.textRange.length - 1)
        template.replaceElement(stringLiteral, rangeWithoutQuotes, "TODO: panic message")
        template.runInline()
    }
}

private class InspectionResult(val description: String, val fixes: List<LocalQuickFix>)

private fun inspectAndProposeFixes(expr: RsExpr): InspectionResult? {
    val mustUseAttrName = "must_use"
    val type = expr.type as? TyAdt
    val func = when (expr) {
        is RsDotExpr -> expr.methodCall?.getFunctionCallContext()?.function
        is RsCallExpr -> expr.getFunctionCallContext()?.function
        else -> null
    }
    val attrType = type?.item?.findFirstMetaItem(mustUseAttrName)
    val attrFunc = func?.findFirstMetaItem(mustUseAttrName)
    val description = when {
        attrType != null -> RsBundle.message("inspection.UnusedMustUse.description.type.attribute", type)
        attrFunc != null -> RsBundle.message("inspection.UnusedMustUse.description.function.attribute", func.name.toString())
        else -> return null
    }
    val fixes: MutableList<LocalQuickFix> = mutableListOf(FixAddLetUnderscore())
    if (expr.returnsStdResult()) {
        fixes += FixAddExpect(expr)
        fixes += FixAddUnwrap()
    }
    return InspectionResult(description, fixes)
}

/** Analogue of rustc's unused_must_use. See also [RsDoubleMustUseInspection]. */
class RsUnusedMustUseInspection : RsLintInspection() {
    override fun getLint(element: PsiElement) = RsLint.UnusedMustUse

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitExprStmt(o: RsExprStmt) {
            super.visitExprStmt(o)
            val problem = inspectAndProposeFixes(o.expr)
            if (problem != null) {
                holder.registerLintProblem(o, problem.description, fixes=problem.fixes)
            }
        }
    }
}
