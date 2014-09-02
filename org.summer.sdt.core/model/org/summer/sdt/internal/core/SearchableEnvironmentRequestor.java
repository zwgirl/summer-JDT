/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.summer.sdt.internal.core;

import org.summer.sdt.core.IInitializer;
import org.summer.sdt.core.IJavaElement;
import org.summer.sdt.core.IJavaProject;
import org.summer.sdt.core.IPackageFragment;
import org.summer.sdt.core.IType;
import org.summer.sdt.core.JavaCore;
import org.summer.sdt.core.JavaModelException;
import org.summer.sdt.core.compiler.CharOperation;
import org.summer.sdt.internal.codeassist.ISearchRequestor;
import org.summer.sdt.internal.compiler.env.AccessRestriction;
import org.summer.sdt.internal.compiler.env.AccessRuleSet;
import org.summer.sdt.internal.compiler.env.ICompilationUnit;

/**
 * Implements <code>IJavaElementRequestor</code>, wrappering and forwarding
 * results onto a <code>org.summer.sdt.internal.codeassist.api.ISearchRequestor</code>.
 */
class SearchableEnvironmentRequestor extends JavaElementRequestor {
	/**
	 * The <code>ISearchRequestor</code> this JavaElementRequestor wraps
	 * and forwards results to.
	 */
	protected ISearchRequestor requestor;
	/**
	 * The <code>ICompilationUNit</code> this JavaElementRequestor will not
	 * accept types within.
	 */
	protected ICompilationUnit unitToSkip;

	protected IJavaProject project;

	protected NameLookup nameLookup;

	protected boolean checkAccessRestrictions;
/**
 * Constructs a SearchableEnvironmentRequestor that wraps the
 * given SearchRequestor.
 */
public SearchableEnvironmentRequestor(ISearchRequestor requestor) {
	this.requestor = requestor;
	this.unitToSkip= null;
	this.project= null;
	this.nameLookup= null;
	this.checkAccessRestrictions = false;

}
/**
 * Constructs a SearchableEnvironmentRequestor that wraps the
 * given SearchRequestor.  The requestor will not accept types in
 * the <code>unitToSkip</code>.
 */
public SearchableEnvironmentRequestor(ISearchRequestor requestor, ICompilationUnit unitToSkip, IJavaProject project, NameLookup nameLookup) {
	this.requestor = requestor;
	this.unitToSkip= unitToSkip;
	this.project= project;
	this.nameLookup = nameLookup;
	this.checkAccessRestrictions =
		!JavaCore.IGNORE.equals(project.getOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, true))
		|| !JavaCore.IGNORE.equals(project.getOption(JavaCore.COMPILER_PB_DISCOURAGED_REFERENCE, true));
}
/**
 * Do nothing, a SearchRequestor does not accept initializers
 * so there is no need to forward this results.
 *
 * @see IJavaElementRequestor
 */
public void acceptInitializer(IInitializer initializer) {
	// implements interface method
}
/**
 * @see IJavaElementRequestor
 */
public void acceptPackageFragment(IPackageFragment packageFragment) {
	this.requestor.acceptPackage(packageFragment.getElementName().toCharArray());
}
/**
 * @see IJavaElementRequestor
 */
public void acceptType(IType type) {
	try {
		if (this.unitToSkip != null && this.unitToSkip.equals(type.getCompilationUnit())){
			return;
		}
		char[] packageName = type.getPackageFragment().getElementName().toCharArray();
		boolean isBinary = type instanceof BinaryType;

		// determine associated access restriction
		AccessRestriction accessRestriction = null;

		if (this.checkAccessRestrictions && (isBinary || !type.getJavaProject().equals(this.project))) {
			PackageFragmentRoot root = (PackageFragmentRoot)type.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			ClasspathEntry entry = (ClasspathEntry) this.nameLookup.rootToResolvedEntries.get(root);
			if (entry != null) { // reverse map always contains resolved CP entry
				AccessRuleSet accessRuleSet = entry.getAccessRuleSet();
				if (accessRuleSet != null) {
					// TODO (philippe) improve char[] <-> String conversions to avoid performing them on the fly
					char[][] packageChars = CharOperation.splitOn('.', packageName);
					char[] fileWithoutExtension = type.getElementName().toCharArray();
					accessRestriction = accessRuleSet.getViolatedRestriction(CharOperation.concatWith(packageChars, fileWithoutExtension, '/'));
				}
			}
		}
		this.requestor.acceptType(packageName, type.getElementName().toCharArray(), null, type.getFlags(), accessRestriction);
	} catch (JavaModelException jme) {
		// ignore
	}
}
}