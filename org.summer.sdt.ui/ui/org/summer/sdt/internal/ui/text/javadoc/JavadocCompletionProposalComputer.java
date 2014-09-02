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
package org.summer.sdt.internal.ui.text.javadoc;

import org.summer.sdt.core.CompletionProposal;
import org.summer.sdt.internal.ui.text.java.JavaCompletionProposalComputer;
import org.summer.sdt.ui.text.java.CompletionProposalCollector;
import org.summer.sdt.ui.text.java.JavaContentAssistInvocationContext;

/**
 *
 * @since 3.2
 */
public class JavadocCompletionProposalComputer extends JavaCompletionProposalComputer {
	/*
	 * @see org.summer.sdt.internal.ui.text.java.JavaCompletionProposalComputer#createCollector(org.summer.sdt.ui.text.java.JavaContentAssistInvocationContext)
	 */
	@Override
	protected CompletionProposalCollector createCollector(JavaContentAssistInvocationContext context) {
		CompletionProposalCollector collector= super.createCollector(context);
		collector.setIgnored(CompletionProposal.JAVADOC_TYPE_REF, false);
		collector.setIgnored(CompletionProposal.JAVADOC_FIELD_REF, false);
		collector.setIgnored(CompletionProposal.JAVADOC_METHOD_REF, false);
		collector.setIgnored(CompletionProposal.JAVADOC_PARAM_REF, false);
		collector.setIgnored(CompletionProposal.JAVADOC_VALUE_REF, false);
		collector.setIgnored(CompletionProposal.JAVADOC_BLOCK_TAG, false);
		collector.setIgnored(CompletionProposal.JAVADOC_INLINE_TAG, false);
		collector.setIgnored(CompletionProposal.TYPE_REF, false);
		collector.setIgnored(CompletionProposal.FIELD_REF, false);
		collector.setIgnored(CompletionProposal.METHOD_REF, false);
		return collector;
	}
}
