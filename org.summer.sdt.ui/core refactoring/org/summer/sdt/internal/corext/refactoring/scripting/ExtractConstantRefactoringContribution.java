/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.summer.sdt.internal.corext.refactoring.scripting;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.summer.sdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.summer.sdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.summer.sdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.summer.sdt.internal.corext.refactoring.code.ExtractConstantRefactoring;

/**
 * Refactoring contribution for the extract constant refactoring.
 *
 * @since 3.2
 */
public final class ExtractConstantRefactoringContribution extends JavaUIRefactoringContribution {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final Refactoring createRefactoring(JavaRefactoringDescriptor descriptor, RefactoringStatus status) throws CoreException {
		JavaRefactoringArguments arguments= new JavaRefactoringArguments(descriptor.getProject(), retrieveArgumentMap(descriptor));
		return new ExtractConstantRefactoring(arguments, status);
	}

	@Override
	public RefactoringDescriptor createDescriptor() {
		return RefactoringSignatureDescriptorFactory.createExtractConstantDescriptor();
	}

	@Override
	public RefactoringDescriptor createDescriptor(String id, String project, String description, String comment, Map arguments, int flags) {
		return RefactoringSignatureDescriptorFactory.createExtractConstantDescriptor(project, description, comment, arguments, flags);
	}
}
