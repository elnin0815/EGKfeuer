package de.gecko.egkfeuer.model.ekg;

import de.gecko.egkfeuer.model.EgkPatient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



public class DelegatingToPatientConverter implements ToPatientConverter
{
	private final List<ToPatientConverter> converters = new ArrayList<>();

	public DelegatingToPatientConverter(ToPatientConverter... converters)
	{
		this.converters.addAll(Arrays.asList(converters));
	}

	@Override
	public boolean isPdCompatible(String pdContent)
	{
		for (ToPatientConverter c : converters)
			if (c.isPdCompatible(pdContent))
				return true;

		return false;
	}

	@Override
	public boolean isVdCompatible(String vdContent)
	{
		for (ToPatientConverter c : converters)
			if (c.isVdCompatible(vdContent))
				return true;

		return false;
	}

	@Override
	public EgkPatient toPatient(String pdContent, String vdContent)
	{
		for (ToPatientConverter c : converters)
			if (c.isPdCompatible(pdContent) && c.isVdCompatible(vdContent))
				return c.toPatient(pdContent, vdContent);

		throw new IllegalArgumentException("pdContent or vdContent not compatible");
	}
}
