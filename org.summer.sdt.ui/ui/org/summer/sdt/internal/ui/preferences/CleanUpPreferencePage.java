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
package org.summer.sdt.internal.ui.preferences;


import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.summer.sdt.internal.ui.IJavaHelpContextIds;
import org.summer.sdt.internal.ui.preferences.cleanup.CleanUpConfigurationBlock;
import org.summer.sdt.internal.ui.preferences.formatter.ProfileConfigurationBlock;

/*
 * The page to configure the clean up options.
 */
public class CleanUpPreferencePage extends ProfilePreferencePage {

	public static final String PREF_ID= "org.summer.sdt.ui.preferences.CleanUpPreferencePage"; //$NON-NLS-1$
	public static final String PROP_ID= "org.summer.sdt.ui.propertyPages.CleanUpPreferencePage"; //$NON-NLS-1$

	public CleanUpPreferencePage() {
		// only used when page is shown programmatically
		setTitle(PreferencesMessages.CleanUpPreferencePage_Title );
	}

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.preferences.ProfilePreferencePage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createControl(Composite parent) {
	    super.createControl(parent);
    	PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.CLEAN_UP_PREFERENCE_PAGE);
	}

	@Override
	protected ProfileConfigurationBlock createConfigurationBlock(PreferencesAccess access) {
	    return new CleanUpConfigurationBlock(getProject(), access);
    }

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.preferences.PropertyAndPreferencePage#getPreferencePageID()
	 */
	@Override
	protected String getPreferencePageID() {
		return PREF_ID;
	}

	/* (non-Javadoc)
	 * @see org.summer.sdt.internal.ui.preferences.PropertyAndPreferencePage#getPropertyPageID()
	 */
	@Override
	protected String getPropertyPageID() {
		return PROP_ID;
	}
}