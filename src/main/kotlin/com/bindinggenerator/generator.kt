package com.bindinggenerator

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class generator : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)

        if (psiFile != null && editor != null) {
            WriteCommandAction.runWriteCommandAction(e.project) {
                val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
                val psiFactory = KtPsiFactory(psiFile)
                val elementsMap = linkedMapOf<String, String>()

                psiFile.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (element is KtClass) {
                            val classBody = element.getBody()
                            if (classBody == null) {
                                val emptyBody = psiFactory.createEmptyClassBody()
                                element.add(emptyBody)
                            }
                        }

                        if (element is KtClassBody) {
                            val klass = element
                            val existingPropertyNames = klass.declarations.filterIsInstance<KtProperty>()
                                .map { it.name }.toSet()

                            elementsMap.onEachIndexed{ index,(propertyName, propertyType) ->
                                if (propertyName !in existingPropertyNames) {
                                    val functionText = """
                                        var $propertyName:$propertyType
                                        @Bindable get() = _$propertyName
                                        set(value) {
                                                _$propertyName = value
                                                notifyPropertyChanged(BR.$propertyName)
                                                }
                                        """

                                    val functionElement = psiFactory.createProperty(functionText)
                                    if (index!=0)
                                        klass.addAfter(psiFactory.createNewLine(),klass.lBrace)

                                    klass.addAfter(functionElement,klass.lBrace)
                                }
                            }
                            elementsMap.clear()
                        }

                        if (element is KtParameter && element.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                            val name = element.name?.replace("_", "")
                            val type = element.typeReference?.text
                            if (name != null && type != null) {
                                if (element.text.contains("private var")) {
                                    elementsMap[name] = type
                                }
                            }
                        }

                        super.visitElement(element)
                    }
                })

                if (document != null) {
                    FileDocumentManager.getInstance().saveDocument(document)
                    PsiDocumentManager.getInstance(psiFile.project).commitDocument(document)
                }
            }
        }
    }
}
