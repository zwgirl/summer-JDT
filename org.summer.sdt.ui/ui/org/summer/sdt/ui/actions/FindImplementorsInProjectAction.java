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

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.summer.sdt.core.IJavaElement;
import org.summer.sdt.core.JavaModelException;
import org.summer.sdt.core.search.IJavaSearchScope;
import org.summer.sdt.internal.ui.IJavaHelpContextIds;
import org.summer.sdt.internal.ui.JavaPluginImages;
import org.summer.sdt.internal.ui.javaeditor.JavaEditor;
import org.summer.sdt.internal.ui.search.JavaSearchScopeFactory;
import org.summer.sdt.internal.ui.search.SearchMessages;
import org.summer.sdt.ui.search.ElementQuerySpecification;
import org.summer.sdt.ui.search.QuerySpecification;

/**
 * Finds implementors of the selected element in the enclosing project.
 * The action is applicable to selections representing a Java interface.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class FindImplementorsInProjectAction extends FindImplementorsAction {

	/**
	 * Creates a new <code>FindImplementorsInProjectAction</code>. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * <code>IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public FindImplementorsInProjectAction(IWorkbenchSite site) {
		super(site);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public FindImplementorsInProjectAction(JavaEditor editor) {
		super(editor);
	}

	@Override
	void init() {
		setText(SearchMessages.Search_FindImplementorsInProjectAction_label);
		setToolTipText(SearchMessages.Search_FindImplementorsInProjectAction_tooltip);
		setImageDescriptor(JavaPluginImages.DESC_OBJS_SEARCH_DECL);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.FIND_IMPLEMENTORS_IN_PROJECT_ACTION);
	}

	@Override
	QuerySpecification createQuery(IJavaElement element) throws JavaModelException {
		JavaSearchScopeFactory factory= JavaSearchScopeFactory.getInstance();
		JavaEditor editor= getEditor();

		IJavaSearchScope scope;
		String description;
		boolean isInsideJRE= factory.isInsideJRE(element);
		if (editor != null) {
			scope= factory.createJavaProjectSearchScope(editor.getEditorInput(), isInsideJRE);
			description= factory.getProjectScopeDescription(editor.getEditorInput(), isInsideJRE);
		} else {
			scope= factory.createJavaProjectSearchScope(element.getJavaProject(), isInsideJRE);
			description=  factory.getProjectScopeDescription(element.getJavaProject(), isInsideJRE);
		}
		return new ElementQuerySpecification(element, getLimitTo(), scope, description);
	}
}
