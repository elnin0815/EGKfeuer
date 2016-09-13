package de.gecko.egkfeuer.model.ekg.v51;

import de.gecko.egkfeuer.model.EgkPatient;
import de.gecko.egkfeuer.model.ekg.ToPatientConverter;
import de.gecko.egkfeuer.service.PatientToFhirServiceDSTU2;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;


public class ToEgkPatientConverterV51Test
{
	private ToPatientConverter converter;

	@Before
	public void before()
	{
		converter = new ToPatientConverterV51();
	}

	@Test
	public void testToPatient() throws Exception
	{
		String pdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v51.pd.xml")),
				"ISO-8859-15");
		String vdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v51.vd.xml")),
				"ISO-8859-15");

		assertTrue(converter.isPdCompatible(pdContent));
		assertTrue(converter.isVdCompatible(vdContent));

		EgkPatient patient = converter.toPatient(pdContent, vdContent);

		assertNotNull(patient);
	}

	@Test
	public void testToFhirPatient() throws Exception {
		PatientToFhirServiceDSTU2 fhirConverter = new PatientToFhirServiceDSTU2("http://fhirtest.uhn.ca/baseDstu2");

		String pdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v51.pd.xml")),
				"ISO-8859-15");
		String vdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v51.vd.xml")),
				"ISO-8859-15");

		assertTrue(converter.isPdCompatible(pdContent));
		assertTrue(converter.isVdCompatible(vdContent));

		EgkPatient patient = converter.toPatient(pdContent, vdContent);
		assertNotNull(fhirConverter.sendPatientToFhirServer(patient));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testToPatientWrongVersionPd() throws Exception
	{
		String pdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v52.pd.xml")),
				"ISO-8859-15");
		String vdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v51.vd.xml")),
				"ISO-8859-15");

		assertFalse(converter.isPdCompatible(pdContent));

		converter.toPatient(pdContent, vdContent);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testToPatientWrongVersionVd() throws Exception
	{
		String pdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v51.pd.xml")),
				"ISO-8859-15");
		String vdContent = IOUtils.toString(Files.newInputStream(Paths.get("src/test/resources/egk/v52.vd.xml")),
				"ISO-8859-15");

		assertFalse(converter.isVdCompatible(vdContent));

		converter.toPatient(pdContent, vdContent);
	}
}
