/*******************************************************************************
 * Copyright (c) 2012, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.summer.sdt.internal.ui.text.java;

import org.summer.sdt.core.CompletionProposal;
import org.summer.sdt.core.ICompilationUnit;
import org.summer.sdt.core.IJavaProject;
import org.summer.sdt.ui.text.java.JavaContentAssistInvocationContext;

public class LazyPackageCompletionProposal extends LazyJavaCompletionProposal {

	private final ICompilationUnit fCompilationUnit;

	public LazyPackageCompletionProposal(CompletionProposal proposal, JavaContentAssistInvocationContext context) {
		super(proposal, context);
		fCompilationUnit= context.getCompilationUnit();
	}

	@Override
	protected ProposalInfo computeProposalInfo() {

		IJavaProject project;
		if (fCompilationUnit != null)
			project= fCompilationUnit.getJavaProject();
		else
			project= fInvocationContext.getProject();
		if (project != null) {
			return new PackageProposalInfo(project, fProposal);
		}

		return super.computeProposalInfo();
	}

}