package de.gecko.egkfeuer.model.ekg.v51;

import de.gecko.egkfeuer.model.EgkPatient;
import de.gecko.egkfeuer.model.ekg.AbstractToPatientConverter;
import de.gecko.egkfeuer.model.ekg.v51.pd.UCPersoenlicheVersichertendatenXML;
import de.gecko.egkfeuer.model.ekg.v51.vd.UCAllgemeineVersicherungsdatenXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXB;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class ToPatientConverterV51 extends AbstractToPatientConverter
{
	private static final Logger logger = LoggerFactory.getLogger(ToPatientConverterV51.class);

	private static final String CDM_VERSION = "5.1.0";

	@Override
	public boolean isPdCompatible(String pdContent)
	{
		return isCompatible(pdContent, CDM_VERSION);
	}

	@Override
	public boolean isVdCompatible(String vdContent)
	{
		return isCompatible(vdContent, CDM_VERSION);
	}

	@Override
	public EgkPatient toPatient(String pdContent, String vdContent)
	{
		if (!isPdCompatible(pdContent))
			throw new IllegalArgumentException("pdContent not compatible");
		if (!isVdCompatible(vdContent))
			throw new IllegalArgumentException("vdContent not compatible");

		logger.debug("Unmarschaling PD content");
		UCPersoenlicheVersichertendatenXML pd = JAXB.unmarshal(new StringReader(pdContent),
				UCPersoenlicheVersichertendatenXML.class);

		logger.debug("Unmarschaling VD content");
		UCAllgemeineVersicherungsdatenXML vd = JAXB.unmarshal(new StringReader(vdContent),
				UCAllgemeineVersicherungsdatenXML.class);

		String titel = pd.getVersicherter().getPerson().getTitel();
		String vorname = pd.getVersicherter().getPerson().getVorname();
		String nachname = pd.getVersicherter().getPerson().getNachname();
		String vorsatzwort = pd.getVersicherter().getPerson().getVorsatzwort();
		String namenszusatz = pd.getVersicherter().getPerson().getNamenszusatz();

		LocalDate geburtsdatum = LocalDate.parse(pd.getVersicherter().getPerson().getGeburtsdatum(),
				DateTimeFormatter.ofPattern("yyyyMMdd"));
		String geschlecht = pd.getVersicherter().getPerson().getGeschlecht();

		String postleitzahl = pd.getVersicherter().getPerson().getStrassenAdresse().getPostleitzahl();
		String ort = pd.getVersicherter().getPerson().getStrassenAdresse().getOrt();
		String strasse = pd.getVersicherter().getPerson().getStrassenAdresse().getStrasse();
		String hausnummer = pd.getVersicherter().getPerson().getStrassenAdresse().getHausnummer();
		String versichertenID = pd.getVersicherter().getVersichertenID();

		String kostentraegerkennung = vd.getVersicherter().getVersicherungsschutz().getKostentraeger()
				.getKostentraegerkennung().toString();
		String kostentraegerName = vd.getVersicherter().getVersicherungsschutz().getKostentraeger().getName();

		return new EgkPatient(titel, vorname, nachname, vorsatzwort, namenszusatz, geburtsdatum, toSex(geschlecht), postleitzahl, ort, strasse,
				 hausnummer, kostentraegerkennung, kostentraegerName, versichertenID);
	}
}
