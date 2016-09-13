package de.gecko.egkfeuer.service;

import de.gecko.egkfeuer.model.EgkPatient;

public interface CardReaderService
{
	EgkPatient read() throws de.gecko.egkfeuer.exception.smartcard.CardException;

	boolean isCardReaderPresent();
}
