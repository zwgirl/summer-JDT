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
package org.summer.sdt.debug.core;

import org.eclipse.debug.core.DebugException;

/**
 * Notification of hot code replace failure and success. As resources are
 * modified in the workspace, targets that support hot code replace are updated
 * with new class files.
 * <p>
 * Clients may implement this interface
 * </p>
 * 
 * @since 2.0
 */
public interface IJavaHotCodeReplaceListener {

	/**
	 * Notification that a hot code replace attempt failed in the given target.
	 * 
	 * @param target
	 *            the target in which the hot code replace failed
	 * @param exception
	 *            the exception generated by the hot code replace failure, or
	 *            <code>null</code> if the hot code replace failed because the
	 *            target VM does not support hot code replace
	 */
	public void hotCodeReplaceFailed(IJavaDebugTarget target,
			DebugException exception);

	/**
	 * Notification that a hot code replace attempt succeeded in the given
	 * target.
	 * 
	 * @param target
	 *            the target in which the hot code replace succeeded
	 */
	public void hotCodeReplaceSucceeded(IJavaDebugTarget target);

	/**
	 * Notification that obsolete methods remain on the stack in one or more
	 * threads in the given target after a hot code replace.
	 * 
	 * @param target
	 *            the target in which obsolete methods remain after a hot code
	 *            replace
	 */
	public void obsoleteMethods(IJavaDebugTarget target);

}
