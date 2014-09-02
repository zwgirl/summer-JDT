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

package org.summer.sdt.internal.ui.javaeditor;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.texteditor.ITextEditor;
import org.summer.sdt.core.ITypeRoot;
import org.summer.sdt.core.dom.ASTNode;
import org.summer.sdt.core.dom.CompilationUnit;
import org.summer.sdt.core.dom.NodeFinder;
import org.summer.sdt.core.dom.QualifiedName;
import org.summer.sdt.core.dom.SimpleName;
import org.summer.sdt.core.dom.StringLiteral;
import org.summer.sdt.internal.corext.refactoring.nls.AccessorClassReference;
import org.summer.sdt.internal.corext.refactoring.nls.NLSHintHelper;
import org.summer.sdt.ui.JavaUI;
import org.summer.sdt.ui.SharedASTProvider;



/**
 * NLS hyperlink detector.
 *
 * @since 3.1
 */
public class NLSKeyHyperlinkDetector extends AbstractHyperlinkDetector {


	/*
	 * @see org.eclipse.jface.text.hyperlink.IHyperlinkDetector#detectHyperlinks(org.eclipse.jface.text.ITextViewer, org.eclipse.jface.text.IRegion, boolean)
	 */
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		ITextEditor textEditor= (ITextEditor)getAdapter(ITextEditor.class);
		if (region == null || textEditor == null)
			return null;

		IEditorSite site= textEditor.getEditorSite();
		if (site == null)
			return null;

		ITypeRoot javaElement= getInputJavaElement(textEditor);
		if (javaElement == null)
			return null;

		CompilationUnit ast= SharedASTProvider.getAST(javaElement, SharedASTProvider.WAIT_NO, null);
		if (ast == null)
			return null;

		ASTNode node= NodeFinder.perform(ast, region.getOffset(), 1);
		if (!(node instanceof StringLiteral)  && !(node instanceof SimpleName))
			return null;

		if (node.getLocationInParent() == QualifiedName.QUALIFIER_PROPERTY)
			return null;

		IRegion nlsKeyRegion= new Region(node.getStartPosition(), node.getLength());
		AccessorClassReference ref= NLSHintHelper.getAccessorClassReference(ast, nlsKeyRegion);
		if (ref == null)
			return null;
		String keyName= null;
		if (node instanceof StringLiteral) {
			keyName= ((StringLiteral)node).getLiteralValue();
		} else {
			keyName= ((SimpleName)node).getIdentifier();
		}
		if (keyName != null)
			return new IHyperlink[] {new NLSKeyHyperlink(nlsKeyRegion, keyName, ref, textEditor)};

		return null;
	}

	private ITypeRoot getInputJavaElement(ITextEditor editor) {
		return JavaUI.getEditorInputTypeRoot(editor.getEditorInput());
	}

}
