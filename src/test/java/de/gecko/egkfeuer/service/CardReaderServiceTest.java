package de.gecko.egkfeuer.service;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.smartcardio.CardTerminalSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import de.gecko.egkfeuer.exception.smartcard.CardAccessException;
import de.gecko.egkfeuer.exception.smartcard.CardDecisionException;
import de.gecko.egkfeuer.exception.smartcard.EgkNotFoundException;
import de.gecko.egkfeuer.exception.smartcard.UnsupportedEgkException;
import de.gecko.egkfeuer.model.EgkPatient;
import de.gecko.egkfeuer.model.Sex;
import de.gecko.egkfeuer.model.ekg.ToPatientConverter;
import de.gecko.egkfeuer.model.ekg.v51.pd.UCPersoenlicheVersichertendatenXML;
import de.gecko.egkfeuer.service.smartcard.EgkSimG0;
import de.gecko.egkfeuer.service.smartcard.EgkSimG1;
import de.gecko.egkfeuer.service.smartcard.EgkSimG1Plus;
import de.gecko.egkfeuer.service.smartcard.NonEgkSim;
import javacard.framework.AID;
import javacard.framework.Applet;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.CardTerminals.State;
import javax.smartcardio.CommandAPDU;
import javax.xml.bind.JAXB;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;


@SuppressWarnings("restriction")
public class CardReaderServiceTest
{

	private CardReaderService cardReaderService;

	private List<CardTerminal> terminallist;
	private List<CardTerminal> terminallistCardPresent;

	@Before
	public void setUp() throws Exception
	{
		terminallist = new ArrayList<CardTerminal>();
		terminallistCardPresent = new ArrayList<CardTerminal>();

		CardTerminalsService cardTerminalService = EasyMock.createMock(CardTerminalsService.class);
		CardTerminals cardTerminals = EasyMock.createMock(CardTerminals.class);
		EasyMock.expect(cardTerminalService.terminals()).andReturn(cardTerminals).anyTimes();
		EasyMock.expect(cardTerminals.list()).andReturn(terminallist).anyTimes();
		EasyMock.expect(cardTerminals.list(State.CARD_PRESENT)).andReturn(terminallistCardPresent)
				.anyTimes();
		EasyMock.replay(cardTerminals, cardTerminalService);

		cardReaderService = new CardReaderServiceImpl(new ToPatientConverter()
		{
			@Override
			public EgkPatient toPatient(String pdContent, String vdContent)
			{
				return new EgkPatient("title", "givenName", "surname", "vorsatzwort", "namenszusatz", LocalDate.now(), Sex.FEMALE, "zip", "city",
						"streetname", "housenumber", "healthInsuranceProviderNumber", "healthInsuranceNumber", "versichertenID");
			}

			@Override
			public boolean isVdCompatible(String vdContent)
			{
				return true;
			}

			@Override
			public boolean isPdCompatible(String pdContent)
			{
				return true;
			}
		}, cardTerminalService);
	}

	@Test
	public void testNoReaderPresent() throws Exception
	{
		assertFalse(cardReaderService.isCardReaderPresent());
	}

	@Test
	public void testReaderPresentempty() throws Exception
	{
		insertTerminal(null);
		assertTrue(cardReaderService.isCardReaderPresent());
	}

	@Test
	public void testReaderPresentEgk() throws Exception
	{
		insertTerminal(EgkSimG1Plus.class);
		assertTrue(cardReaderService.isCardReaderPresent());
	}

	@Test
	public void testReaderPresentNonEgk() throws Exception
	{
		insertTerminal(NonEgkSim.class);
		assertTrue(cardReaderService.isCardReaderPresent());
	}

	@Test
	public void testReaderPresentemptyEgknonEgk() throws Exception
	{
		insertTerminal(null);
		insertTerminal(EgkSimG1Plus.class);
		insertTerminal(NonEgkSim.class);
		assertTrue(cardReaderService.isCardReaderPresent());
	}

	@Test(expected = UnsupportedEgkException.class)
	public void testReadEgkG0() throws Exception
	{
		insertTerminal(EgkSimG0.class);
		EgkPatient read = cardReaderService.read();
		assertNotNull(read.getSurname());
	}

	@Test
	public void testReadEgkG1() throws Exception
	{
		insertTerminal(EgkSimG1.class);
		EgkPatient read = cardReaderService.read();
		assertNotNull(read.getSurname());
	}

	@Test
	public void testReadEgkG1Plus() throws Exception
	{
		insertTerminal(EgkSimG1Plus.class);
		EgkPatient read = cardReaderService.read();
		assertNotNull(read.getSurname());
	}

	@Test(expected = CardAccessException.class)
	public void testReadNonEgk() throws Exception
	{
		insertTerminal(NonEgkSim.class);
		cardReaderService.read();
	}

	@Test(expected = CardDecisionException.class)
	public void testRead2EgkG1() throws Exception
	{
		insertTerminal(EgkSimG1.class);
		insertTerminal(EgkSimG1.class);
		cardReaderService.read();
	}

	@Test(expected = CardAccessException.class)
	public void testReadEgkG1andNonEgk() throws Exception
	{
		insertTerminal(EgkSimG1.class);
		insertTerminal(NonEgkSim.class);
		cardReaderService.read();
	}

	@Test(expected = EgkNotFoundException.class)
	public void testReadEmpty() throws Exception
	{
		cardReaderService.read();
	}

	@Test
	public void unmarschallTest()
	{
		UCPersoenlicheVersichertendatenXML pd = JAXB
				.unmarshal(
						new StringReader(
								"<?xml version=\"1.0\" encoding=\"ISO-8859-15\" standalone=\"yes\"?><UC_PersoenlicheVersichertendatenXML CDM_VERSION=\"5.1.0\" xmlns=\"http://ws.gematik.de/fa/vsds/UC_PersoenlicheVersichertendatenXML/v5.1\"><Versicherter><Versicherten_ID>H719994900</Versicherten_ID><Person><Geburtsdatum>19901124</Geburtsdatum><Vorname>Niklas</Vorname><Nachname>Bunge</Nachname><Geschlecht>M</Geschlecht><StrassenAdresse><Postleitzahl>74072</Postleitzahl><Ort>Heilbronn</Ort><Land><Wohnsitzlaendercode>D</Wohnsitzlaendercode></Land><Strasse>Oststr.</Strasse><Hausnummer>100</Hausnummer></StrassenAdresse></Person></Versicherter></UC_PersoenlicheVersichertendatenXML>"),
						UCPersoenlicheVersichertendatenXML.class);

		UCPersoenlicheVersichertendatenXML pd2 = JAXB
				.unmarshal(
						new StringReader(
								"<?xml version=\"1.0\" encoding=\"ISO-8859-15\" standalone=\"yes\"?><UC_PersoenlicheVersichertendatenXML CDM_VERSION=\"5.1.0\" xmlns=\"http://ws.gematik.de/fa/vsds/UC_PersoenlicheVersichertendatenXML/v5.1\"><Versicherter><Versicherten_ID>D110104619</Versicherten_ID><Person><Geburtsdatum>19800112</Geburtsdatum><Vorname>Ltu</Vorname><Nachname>Musterkarte-0461</Nachname><Geschlecht>M</Geschlecht><StrassenAdresse><Postleitzahl>24937</Postleitzahl><Ort>Flensburg</Ort><Land><Wohnsitzlaendercode>D</Wohnsitzlaendercode></Land><Strasse>M�hlenstr.</Strasse><Hausnummer>46</Hausnummer></StrassenAdresse></Person></Versicherter></UC_PersoenlicheVersichertendatenXML>"),
						UCPersoenlicheVersichertendatenXML.class);

		UCPersoenlicheVersichertendatenXML pd3 = JAXB
				.unmarshal(
						new StringReader(
								"<?xml version=\"1.0\" encoding=\"ISO-8859-15\" standalone=\"yes\"?><UC_PersoenlicheVersichertendatenXML CDM_VERSION=\"5.1.0\" xmlns=\"http://ws.gematik.de/fa/vsds/UC_PersoenlicheVersichertendatenXML/v5.1\"><Versicherter><Versicherten_ID>D110104619</Versicherten_ID><Person><Geburtsdatum>19800112</Geburtsdatum><Vorname>Ltu</Vorname><Nachname>Musterkarte-0461</Nachname><Geschlecht>M</Geschlecht><StrassenAdresse><Postleitzahl>24937</Postleitzahl><Ort>Flensburg</Ort><Land><Wohnsitzlaendercode>D</Wohnsitzlaendercode></Land><Strasse>Mühlenstr.</Strasse><Hausnummer>46</Hausnummer></StrassenAdresse></Person></Versicherter></UC_PersoenlicheVersichertendatenXML>"),
						UCPersoenlicheVersichertendatenXML.class);

		assertNotNull(pd.getVersicherter());
		assertNotNull(pd2.getVersicherter());
		assertNotNull(pd3.getVersicherter());
		assertNotNull(pd.getVersicherter().getPerson());
	}

	// @Ignore
	// @Test
	// public void unmarschallTest2() throws JAXBException
	// {
	// UCPersoenlicheVersichertendatenXMLv053 unmarshal =
	// (UCPersoenlicheVersichertendatenXMLv053) JAXBUtil
	// .unmarshall(
	// "<?xml version=\"1.0\" encoding=\"ISO-8859-15\" standalone=\"yes\"?><UC_PersoenlicheVersichertendatenXML CDM_VERSION=\"5.1.0\" xmlns=\"http://ws.gematik.de/fa/vsds/UC_PersoenlicheVersichertendatenXML/v5.1\"><Versicherter><Versicherten_ID>D110104619</Versicherten_ID><Person><Geburtsdatum>19800112</Geburtsdatum><Vorname>Ltu</Vorname><Nachname>Musterkarte-0461</Nachname><Geschlecht>M</Geschlecht><StrassenAdresse><Postleitzahl>24937</Postleitzahl><Ort>Flensburg</Ort><Land><Wohnsitzlaendercode>D</Wohnsitzlaendercode></Land><Strasse>M�hlenstr.</Strasse><Hausnummer>46</Hausnummer></StrassenAdresse></Person></Versicherter></UC_PersoenlicheVersichertendatenXML>",
	// UCPersoenlicheVersichertendatenXMLv053.class);
	// assertNotNull(unmarshal.getVersicherter());
	// }

	private void insertTerminal(Class<? extends Applet> card) throws CardException
	{
		if (card != null)
		{
			CardSimulator simulator = new CardSimulator();
			AID appletAID = AIDUtil.create("D2760000015445535442");
			simulator.installApplet(appletAID, card);
			CardTerminal terminal = CardTerminalSimulator.terminal(simulator);
			CommandAPDU selectCommand = new CommandAPDU(AIDUtil.select(appletAID));
			simulator.transmitCommand(selectCommand);
			terminallist.add(terminal);
			terminallistCardPresent.add(terminal);
		}
		else
		{
			CardTerminal emptyTerminal = EasyMock.createNiceMock(CardTerminal.class);
			EasyMock.expect(emptyTerminal.connect(EasyMock.anyString())).andThrow(
					new javax.smartcardio.CardNotPresentException("no card found"));
			EasyMock.expect(emptyTerminal.isCardPresent()).andReturn(false);
			EasyMock.replay(emptyTerminal);
			terminallist.add(emptyTerminal);
		}
	}

}