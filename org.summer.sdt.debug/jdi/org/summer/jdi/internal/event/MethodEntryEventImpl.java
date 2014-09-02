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
package org.summer.jdi.internal.event;

import java.io.DataInputStream;
import java.io.IOException;

import org.summer.jdi.internal.MirrorImpl;
import org.summer.jdi.internal.VirtualMachineImpl;
import org.summer.jdi.internal.request.RequestID;

import com.sun.jdi.Method;
import com.sun.jdi.event.MethodEntryEvent;

/**
 * this class implements the corresponding interfaces declared by the JDI
 * specification. See the com.sun.jdi package for more information.
 * 
 */
public class MethodEntryEventImpl extends LocatableEventImpl implements
		MethodEntryEvent {
	/** Jdwp Event Kind. */
	public static final byte EVENT_KIND = EVENT_METHOD_ENTRY;

	/**
	 * Creates new MethodEntryEventImpl.
	 */
	private MethodEntryEventImpl(VirtualMachineImpl vmImpl, RequestID requestID) {
		super("MethodEntryEvent", vmImpl, requestID); //$NON-NLS-1$
	}

	/**
	 * @return Creates, reads and returns new EventImpl, of which requestID has
	 *         already been read.
	 */
	public static MethodEntryEventImpl read(MirrorImpl target,
			RequestID requestID, DataInputStream dataInStream)
			throws IOException {
		VirtualMachineImpl vmImpl = target.virtualMachineImpl();
		MethodEntryEventImpl event = new MethodEntryEventImpl(vmImpl, requestID);
		event.readThreadAndLocation(target, dataInStream);
		return event;
	}

	/**
	 * @return Returns the method that was entered.
	 */
	public Method method() {
		return fLocation.method();
	}
}
