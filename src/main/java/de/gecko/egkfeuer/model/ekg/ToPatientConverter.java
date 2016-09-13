package de.gecko.egkfeuer.model.ekg;

import de.gecko.egkfeuer.model.EgkPatient;

public interface ToPatientConverter
{
	boolean isPdCompatible(String pdContent);

	boolean isVdCompatible(String vdContent);

	/**
	 * @param pdContent
	 * @param vdContent
	 * @return
	 * @throws IllegalArgumentException
	 *             if the given <b>pdContent</b> or <b>vdContent</b> is not
	 *             compatible
	 * @see #isPdCompatible(String)
	 * @see #isVdCompatible(String)
	 */
	EgkPatient toPatient(String pdContent, String vdContent);
}
