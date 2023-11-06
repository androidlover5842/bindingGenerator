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
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class generator : AnAction() {
    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile != null) {
            val psiClass = PsiTreeUtil.findChildOfType(psiFile, KtClass::class.java)
            e.presentation.isEnabledAndVisible = psiClass != null && psiClass.isData()
        } else {
            e.presentation.isEnabledAndVisible = false
        }
    }
    override fun actionPerformed(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile != null) {
            generationCode(e,psiFile)
        }
    }

    private fun generationCode(e: AnActionEvent,psiFile:PsiFile){
        val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
        val psiFactory = KtPsiFactory(psiFile)
        val elementsMap = linkedMapOf<String, String>()

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtClass) {
                    val classBody = element.getBody()
                    if (classBody == null) {
                        val emptyBody = psiFactory.createEmptyClassBody()
                        WriteCommandAction.runWriteCommandAction(e.project) {
                            element.add(emptyBody)
                        }

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
                        if (result && dialog.getSelectedProperties().isNotEmpty()) {
                            WriteCommandAction.runWriteCommandAction(e.project) {
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
                    }
                    elementsMap.clear()
                }

                if (element is KtParameter && element.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                    val name = element.name?.replace("_", "")
                    val type = element.typeReference?.text
                    if (name != null && type != null && element.name?.startsWith("_")==true) {
                        if (element.text.contains("private var")) {
                            elementsMap[name] = type
                        }
                    }
                }

                super.visitElement(element)
            }
        })
        WriteCommandAction.runWriteCommandAction(e.project) {
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
    private val selectAllCheckbox = JCheckBox("Select All")

    init {
        title = "Select Properties to Add"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        selectAllCheckbox.addActionListener {
            checkboxes.forEach { it.isSelected = selectAllCheckbox.isSelected }
        }
        panel.add(selectAllCheckbox)

        for (property in availableProperties) {
            val checkbox = JCheckBox(property.key)
            checkbox.isSelected=true
            checkboxes.add(checkbox)
            checkbox.addActionListener {  selectAllCheckbox.isSelected=checkboxes.all {newIT-> newIT.isSelected }}
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