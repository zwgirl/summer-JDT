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
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.summer.sdt.core.IJavaElement;
import org.summer.sdt.core.JavaModelException;
import org.summer.sdt.core.search.IJavaSearchScope;
import org.summer.sdt.internal.ui.IJavaHelpContextIds;
import org.summer.sdt.internal.ui.JavaPluginImages;
import org.summer.sdt.internal.ui.javaeditor.JavaEditor;
import org.summer.sdt.internal.ui.search.JavaSearchScopeFactory;
import org.summer.sdt.internal.ui.search.SearchMessages;
import org.summer.sdt.internal.ui.search.SearchUtil;
import org.summer.sdt.ui.search.ElementQuerySpecification;
import org.summer.sdt.ui.search.QuerySpecification;


/**
 * Finds references of the selected element in working sets.
 * The action is applicable to selections representing a Java element.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class FindReferencesInWorkingSetAction extends FindReferencesAction {

	private IWorkingSet[] fWorkingSets;

	/**
	 * Creates a new <code>FindReferencesInWorkingSetAction</code>. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>. The user will
	 * be prompted to select the working sets.
	 *
	 * @param site the site providing context information for this action
	 */
	public FindReferencesInWorkingSetAction(IWorkbenchSite site) {
		this(site, null);
	}

	/**
	 * Creates a new <code>FindReferencesInWorkingSetAction</code>. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site			the site providing context information for this action
	 * @param workingSets	the working sets to be used in the search
	 */
	public FindReferencesInWorkingSetAction(IWorkbenchSite site, IWorkingSet[] workingSets) {
		super(site);
		fWorkingSets= workingSets;
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public FindReferencesInWorkingSetAction(JavaEditor editor) {
		this(editor, null);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 * @param workingSets the working sets to be used in the search
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public FindReferencesInWorkingSetAction(JavaEditor editor, IWorkingSet[] workingSets) {
		super(editor);
		fWorkingSets= workingSets;
	}

	@Override
	void init() {
		setText(SearchMessages.Search_FindReferencesInWorkingSetAction_label);
		setToolTipText(SearchMessages.Search_FindReferencesInWorkingSetAction_tooltip);
		setImageDescriptor(JavaPluginImages.DESC_OBJS_SEARCH_REF);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.FIND_REFERENCES_IN_WORKING_SET_ACTION);
	}

	@Override
	QuerySpecification createQuery(IJavaElement element) throws JavaModelException, InterruptedException {
		JavaSearchScopeFactory factory= JavaSearchScopeFactory.getInstance();

		IWorkingSet[] workingSets= fWorkingSets;
		if (fWorkingSets == null) {
			workingSets= factory.queryWorkingSets();
			if (workingSets == null)
				return super.createQuery(element); // in workspace
		}
		SearchUtil.updateLRUWorkingSets(workingSets);
		IJavaSearchScope scope= factory.createJavaSearchScope(workingSets, JavaSearchScopeFactory.NO_PROJ);
		String description= factory.getWorkingSetScopeDescription(workingSets, JavaSearchScopeFactory.NO_PROJ);
		return new ElementQuerySpecification(element, getLimitTo(), scope, description);
	}
}
