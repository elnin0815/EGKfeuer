package de.gecko.egkfeuer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.dstu2.composite.AddressDt;
import ca.uhn.fhir.model.dstu2.composite.HumanNameDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Coverage;
import ca.uhn.fhir.model.dstu2.resource.Organization;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.valueset.AddressTypeEnum;
import ca.uhn.fhir.model.dstu2.valueset.AdministrativeGenderEnum;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.client.IGenericClient;
import de.gecko.egkfeuer.model.EgkPatient;
import de.gecko.egkfeuer.model.Sex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PatientToFhirServiceDSTU2 {

    private static final Logger logger = LoggerFactory.getLogger(CardReaderServiceImpl.class);
    private final String serverBase;

    public PatientToFhirServiceDSTU2(String serverBase) {
        this.serverBase = serverBase;
    }

    public String sendPatientToFhirServer(EgkPatient patient) {
        FhirContext ctx = FhirContext.forDstu2();
        IGenericClient client = ctx.newRestfulGenericClient(serverBase);

        //set Identifier
        Patient fhirPatient = new Patient();
        fhirPatient.addIdentifier()
                .setSystem("http://fhir.de/NamingSystem/gkv/kvnr")
                .setValue(patient.getHealthInsuranceNumber());
        //setName
        //TODO: HumanName.text adden
        HumanNameDt name = new HumanNameDt();
        name.addFamily(patient.getSurname())
                .addGiven(patient.getGivenName())
                .addPrefix(patient.getTitle())
                .addSuffix(patient.getNamenszusatz());
        ExtensionDt vorsatzwort = new ExtensionDt(false, "http://fhir.de/StructureDefinition/kbv/egk/vorsatzwort", new StringDt(patient.getVorsatzwort()));
        name.addUndeclaredExtension(vorsatzwort);
        fhirPatient.addName(name);

        //setSex
        if (patient.getSex() == Sex.FEMALE)
            fhirPatient.setGender(AdministrativeGenderEnum.FEMALE);
        else if (patient.getSex() == Sex.MALE)
            fhirPatient.setGender(AdministrativeGenderEnum.MALE);
        else
            throw new RuntimeException("Gender of patient was not set");

        //setBirthday
        fhirPatient.setBirthDateWithDayPrecision(Date.from(patient.getBirthday().atStartOfDay(ZoneId.systemDefault()).toInstant()));

        //setAdress
        //TODO: AdressType postal, other Countries than Germany
        List<AddressDt> adresses = new ArrayList<AddressDt>();
        AddressDt adress = new AddressDt();
        adress.addLine(patient.getStreetname() + " " + patient.getHousenumber()).setCity(patient.getCity()).setType(AddressTypeEnum.PHYSICAL).setPostalCode(patient.getZip()).setCountry("Germany");


        ExtensionDt streetname = new ExtensionDt(false, "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName", new StringDt(patient.getStreetname()));
        ExtensionDt housenumber = new ExtensionDt(false, "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-houseNumber", new StringDt(patient.getHousenumber()));
        adress.addUndeclaredExtension(streetname);
        adress.addUndeclaredExtension(housenumber);
        adresses.add(adress);
        fhirPatient.setAddress(adresses);

        //setProfile
        fhirPatient.getResourceMetadata().put(ResourceMetadataKeyEnum.PROFILES,
                "http://fhir.de/StructureDefinition/kbv/persoenlicheVersicherungsdaten");

        //submitToServer
        IdDt idPatient = (IdDt) client.update().resource(fhirPatient).conditional()
                .where(Patient.IDENTIFIER.exactly().systemAndIdentifier("http://fhir.de/NamingSystem/gkv/kvnr", patient.getHealthInsuranceNumber()))
                .execute().getId();
        logger.info("EgkPatient with ID: " + idPatient + " generated");


        Organization healthInsurance = new Organization();
        healthInsurance.addIdentifier()
                .setSystem("http://fhir.de/NamingSystem/arge-ik/iknr")
                .setValue(patient.getHealthInsuranceProviderNumber());
        healthInsurance.setName(patient.getHealthInsuranceProviderName());
        IdDt idInsurance = (IdDt) client.update().resource(healthInsurance).conditional()
                .where(Organization.IDENTIFIER.exactly().systemAndIdentifier("http://fhir.de/NamingSystem/arge-ik/iknr",patient.getHealthInsuranceProviderNumber()))
                .execute().getId();
        logger.info("Organization with ID: " + idInsurance + " generated");

        Coverage coverage = new Coverage();
        coverage.addIdentifier()
                .setSystem("http://fhir.de/NamingSystem/gkv/kvnr").setValue(patient.getHealthInsuranceNumber());
        coverage.setIssuer(new ResourceReferenceDt(idInsurance))
                .setSubscriber(new ResourceReferenceDt(idPatient));
        IdDt idCoverage = (IdDt) client.update().resource(coverage).conditional()
                .where(Coverage.IDENTIFIER.exactly().systemAndIdentifier("http://fhir.de/NamingSystem/gkv/kvnr", patient.getHealthInsuranceNumber()))
                .execute().getId();
        logger.info("Coverage with ID: " + idCoverage + " generated");

        return idPatient.toString();
    }
}
