/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.summer.sdt.internal.ui.text.correction.proposals;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.summer.sdt.core.ICompilationUnit;
import org.summer.sdt.core.IJavaProject;
import org.summer.sdt.core.dom.AST;
import org.summer.sdt.core.dom.ASTNode;
import org.summer.sdt.core.dom.IExtendedModifier;
import org.summer.sdt.core.dom.IMethodBinding;
import org.summer.sdt.core.dom.ITypeBinding;
import org.summer.sdt.core.dom.Modifier;
import org.summer.sdt.core.dom.SimpleName;
import org.summer.sdt.core.dom.SingleVariableDeclaration;
import org.summer.sdt.core.dom.Type;
import org.summer.sdt.core.dom.TypeParameter;
import org.summer.sdt.core.dom.rewrite.ASTRewrite;
import org.summer.sdt.core.dom.rewrite.ImportRewrite;
import org.summer.sdt.internal.corext.codemanipulation.StubUtility;
import org.summer.sdt.internal.ui.JavaPlugin;
import org.summer.sdt.internal.ui.viewsupport.JavaElementImageProvider;

public class NewDefiningMethodProposal extends AbstractMethodCorrectionProposal {

	private final IMethodBinding fMethod;
	private final String[] fParamNames;

	public NewDefiningMethodProposal(String label, ICompilationUnit targetCU, ASTNode invocationNode, ITypeBinding binding, IMethodBinding method, String[] paramNames, int relevance) {
		super(label,targetCU,invocationNode,binding,relevance,null);
		fMethod= method;
		fParamNames= paramNames;

		ImageDescriptor desc= JavaElementImageProvider.getMethodImageDescriptor(binding.isInterface() || binding.isAnnotation(), method.getModifiers());
		setImage(JavaPlugin.getImageDescriptorRegistry().get(desc));
	}

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.text.correction.proposals.AbstractMethodCorrectionProposal#isConstructor()
	 */
	@Override
	protected boolean isConstructor() {
		return fMethod.isConstructor();
	}

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.text.correction.proposals.AbstractMethodCorrectionProposal#addNewParameters(org.summer.sdt.core.dom.rewrite.ASTRewrite, java.util.List, java.util.List)
	 */
	@Override
	protected void addNewParameters(ASTRewrite rewrite, List<String> takenNames, List<SingleVariableDeclaration> params) throws CoreException {
		AST ast= rewrite.getAST();
		ImportRewrite importRewrite= getImportRewrite();
		ITypeBinding[] bindings= fMethod.getParameterTypes();

		IJavaProject project= getCompilationUnit().getJavaProject();
		String[][] paramNames= StubUtility.suggestArgumentNamesWithProposals(project, fParamNames);

		for (int i= 0; i < bindings.length; i++) {
			ITypeBinding curr= bindings[i];

			String[] proposedNames= paramNames[i];

			SingleVariableDeclaration newParam= ast.newSingleVariableDeclaration();

			newParam.setType(importRewrite.addImport(curr, ast));
			newParam.setName(ast.newSimpleName(proposedNames[0]));

			params.add(newParam);

			String groupId= "arg_name_" + i; //$NON-NLS-1$
			addLinkedPosition(rewrite.track(newParam.getName()), false, groupId);

			for (int k= 0; k < proposedNames.length; k++) {
				addLinkedPositionProposal(groupId, proposedNames[k], null);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.text.correction.proposals.AbstractMethodCorrectionProposal#getNewName(org.summer.sdt.core.dom.rewrite.ASTRewrite)
	 */
	@Override
	protected SimpleName getNewName(ASTRewrite rewrite) {
		AST ast= rewrite.getAST();
		SimpleName nameNode= ast.newSimpleName(fMethod.getName());
		return nameNode;
	}

	private int evaluateModifiers() {
		if (getSenderBinding().isInterface()) {
			return 0;
		} else {
			int modifiers= fMethod.getModifiers();
			if (Modifier.isPrivate(modifiers)) {
				modifiers |= Modifier.PROTECTED;
			}
			return modifiers & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.ABSTRACT | Modifier.STRICTFP);
		}
	}

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.text.correction.proposals.AbstractMethodCorrectionProposal#addNewModifiers(org.summer.sdt.core.dom.rewrite.ASTRewrite, org.summer.sdt.core.dom.ASTNode, java.util.List)
	 */
	@Override
	protected void addNewModifiers(ASTRewrite rewrite, ASTNode targetTypeDecl, List<IExtendedModifier> modifiers) {
		modifiers.addAll(rewrite.getAST().newModifiers(evaluateModifiers()));
	}

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.text.correction.proposals.AbstractMethodCorrectionProposal#getNewMethodType(org.summer.sdt.core.dom.rewrite.ASTRewrite)
	 */
	@Override
	protected Type getNewMethodType(ASTRewrite rewrite) throws CoreException {
		return getImportRewrite().addImport(fMethod.getReturnType(), rewrite.getAST());
	}

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.text.correction.proposals.AbstractMethodCorrectionProposal#addNewExceptions(org.summer.sdt.core.dom.rewrite.ASTRewrite, java.util.List)
	 */
	@Override
	protected void addNewExceptions(ASTRewrite rewrite, List<Type> exceptions) throws CoreException {
		AST ast= rewrite.getAST();
		ImportRewrite importRewrite= getImportRewrite();
		ITypeBinding[] bindings= fMethod.getExceptionTypes();
		for (int i= 0; i < bindings.length; i++) {
			Type newType= importRewrite.addImport(bindings[i], ast);
			exceptions.add(newType);

			addLinkedPosition(rewrite.track(newType), false, "exc_type_" + i); //$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.text.correction.proposals.AbstractMethodCorrectionProposal#addNewTypeParameters(org.summer.sdt.core.dom.rewrite.ASTRewrite, java.util.List, java.util.List)
	 */
	@Override
	protected void addNewTypeParameters(ASTRewrite rewrite, List<String> takenNames, List<TypeParameter> params) throws CoreException {

	}

}
