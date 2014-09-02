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
package org.summer.sdt.internal.ui.refactoring.actions;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.summer.sdt.core.ICompilationUnit;
import org.summer.sdt.core.IJavaElement;
import org.summer.sdt.core.IMethod;
import org.summer.sdt.core.JavaModelException;
import org.summer.sdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.summer.sdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.summer.sdt.internal.corext.util.JavaModelUtil;
import org.summer.sdt.internal.ui.IJavaHelpContextIds;
import org.summer.sdt.internal.ui.JavaPlugin;
import org.summer.sdt.internal.ui.actions.ActionUtil;
import org.summer.sdt.internal.ui.actions.SelectionConverter;
import org.summer.sdt.internal.ui.javaeditor.JavaEditor;
import org.summer.sdt.internal.ui.javaeditor.JavaTextSelection;
import org.summer.sdt.internal.ui.refactoring.RefactoringMessages;
import org.summer.sdt.internal.ui.util.ExceptionHandler;
import org.summer.sdt.ui.actions.SelectionDispatchAction;

public final class MoveInstanceMethodAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the java editor
	 */
	public MoveInstanceMethodAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	public MoveInstanceMethodAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.MoveInstanceMethodAction_Move_Method);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.MOVE_ACTION);
	}

	/*
	 * @see SelectionDispatchAction#selectionChanged(IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isMoveMethodAvailable(selection));
		} catch (JavaModelException e) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);//no ui
		}
	}

	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
    }

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isMoveMethodAvailable(selection));
		} catch (CoreException e) {
			setEnabled(false);
		}
	}

	private static IMethod getSingleSelectedMethod(IStructuredSelection selection) {
		if (selection.isEmpty() || selection.size() != 1)
			return null;

		Object first= selection.getFirstElement();
		if (! (first instanceof IMethod))
			return null;
		return (IMethod) first;
	}
	/*
	 * @see SelectionDispatchAction#run(IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			Assert.isTrue(RefactoringAvailabilityTester.isMoveMethodAvailable(selection));
			IMethod method= getSingleSelectedMethod(selection);
			Assert.isNotNull(method);
			if (!ActionUtil.isEditable(fEditor, getShell(), method))
				return;
			RefactoringExecutionStarter.startMoveMethodRefactoring(method, getShell());
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, getShell(), RefactoringMessages.MoveInstanceMethodAction_dialog_title, RefactoringMessages.MoveInstanceMethodAction_unexpected_exception);
		}
 	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		try {
			run(selection, SelectionConverter.getInputAsCompilationUnit(fEditor));
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, getShell(), RefactoringMessages.MoveInstanceMethodAction_dialog_title, RefactoringMessages.MoveInstanceMethodAction_unexpected_exception);
		}
	}

	private void run(ITextSelection selection, ICompilationUnit cu) throws JavaModelException {
		Assert.isNotNull(cu);
		Assert.isTrue(selection.getOffset() >= 0);
		Assert.isTrue(selection.getLength() >= 0);

		if (!ActionUtil.isEditable(fEditor, getShell(), cu))
			return;

		IMethod method= getMethod(cu, selection);
		if (method != null) {
			RefactoringExecutionStarter.startMoveMethodRefactoring(method, getShell());
		} else {
			MessageDialog.openInformation(getShell(), RefactoringMessages.MoveInstanceMethodAction_dialog_title, RefactoringMessages.MoveInstanceMethodAction_No_reference_or_declaration);
		}
	}

	private static IMethod getMethod(ICompilationUnit cu, ITextSelection selection) throws JavaModelException {
		IJavaElement element= SelectionConverter.getElementAtOffset(cu, selection);
		if (element instanceof IMethod)
			return (IMethod) element;
		return null;
	}
}
