package com.bindinggenerator

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.awt.event.ItemEvent
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

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
                            val newMap= mutableMapOf<String,String>()
                            elementsMap.forEach {
                                if (it.key !in existingPropertyNames)
                                    newMap[it.key]=it.value
                            }

                            if (newMap.isEmpty())
                                showNoPropertiesNotification(e.project)
                            else {
                                val dialog = PropertySelectionDialog(newMap)
                                val result =dialog.showAndGet()
                                if (result) {
                                    dialog.getSelectedProperties()
                                        .onEachIndexed { index, (propertyName, propertyType) ->
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
                                                if (index != 0)
                                                    klass.addAfter(psiFactory.createNewLine(), klass.lBrace)

                                                klass.addAfter(functionElement, klass.lBrace)
                                            }
                                        }
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
    private fun showNoPropertiesNotification(project: Project?) {
        val notification = Notification(
            "com.bindingGenerator.BindingGenerator",
            "No Properties Available",
            "There are no properties to select.",
            NotificationType.INFORMATION
        )
        Notifications.Bus.notify(notification, project)
    }

    class PropertySelectionDialog(private val availableProperties: Map<String,String>) : DialogWrapper(true) {
        private val selectedProperties = mutableMapOf<String,String>()
        private val checkboxes = mutableListOf<JCheckBox>()

        init {
            title = "Select Properties to Add"
            init()
        }

        override fun createCenterPanel(): JComponent? {
            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            val selectAllCheckbox = JCheckBox("Select All")
            selectAllCheckbox.addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    for (checkbox in checkboxes) {
                        checkbox.isSelected = true
                    }
                } else if (e.stateChange == ItemEvent.DESELECTED) {
                    for (checkbox in checkboxes) {
                        checkbox.isSelected = false
                    }
                }
            }
            panel.add(selectAllCheckbox)

            for (property in availableProperties) {
                val checkbox = JCheckBox(property.key)
                checkboxes.add(checkbox)
                panel.add(checkbox)
            }
            selectAllCheckbox.isSelected=true

            return panel
        }

        fun getSelectedProperties(): Map<String,String> {
            selectedProperties.clear()
            for (checkbox in checkboxes) {
                if (checkbox.isSelected) {
                    val value=availableProperties[checkbox.text]
                    if (value!=null)
                        selectedProperties[checkbox.text] = value
                }
            }
            return selectedProperties
        }
    }

}
