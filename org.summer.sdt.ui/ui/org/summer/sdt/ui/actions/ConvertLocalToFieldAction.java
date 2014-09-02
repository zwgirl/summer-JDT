/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.summer.sdt.ui.actions;

import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.PlatformUI;
import org.summer.sdt.core.ICompilationUnit;
import org.summer.sdt.core.JavaModelException;
import org.summer.sdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.summer.sdt.internal.corext.refactoring.code.PromoteTempToFieldRefactoring;
import org.summer.sdt.internal.ui.IJavaHelpContextIds;
import org.summer.sdt.internal.ui.actions.ActionUtil;
import org.summer.sdt.internal.ui.actions.SelectionConverter;
import org.summer.sdt.internal.ui.javaeditor.JavaEditor;
import org.summer.sdt.internal.ui.javaeditor.JavaTextSelection;
import org.summer.sdt.internal.ui.refactoring.PromoteTempWizard;
import org.summer.sdt.internal.ui.refactoring.RefactoringMessages;
import org.summer.sdt.internal.ui.refactoring.actions.RefactoringStarter;
import org.summer.sdt.ui.refactoring.RefactoringSaveHelper;

/**
 * Action to convert a local variable to a field.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.1
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ConvertLocalToFieldAction extends SelectionDispatchAction {

	private final JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 *
	 * @param editor the java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ConvertLocalToFieldAction(JavaEditor editor) {
		super(editor.getEditorSite());
		setText(RefactoringMessages.ConvertLocalToField_label);
		fEditor= editor;
		setEnabled(SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.PROMOTE_TEMP_TO_FIELD_ACTION);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(fEditor != null && SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * @param selection the Java text selection (internal type)
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isPromoteTempAvailable(selection));
		} catch (JavaModelException e) {
			setEnabled(false);
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		if (!ActionUtil.isEditable(fEditor))
			return;
		ICompilationUnit cunit= SelectionConverter.getInputAsCompilationUnit(fEditor);
		PromoteTempToFieldRefactoring refactoring= new PromoteTempToFieldRefactoring(cunit, selection.getOffset(), selection.getLength());
		new RefactoringStarter().activate(new PromoteTempWizard(refactoring), getShell(), RefactoringMessages.ConvertLocalToField_title, RefactoringSaveHelper.SAVE_NOTHING);
	}
}
