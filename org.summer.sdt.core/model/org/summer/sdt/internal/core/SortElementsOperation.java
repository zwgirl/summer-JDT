/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Alex Blewitt - alex_blewitt@yahoo.com https://bugs.eclipse.org/bugs/show_bug.cgi?id=171066
 *******************************************************************************/
package org.summer.sdt.internal.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.summer.sdt.core.IBuffer;
import org.summer.sdt.core.ICompilationUnit;
import org.summer.sdt.core.IJavaElement;
import org.summer.sdt.core.IJavaModelStatus;
import org.summer.sdt.core.IJavaModelStatusConstants;
import org.summer.sdt.core.JavaModelException;
import org.summer.sdt.core.compiler.CharOperation;
import org.summer.sdt.core.dom.ASTNode;
import org.summer.sdt.core.dom.ASTParser;
import org.summer.sdt.core.dom.ASTVisitor;
import org.summer.sdt.core.dom.AbstractTypeDeclaration;
import org.summer.sdt.core.dom.AnnotationTypeDeclaration;
import org.summer.sdt.core.dom.AnonymousClassDeclaration;
import org.summer.sdt.core.dom.BodyDeclaration;
import org.summer.sdt.core.dom.EnumConstantDeclaration;
import org.summer.sdt.core.dom.EnumDeclaration;
import org.summer.sdt.core.dom.TypeDeclaration;
import org.summer.sdt.core.dom.rewrite.ASTRewrite;
import org.summer.sdt.core.dom.rewrite.ListRewrite;
import org.summer.sdt.core.util.CompilationUnitSorter;
import org.summer.sdt.internal.compiler.impl.CompilerOptions;
import org.summer.sdt.internal.core.util.Messages;

/**
 * This operation is used to sort elements in a compilation unit according to
 * certain criteria.
 *
 * @since 2.1
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SortElementsOperation extends JavaModelOperation {
	public static final String CONTAINS_MALFORMED_NODES = "malformed"; //$NON-NLS-1$

	Comparator comparator;
	int[] positions;
    int apiLevel;

	/**
	 * Constructor for SortElementsOperation.
     *
     * @param level the AST API level; one of the AST LEVEL constants
	 * @param elements
	 * @param positions
	 * @param comparator
	 */
	public SortElementsOperation(int level, IJavaElement[] elements, int[] positions, Comparator comparator) {
		super(elements);
		this.comparator = comparator;
        this.positions = positions;
        this.apiLevel = level;
	}

	/**
	 * Returns the amount of work for the main task of this operation for
	 * progress reporting.
	 */
	protected int getMainAmountOfWork(){
		return this.elementsToProcess.length;
	}

	boolean checkMalformedNodes(ASTNode node) {
		Object property = node.getProperty(CONTAINS_MALFORMED_NODES);
		if (property == null) return false;
		return ((Boolean) property).booleanValue();
	}

	protected boolean isMalformed(ASTNode node) {
		return (node.getFlags() & ASTNode.MALFORMED) != 0;
	}

	/**
	 * @see org.summer.sdt.internal.core.JavaModelOperation#executeOperation()
	 */
	protected void executeOperation() throws JavaModelException {
		try {
			beginTask(Messages.operation_sortelements, getMainAmountOfWork());
			CompilationUnit copy = (CompilationUnit) this.elementsToProcess[0];
			ICompilationUnit unit = copy.getPrimary();
			IBuffer buffer = copy.getBuffer();
			if (buffer  == null) {
				return;
			}
			char[] bufferContents = buffer.getCharacters();
			String result = processElement(unit, bufferContents);
			if (!CharOperation.equals(result.toCharArray(), bufferContents)) {
				copy.getBuffer().setContents(result);
			}
			worked(1);
		} finally {
			done();
		}
	}

	/**
	 * Calculates the required text edits to sort the <code>unit</code>
	 * @param group
	 * @return the edit or null if no sorting is required
	 */
	public TextEdit calculateEdit(org.summer.sdt.core.dom.CompilationUnit unit, TextEditGroup group) throws JavaModelException {
		if (this.elementsToProcess.length != 1)
			throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.NO_ELEMENTS_TO_PROCESS));

		if (!(this.elementsToProcess[0] instanceof ICompilationUnit))
			throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INVALID_ELEMENT_TYPES, this.elementsToProcess[0]));

		try {
			beginTask(Messages.operation_sortelements, getMainAmountOfWork());

			ICompilationUnit cu= (ICompilationUnit)this.elementsToProcess[0];
			String content= cu.getBuffer().getContents();
			ASTRewrite rewrite= sortCompilationUnit(unit, group);
			if (rewrite == null) {
				return null;
			}

			Document document= new Document(content);
			return rewrite.rewriteAST(document, cu.getJavaProject().getOptions(true));
		} finally {
			done();
		}
	}

	/**
	 * Method processElement.
	 * @param unit
	 * @param source
	 */
	private String processElement(ICompilationUnit unit, char[] source) {
		Document document = new Document(new String(source));
		CompilerOptions options = new CompilerOptions(unit.getJavaProject().getOptions(true));
		ASTParser parser = ASTParser.newParser(this.apiLevel);
		parser.setCompilerOptions(options.getMap());
		parser.setSource(source);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
		org.summer.sdt.core.dom.CompilationUnit ast = (org.summer.sdt.core.dom.CompilationUnit) parser.createAST(null);

		ASTRewrite rewriter= sortCompilationUnit(ast, null);
		if (rewriter == null)
			return document.get();

		TextEdit edits = rewriter.rewriteAST(document, unit.getJavaProject().getOptions(true));

		RangeMarker[] markers = null;
		if (this.positions != null) {
			markers = new RangeMarker[this.positions.length];
			for (int i = 0, max = this.positions.length; i < max; i++) {
				markers[i]= new RangeMarker(this.positions[i], 0);
				insert(edits, markers[i]);
			}
		}
		try {
			edits.apply(document, TextEdit.UPDATE_REGIONS);
			if (this.positions != null) {
				for (int i= 0, max = markers.length; i < max; i++) {
					this.positions[i]= markers[i].getOffset();
				}
			}
		} catch (BadLocationException e) {
			// ignore
		}
		return document.get();
	}


	private ASTRewrite sortCompilationUnit(org.summer.sdt.core.dom.CompilationUnit ast, final TextEditGroup group) {
		ast.accept(new ASTVisitor() {
			public boolean visit(org.summer.sdt.core.dom.CompilationUnit compilationUnit) {
				List types = compilationUnit.types();
				for (Iterator iter = types.iterator(); iter.hasNext();) {
					AbstractTypeDeclaration typeDeclaration = (AbstractTypeDeclaration) iter.next();
					typeDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, new Integer(typeDeclaration.getStartPosition()));
					compilationUnit.setProperty(CONTAINS_MALFORMED_NODES, Boolean.valueOf(isMalformed(typeDeclaration)));
				}
				return true;
			}
			public boolean visit(AnnotationTypeDeclaration annotationTypeDeclaration) {
				List bodyDeclarations = annotationTypeDeclaration.bodyDeclarations();
				for (Iterator iter = bodyDeclarations.iterator(); iter.hasNext();) {
					BodyDeclaration bodyDeclaration = (BodyDeclaration) iter.next();
					bodyDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, new Integer(bodyDeclaration.getStartPosition()));
					annotationTypeDeclaration.setProperty(CONTAINS_MALFORMED_NODES, Boolean.valueOf(isMalformed(bodyDeclaration)));
				}
				return true;
			}

			public boolean visit(AnonymousClassDeclaration anonymousClassDeclaration) {
				List bodyDeclarations = anonymousClassDeclaration.bodyDeclarations();
				for (Iterator iter = bodyDeclarations.iterator(); iter.hasNext();) {
					BodyDeclaration bodyDeclaration = (BodyDeclaration) iter.next();
					bodyDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, new Integer(bodyDeclaration.getStartPosition()));
					anonymousClassDeclaration.setProperty(CONTAINS_MALFORMED_NODES, Boolean.valueOf(isMalformed(bodyDeclaration)));
				}
				return true;
			}

			public boolean visit(TypeDeclaration typeDeclaration) {
				List bodyDeclarations = typeDeclaration.bodyDeclarations();
				for (Iterator iter = bodyDeclarations.iterator(); iter.hasNext();) {
					BodyDeclaration bodyDeclaration = (BodyDeclaration) iter.next();
					bodyDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, new Integer(bodyDeclaration.getStartPosition()));
					typeDeclaration.setProperty(CONTAINS_MALFORMED_NODES, Boolean.valueOf(isMalformed(bodyDeclaration)));
				}
				return true;
			}

			public boolean visit(EnumDeclaration enumDeclaration) {
				List bodyDeclarations = enumDeclaration.bodyDeclarations();
				for (Iterator iter = bodyDeclarations.iterator(); iter.hasNext();) {
					BodyDeclaration bodyDeclaration = (BodyDeclaration) iter.next();
					bodyDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, new Integer(bodyDeclaration.getStartPosition()));
					enumDeclaration.setProperty(CONTAINS_MALFORMED_NODES, Boolean.valueOf(isMalformed(bodyDeclaration)));
				}
				List enumConstants = enumDeclaration.enumConstants();
				for (Iterator iter = enumConstants.iterator(); iter.hasNext();) {
					EnumConstantDeclaration enumConstantDeclaration = (EnumConstantDeclaration) iter.next();
					enumConstantDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, new Integer(enumConstantDeclaration.getStartPosition()));
					enumDeclaration.setProperty(CONTAINS_MALFORMED_NODES, Boolean.valueOf(isMalformed(enumConstantDeclaration)));
				}
				return true;
			}
		});

		final ASTRewrite rewriter= ASTRewrite.create(ast.getAST());
		final boolean[] hasChanges= new boolean[] {false};

		ast.accept(new ASTVisitor() {

			private void sortElements(List elements, ListRewrite listRewrite) {
				if (elements.size() == 0)
					return;

				final List myCopy = new ArrayList();
				myCopy.addAll(elements);
				Collections.sort(myCopy, SortElementsOperation.this.comparator);

				for (int i = 0; i < elements.size(); i++) {
					ASTNode oldNode= (ASTNode) elements.get(i);
					ASTNode newNode= (ASTNode) myCopy.get(i);
					if (oldNode != newNode) {
						listRewrite.replace(oldNode, rewriter.createMoveTarget(newNode), group);
						hasChanges[0]= true;
					}
				}
			}

			public boolean visit(org.summer.sdt.core.dom.CompilationUnit compilationUnit) {
				if (checkMalformedNodes(compilationUnit)) {
					return true; // abort sorting of current element
				}

				sortElements(compilationUnit.types(), rewriter.getListRewrite(compilationUnit, org.summer.sdt.core.dom.CompilationUnit.TYPES_PROPERTY));
				return true;
			}

			public boolean visit(AnnotationTypeDeclaration annotationTypeDeclaration) {
				if (checkMalformedNodes(annotationTypeDeclaration)) {
					return true; // abort sorting of current element
				}

				sortElements(annotationTypeDeclaration.bodyDeclarations(), rewriter.getListRewrite(annotationTypeDeclaration, AnnotationTypeDeclaration.BODY_DECLARATIONS_PROPERTY));
				return true;
			}

			public boolean visit(AnonymousClassDeclaration anonymousClassDeclaration) {
				if (checkMalformedNodes(anonymousClassDeclaration)) {
					return true; // abort sorting of current element
				}

				sortElements(anonymousClassDeclaration.bodyDeclarations(), rewriter.getListRewrite(anonymousClassDeclaration, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY));
				return true;
			}

			public boolean visit(TypeDeclaration typeDeclaration) {
				if (checkMalformedNodes(typeDeclaration)) {
					return true; // abort sorting of current element
				}

				sortElements(typeDeclaration.bodyDeclarations(), rewriter.getListRewrite(typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY));
				return true;
			}

			public boolean visit(EnumDeclaration enumDeclaration) {
				if (checkMalformedNodes(enumDeclaration)) {
					return true; // abort sorting of current element
				}

				sortElements(enumDeclaration.bodyDeclarations(), rewriter.getListRewrite(enumDeclaration, EnumDeclaration.BODY_DECLARATIONS_PROPERTY));
				sortElements(enumDeclaration.enumConstants(), rewriter.getListRewrite(enumDeclaration, EnumDeclaration.ENUM_CONSTANTS_PROPERTY));
				return true;
			}
		});

		if (!hasChanges[0])
			return null;

		return rewriter;
	}

	/**
	 * Possible failures:
	 * <ul>
	 *  <li>NO_ELEMENTS_TO_PROCESS - the compilation unit supplied to the operation is <code>null</code></li>.
	 *  <li>INVALID_ELEMENT_TYPES - the supplied elements are not an instance of IWorkingCopy</li>.
	 * </ul>
	 * @return IJavaModelStatus
	 */
	public IJavaModelStatus verify() {
		if (this.elementsToProcess.length != 1) {
			return new JavaModelStatus(IJavaModelStatusConstants.NO_ELEMENTS_TO_PROCESS);
		}
		if (this.elementsToProcess[0] == null) {
			return new JavaModelStatus(IJavaModelStatusConstants.NO_ELEMENTS_TO_PROCESS);
		}
		if (!(this.elementsToProcess[0] instanceof ICompilationUnit) || !((ICompilationUnit) this.elementsToProcess[0]).isWorkingCopy()) {
			return new JavaModelStatus(IJavaModelStatusConstants.INVALID_ELEMENT_TYPES, this.elementsToProcess[0]);
		}
		return JavaModelStatus.VERIFIED_OK;
	}

	public static void insert(TextEdit parent, TextEdit edit) {
		if (!parent.hasChildren()) {
			parent.addChild(edit);
			return;
		}
		TextEdit[] children= parent.getChildren();
		// First dive down to find the right parent.
		for (int i= 0; i < children.length; i++) {
			TextEdit child= children[i];
			if (covers(child, edit)) {
				insert(child, edit);
				return;
			}
		}
		// We have the right parent. Now check if some of the children have to
		// be moved under the new edit since it is covering it.
		for (int i= children.length - 1; i >= 0; i--) {
			TextEdit child= children[i];
			if (covers(edit, child)) {
				parent.removeChild(i);
				edit.addChild(child);
			}
		}
		parent.addChild(edit);
	}

	private static boolean covers(TextEdit thisEdit, TextEdit otherEdit) {
		if (thisEdit.getLength() == 0) {
			return false;
		}

		int thisOffset= thisEdit.getOffset();
		int thisEnd= thisEdit.getExclusiveEnd();
		if (otherEdit.getLength() == 0) {
			int otherOffset= otherEdit.getOffset();
			return thisOffset <= otherOffset && otherOffset < thisEnd;
		} else {
			int otherOffset= otherEdit.getOffset();
			int otherEnd= otherEdit.getExclusiveEnd();
			return thisOffset <= otherOffset && otherEnd <= thisEnd;
		}
	}
}
