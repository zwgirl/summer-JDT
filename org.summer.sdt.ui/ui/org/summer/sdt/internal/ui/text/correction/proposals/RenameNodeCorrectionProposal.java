/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.summer.sdt.internal.ui.text.correction.proposals;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jface.text.IDocument;
import org.summer.sdt.core.ICompilationUnit;
import org.summer.sdt.core.dom.ASTNode;
import org.summer.sdt.core.dom.CompilationUnit;
import org.summer.sdt.core.dom.NodeFinder;
import org.summer.sdt.core.dom.SimpleName;
import org.summer.sdt.internal.corext.dom.LinkedNodeFinder;
import org.summer.sdt.internal.ui.JavaPluginImages;
import org.summer.sdt.ui.SharedASTProvider;
import org.summer.sdt.ui.text.java.correction.CUCorrectionProposal;

public class RenameNodeCorrectionProposal extends CUCorrectionProposal {

	private String fNewName;
	private int fOffset;
	private int fLength;

	public RenameNodeCorrectionProposal(String name, ICompilationUnit cu, int offset, int length, String newName, int relevance) {
		super(name, cu, relevance, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));
		fOffset= offset;
		fLength= length;
		fNewName= newName;
	}

	/*(non-Javadoc)
	 * @see org.summer.sdt.internal.ui.text.correction.CUCorrectionProposal#addEdits(org.eclipse.jface.text.IDocument)
	 */
	@Override
	protected void addEdits(IDocument doc, TextEdit root) throws CoreException {
		super.addEdits(doc, root);

		// build a full AST
		CompilationUnit unit= SharedASTProvider.getAST(getCompilationUnit(), SharedASTProvider.WAIT_YES, null);

		ASTNode name= NodeFinder.perform(unit, fOffset, fLength);
		if (name instanceof SimpleName) {

			SimpleName[] names= LinkedNodeFinder.findByProblems(unit, (SimpleName) name);
			if (names != null) {
				for (int i= 0; i < names.length; i++) {
					SimpleName curr= names[i];
					root.addChild(new ReplaceEdit(curr.getStartPosition(), curr.getLength(), fNewName));
				}
				return;
			}
		}
		root.addChild(new ReplaceEdit(fOffset, fLength, fNewName));
	}
}
