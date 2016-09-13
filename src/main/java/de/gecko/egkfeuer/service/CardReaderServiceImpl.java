package de.gecko.egkfeuer.service;

import de.gecko.egkfeuer.exception.smartcard.*;
import de.gecko.egkfeuer.exception.smartcard.CardException;
import de.gecko.egkfeuer.model.EgkPatient;
import de.gecko.egkfeuer.model.ekg.ToPatientConverter;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import javax.smartcardio.CardTerminals.State;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;


@SuppressWarnings("restriction")
public class CardReaderServiceImpl implements CardReaderService
{
	private static final Logger logger = LoggerFactory.getLogger(CardReaderServiceImpl.class);

	private static final int PD_MAX_LENGTH = 848;

	private interface SupplierWithCardException<T>
	{
		public T get() throws javax.smartcardio.CardException;
	}

	private final ToPatientConverter converter;
	private final CardTerminalsService cardTerminals;

	/**
	 * @param converter
	 *            not <code>null</code>
	 */
	public CardReaderServiceImpl(ToPatientConverter converter, CardTerminalsService cardTerminals)
	{
		this.converter = Objects.requireNonNull(converter, "converter");
		this.cardTerminals = Objects.requireNonNull(cardTerminals, "cardTerminals");
	}

	@Override
	public EgkPatient read() throws CardException
	{


		if (!isCardPresent())
			throw new EgkNotFoundException();

		Card card = null;
		try
		{
			EgkPatient patient = null;
			int egkcounter = 0;

			List<CardTerminal> terminals = cardTerminals.terminals().list(State.CARD_PRESENT);
			for (Iterator<CardTerminal> iterator = terminals.iterator(); iterator.hasNext();)
			{
				CardTerminal terminal = iterator.next();
				logger.debug("Reading card at terminal {}", terminal.getName());
				logger.debug("Card present at terminal? {}", terminal.isCardPresent());

				card = terminal.connect("T=1");
				card.beginExclusive();

				CardChannel channel = card.getBasicChannel();
				patient = readFromCardChannel(channel);
				egkcounter++;
			}

			if (patient == null)
			{
				throw new EgkNotFoundException();
			}
			if (egkcounter > 1)
			{
				throw new CardDecisionException();
			}
			return patient;
		}
		catch (CardException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			logger.error("Error while accessing card", e);
			throw new CardException(e);
		}
		finally
		{
			try
			{
				if (card != null)
				{
					card.endExclusive();
					card.disconnect(true);
				}
			}
			catch (Exception e)
			{
				logger.error("Error while disconnecting card", e);
				throw new CardException(e);
			}
		}
	}

	private EgkPatient readFromCardChannel(CardChannel channel) throws CardException
	{
		logger.debug("Selecting root");

		byte[] SELECT_ROOT = new byte[] { 0x00, (byte) 0xa4, 0x04, 0x0c, 0x07, (byte) 0xd2, 0x76, 0x00, 0x01, 0x44,
				(byte) 0x80, 0x00 };
		ResponseAPDU selectRootResponse = translateException("Select root",
				() -> channel.transmit(new CommandAPDU(SELECT_ROOT)));

		logger.debug("Select root response {}", Hex.encodeHexString(selectRootResponse.getBytes()));

		if (selectRootResponse.getSW() != 0x9000)
		{
			throw new CardAccessException("Select root failed, status: "
					+ Hex.encodeHexString(selectRootResponse.getBytes()));
		}

		System.out.println("systemprint version:" + readVersion(channel)); // TODO

		byte[] maxResponseLength = readMaxResponseLengthFromAtr(channel);
		System.out.println("systemprint maxResponseLength: " + Hex.encodeHexString(maxResponseLength)); // TODO

		logger.debug("Selecting HCA");
		byte[] SELECT_HCA = new byte[] { 0x00, (byte) 0xa4, 0x04, 0x0c, 0x06, (byte) 0xd2, 0x76, 0x00, 0x00, 0x01, 0x02 };
		ResponseAPDU select_DF_HCA_Response = translateException("Select HCA",
				() -> channel.transmit(new CommandAPDU(SELECT_HCA)));

		logger.debug("Select HCA response {}", Hex.encodeHexString(select_DF_HCA_Response.getBytes()));
		if (select_DF_HCA_Response.getSW() != 0x9000)
			throw new CardAccessException("Select HCA failed, status: "
					+ Hex.encodeHexString(select_DF_HCA_Response.getBytes()));

		System.out.println("systemprint status:" + readStatus(channel)); // TODO

		String pdContent = readPd(channel);
		String vdContent = readVd(channel);

		if (!converter.isPdCompatible(pdContent))
			throw new CardException("PD content not compatible");
		if (!converter.isVdCompatible(pdContent))
			throw new CardException("CD content not compatible");

		return converter.toPatient(pdContent, vdContent);
	}

	private byte[] readMaxResponseLengthFromAtr(CardChannel channel)
	{
		logger.debug("Reading ATR");
		byte[] READ_ATR = new byte[] { 0x00, (byte) 0xb0, (byte) (0x80 + 0x1d), 0x00, 0x00 };
		ResponseAPDU readAtrResponse = translateException("Read ATR", () -> channel.transmit(new CommandAPDU(READ_ATR)));

		logger.debug("Read ATR response status {}", Integer.toHexString(readAtrResponse.getSW()));

		if (readAtrResponse.getSW() != 0x9000)
		{
			throw new CardAccessException("Reading ATR failed, status: " + Integer.toHexString(readAtrResponse.getSW()));
		}

		byte[] tlv = readAtrResponse.getData();
		int index = 3 + tlv[3] + 2;
		byte[] buffersize = Arrays.copyOfRange(tlv, index + 1, index + 1 + tlv[index]);
		return buffersize;
	}

	private String readVersion(CardChannel channel)
	{
		logger.debug("Reading Version");
		byte[] READ_VERSION_RECORD1 = new byte[] { 0x00, (byte) 0xb2, 0x01, (byte) (0x10 * 8 + 0x04), 0x00 };
		byte[] READ_VERSION_RECORD2 = new byte[] { 0x00, (byte) 0xb2, 0x02, (byte) (0x10 * 8 + 0x04), 0x00 };
		byte[] READ_VERSION_RECORD3 = new byte[] { 0x00, (byte) 0xb2, 0x03, (byte) (0x10 * 8 + 0x04), 0x00 };

		ResponseAPDU readAtrResponse1 = translateException("Read Version Record 1",
				() -> channel.transmit(new CommandAPDU(READ_VERSION_RECORD1)));
		ResponseAPDU readAtrResponse2 = translateException("Read Version Record 2",
				() -> channel.transmit(new CommandAPDU(READ_VERSION_RECORD2)));
		ResponseAPDU readAtrResponse3 = translateException("Read Version Record 3",
				() -> channel.transmit(new CommandAPDU(READ_VERSION_RECORD3)));

		if (readAtrResponse1.getSW() != 0x9000)
			throw new UnsupportedEgkException("Reading version Record1 failed, status: "
					+ Integer.toHexString(readAtrResponse1.getSW()));
		if (readAtrResponse2.getSW() != 0x9000)
			throw new UnsupportedEgkException("Reading version Record2 failed, status: "
					+ Integer.toHexString(readAtrResponse2.getSW()));
		if (readAtrResponse3.getSW() != 0x9000)
			throw new UnsupportedEgkException("Reading version Record3 failed, status: "
					+ Integer.toHexString(readAtrResponse3.getSW()));

		if (readAtrResponse1.getData().length != 5)
			throw new CardAccessException("Reading version Record1 failed, status: "
					+ Hex.encodeHexString(readAtrResponse1.getData()));
		if (readAtrResponse2.getData().length != 5)
			throw new CardAccessException("Reading version Record2 failed, status: "
					+ Hex.encodeHexString(readAtrResponse2.getData()));
		if (readAtrResponse3.getData().length != 5)
			throw new CardAccessException("Reading version Record3 failed, status: "
					+ Hex.encodeHexString(readAtrResponse3.getData()));

		String r1 = BCDtoString(readAtrResponse1.getData());
		String r2 = BCDtoString(readAtrResponse2.getData());
		String r3 = BCDtoString(readAtrResponse3.getData());

		System.out.println("systemprint version record 1;2;3 :" + r1 + ";" + r2 + ";" + r3);

		if (Integer.valueOf(r1) <= 30000000 && Integer.valueOf(r2) <= 30000000 && Integer.valueOf(r3) <= 30000002)
		{
			return "5.1.0";
		}
		else if (Integer.valueOf(r1) == 30000000 && Integer.valueOf(r2) == 30000001 && Integer.valueOf(r3) == 30000003)
		{
			return ">=5.1.0";
		}

		throw new UnsupportedEgkException(r1 + ";" + r2 + ";" + r3);
	}

	private String BCDtoString(byte bcd)
	{
		StringBuffer sb = new StringBuffer();

		byte high = (byte) (bcd & 0xf0);
		high >>>= (byte) 4;
		high = (byte) (high & 0x0f);
		byte low = (byte) (bcd & 0x0f);

		sb.append(high);
		sb.append(low);

		return sb.toString();
	}

	private String BCDtoString(byte[] bcd)
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < bcd.length; i++)
		{
			sb.append(BCDtoString(bcd[i]));
		}
		return sb.toString();
	}

	private String readStatus(CardChannel channel)
	{
		// 0x19 ist die dateigröße aus der doku
		// byte[] READ_StatusVD = new byte[] { 0x00, (byte) 0xb0, (byte) (0x80 +
		// 0x0c), 0x00 ,0x00,0x00, 0x00 };
		byte[] READ_StatusVD = new byte[] { 0x00, (byte) 0xb0, (byte) (0x80 + 0x0c), 0x00, 0x01 };

		ResponseAPDU readStatusVDResponse = translateException("Read Status VD",
				() -> channel.transmit(new CommandAPDU(READ_StatusVD)));
		// logger.debug("Read Status VD {}",
		// Hex.encodeHexString(readStatusVDResponse.getBytes()));

		// if (readStatusVDResponse.getData().length != 0x0019)
		// {
		// throw new
		// CardAccessException("StatusVD file lenth not as long as it should: "
		// + readStatusVDResponse.getData().length);
		// }

		byte[] statusData = new byte[1];
		System.arraycopy(readStatusVDResponse.getData(), 0, statusData, 0, statusData.length);

		return new String(statusData);
	}

	private String readPd(CardChannel channel) throws CardException
	{
		byte[] READ_PD = new byte[] { 0x00, (byte) 0xb0, (byte) (0x80 + 0x01), 0x00, 0x00, 0x00, 0x00 };

		logger.debug("Reading PD");
		ResponseAPDU readPdResponse = translateException("Read PD", () -> channel.transmit(new CommandAPDU(READ_PD)));

		logger.debug("Read PD response status {}", Integer.toHexString(readPdResponse.getSW()));
		if (readPdResponse.getSW() != 0x9000)
			throw new CardAccessException("Reading PD failed, status: " + Integer.toHexString(readPdResponse.getSW()));

		if (readPdResponse.getData().length != 0x0352)
		{
			logger.warn("DP does not have length of 0x0352:" + readPdResponse.getData().length);
			// throw new
			// CardAccessException("PD file lenth not as long as it should be: "
			// + readPdResponse.getData().length);
		}

		int pdLength = ((readPdResponse.getData()[0] & 0xff) << 8) | (readPdResponse.getData()[1] & 0xff);

		try
		{
			byte[] pdDataCompressed = new byte[pdLength];
			System.arraycopy(readPdResponse.getData(), 2, pdDataCompressed, 0, pdLength);

			return new String(unzip(pdDataCompressed), Charset.forName("ISO-8859-15"));
		}
		catch (CardException e)
		{
			if (e.getCause() instanceof ZipException)
			{
				logger.info("Caught ZipException while using PD Length {}, trying again with max length {}", pdLength,
						PD_MAX_LENGTH);

				byte[] pdDataCompressed = new byte[PD_MAX_LENGTH];
				System.arraycopy(readPdResponse.getData(), 2, pdDataCompressed, 0, PD_MAX_LENGTH);

				return new String(unzip(pdDataCompressed), Charset.forName("ISO-8859-15"));
			}
			else
				throw e;
		}
	}

	private String readVd(CardChannel channel) throws CardException
	{
		logger.debug("Reading VD/GVD");
		byte[] READ_VD = new byte[] { 0x00, (byte) 0xb0, (byte) (0x80 + 0x02), 0x00, 0x00, 0x00, 0x00 };
		ResponseAPDU readVdResponse = translateException("Read VD/GVD",
				() -> channel.transmit(new CommandAPDU(READ_VD)));

		logger.debug("Read VD/GVD response status {}", Integer.toHexString(readVdResponse.getSW()));
		if (readVdResponse.getSW() != 0x9000)
			throw new CardAccessException("READ_VD failed, status: " + Integer.toHexString(readVdResponse.getSW()));

		if (readVdResponse.getData().length != 0x04e2)
		{
			logger.warn("VD does not have length of 0x04e2: " + readVdResponse.getData().length);
			// throw new
			// CardAccessException("VD file lenth not as long as it should be: "
			// + readVdResponse.getData().length);
		}

		int offsetStartVD = ((readVdResponse.getData()[0] & 0xff) << 8) | (readVdResponse.getData()[1] & 0xff);
		int offsetEndVD = ((readVdResponse.getData()[2] & 0xff) << 8) | (readVdResponse.getData()[3] & 0xff);
		// int offsetStartGVD = ((readVdResponseData[4] & 0xff) << 8) |
		// (readVdResponseData[5] & 0xff);
		// int offsetEndGVD = ((readVdResponseData[6] & 0xff) << 8) |
		// (readVdResponseData[7] & 0xff);

		byte[] vdDataCompressed = new byte[(offsetEndVD - offsetStartVD) + 1];
		System.arraycopy(readVdResponse.getData(), offsetStartVD, vdDataCompressed, 0, vdDataCompressed.length);

		return new String(unzip(vdDataCompressed), Charset.forName("ISO-8859-15"));
	}

	private <T> T translateException(String commandDescription, SupplierWithCardException<T> s) throws CardException
	{
		try
		{
			return s.get();
		}
		catch (javax.smartcardio.CardException e)
		{
			logger.error("Error while executing " + commandDescription, e);
			throw new CardException("Error while executing " + commandDescription, e);
		}
	}

	private static byte[] unzip(byte[] zip) throws CardException
	{
		try
		{
			return IOUtils.toByteArray(new GZIPInputStream(new ByteArrayInputStream(zip)));
		}
		catch (IOException e)
		{
			throw new CardException("Error while unziping EGK content", e);
		}
	}

	@Override
	public boolean isCardReaderPresent()
	{
		try
		{
			return !cardTerminals.terminals().list().isEmpty();
		}
		catch (javax.smartcardio.CardException e)
		{
			logger.warn("Error while accessing card terminals", e);
			return false;
		}
	}

	private boolean isCardPresent()
	{
		try
		{
			return !cardTerminals.terminals().list(State.CARD_PRESENT).isEmpty();
		}
		catch (javax.smartcardio.CardException e)
		{
			logger.warn("Error while accessing card terminals", e);
			return false;
		}
	}

	public static void main(String[] args) throws Exception
	{
		String card1Hex = "01821f8b080066bdae5400ff8d52db4ec24010fd9566dfe916626d21b4c48051122e0a118d2f646947dab89d92dd052f5fef942e0891075ff632e79c393399e9f63e0be9ec40e9bcc488355d8f39804999e6b88ed8703e6d84a1df6e347de668233015b24488d81768d68bbb3b9d6e3a4ffde503e94b409927192caa5c742b0344a7e3653c72fa83f172713b9b0fa79388f96eabb22163d49d2a45c43263361dce3fb4bb864298fcdd4d81bf094e68511d7c4722660d4f1cd4df102e8783f83108032f0c03dfebf28b845ab6af1bede70e565b6534d5bc2de2663b0cbd967765e56798b52c158a02e281c01ce4c1c6066bca4424d9fefbaa6105696eb4a51d8183b34e32094966e2f1d1f018aa3973a384d68037a902ba0f0d94da48c8cdb7c8647c1d36db6dab3f036aee54517a8198415e585615aac111cdd63e9fcb0c3529a5a00182a25da026ade01266a1930cb6d6784656b82e65aa8d722ded80d5cc7bb1d5b82d0a1aa41f58c649ec5cf3db3b3f9b1ebfb015fcbfab19ff00d333237202030000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
		String card2Hex = "016a1f8b08000000000000008d51c94ec33010fd95c8f7c63128d0a0892bd4225aa90ba2a270abdc6468a2260eb29db27c3d936e10c4a1178fdebcf19be541efa32cbc2d1a9b573a66c20f98873aa9d25caf63369acf3add6e187544c83ceb944e5551698cd9275ad693f0d45f3ed0d70a759127192e1a198ac62155d2f332197bfdc164b9b87b9c8f66d39885feae03f5d4366699736f379cbf5b7f8da572f9c64f91bf2abeb5a9e56768f32de93109bf38d3427a391ac859781d4442442204fe97835d0b2de11e57b5719694eb528aa81b06e252006fa5615119ad4a9443556f90c40e10a62ac9f644ad53e02748aa36c90a4c3227278dd809c1dc19652deadbd420451aa4b2aec0dc7da9ac905791b80880b77230334e0e314fb158a159036f308cc91309cf55a62dd5158a8e8386ec433900fe5f1af8fecb610039c51a75867989c66b18eb8c0ffcc802ad6a755d122be97cbfd0a9e667057e3c266f1b728e95f21bc7e380de89020000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";

		byte[] card1 = Hex.decodeHex(card1Hex.toCharArray());
		byte[] card2 = Hex.decodeHex(card2Hex.toCharArray());

		System.out.println(test(card1));
		System.out.println(test(card2));
	}

	private static String test(byte[] data)
	{
		int pdLength = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);

		try
		{
			System.out.println("pdLength: " + pdLength);
			byte[] pdDataCompressed = new byte[pdLength];
			System.arraycopy(data, 2, pdDataCompressed, 0, pdLength);

			return new String(unzip(pdDataCompressed), Charset.forName("ISO-8859-15"));
		}
		catch (CardException e)
		{
			if (e.getCause() instanceof ZipException)
			{
				logger.info("Caught ZipException while using PD Length {}, trying again with max length {}", pdLength,
						PD_MAX_LENGTH);

				System.out.println("pdLength: " + pdLength);
				byte[] pdDataCompressed = new byte[PD_MAX_LENGTH];
				System.arraycopy(data, 2, pdDataCompressed, 0, PD_MAX_LENGTH);

				return new String(unzip(pdDataCompressed), Charset.forName("ISO-8859-15"));
			}
			else
				throw e;
		}
	}
}
