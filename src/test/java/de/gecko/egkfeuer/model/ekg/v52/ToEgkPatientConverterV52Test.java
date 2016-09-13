package de.gecko.egkfeuer.model.ekg.v52;

import de.gecko.egkfeuer.model.EgkPatient;
import de.gecko.egkfeuer.model.ekg.ToPatientConverter;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;


public class ToEgkPatientConverterV52Test
{
	private ToPatientConverter converter;

	@Before
	public void before()
	{
		converter = new ToPatientConverterV52();
	}

	@Test
	public void testToPatient() throws Exception
	{
		String pdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v52.pd.xml")),
				"ISO-8859-15");
		String vdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v52.vd.xml")),
				"ISO-8859-15");

		assertTrue(converter.isPdCompatible(pdContent));
		assertTrue(converter.isVdCompatible(vdContent));

		EgkPatient patient = converter.toPatient(pdContent, vdContent);

		assertNotNull(patient);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testToPatientWrongVersionPd() throws Exception
	{
		String pdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v51.pd.xml")),
				"ISO-8859-15");
		String vdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v52.vd.xml")),
				"ISO-8859-15");

		assertFalse(converter.isPdCompatible(pdContent));

		converter.toPatient(pdContent, vdContent);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testToPatientWrongVersionVd() throws Exception
	{
		String pdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v52.pd.xml")),
				"ISO-8859-15");
		String vdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v51.vd.xml")),
				"ISO-8859-15");

		assertFalse(converter.isVdCompatible(vdContent));

		converter.toPatient(pdContent, vdContent);
	}
}
