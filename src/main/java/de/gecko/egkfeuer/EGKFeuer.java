package de.gecko.egkfeuer;

import de.gecko.egkfeuer.model.EgkPatient;
import de.gecko.egkfeuer.model.ekg.DelegatingToPatientConverter;
import de.gecko.egkfeuer.model.ekg.v51.ToPatientConverterV51;
import de.gecko.egkfeuer.model.ekg.v52.ToPatientConverterV52;
import de.gecko.egkfeuer.service.CardReaderService;
import de.gecko.egkfeuer.service.CardReaderServiceImpl;
import de.gecko.egkfeuer.service.CardTerminalsServiceImpl;
import de.gecko.egkfeuer.service.PatientToFhirServiceDSTU2;

public class EGKFeuer {
    public static void main(String[] args) {
        String fHIRServerBase;
        String timeout;

        if (args.length > 0)
            fHIRServerBase = args[0];
        else
            fHIRServerBase = "http://fhirtest.uhn.ca/baseDstu2";

        //TODO: use timeout
        if (args.length > 1)
            timeout = args[1];
        else
            timeout = "10";

        CardReaderService cardReaderService = new CardReaderServiceImpl(new DelegatingToPatientConverter(new ToPatientConverterV51(), new ToPatientConverterV52()), new CardTerminalsServiceImpl());
        EgkPatient patient = cardReaderService.read();
        PatientToFhirServiceDSTU2 patientToFhirServiceDSTU2 = new PatientToFhirServiceDSTU2(fHIRServerBase);
        patientToFhirServiceDSTU2.sendPatientToFhirServer(patient);
    }
}
