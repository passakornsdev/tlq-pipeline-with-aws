package com.passakornsdev.tlq.csv.transfer;

import java.util.List;

public class PaymentRecord {
    private static final List<String> HEADERS = List.of(
        "Change_Type",
        "Covered_Recipient_Type",
        "Teaching_Hospital_CCN",
        "Teaching_Hospital_ID",
        "Teaching_Hospital_Name",
        "Physician_Profile_ID",
        "Physician_First_Name",
        "Physician_Middle_Name",
        "Physician_Last_Name",
        "Physician_Name_Suffix",
        "Recipient_Primary_Business_Street_Address_Line1",
        "Recipient_Primary_Business_Street_Address_Line2",
        "Recipient_City",
        "Recipient_State",
        "Recipient_Zip_Code",
        "Recipient_Country",
        "Recipient_Province",
        "Recipient_Postal_Code",
        "Physician_Primary_Type",
        "Physician_Specialty",
        "Physician_License_State_code1",
        "Physician_License_State_code2",
        "Physician_License_State_code3",
        "Physician_License_State_code4",
        "Physician_License_State_code5",
        "Submitting_Applicable_Manufacturer_or_Applicable_GPO_Name",
        "Applicable_Manufacturer_or_Applicable_GPO_Making_Payment_ID",
        "Applicable_Manufacturer_or_Applicable_GPO_Making_Payment_Name",
        "Applicable_Manufacturer_or_Applicable_GPO_Making_Payment_State",
        "Applicable_Manufacturer_or_Applicable_GPO_Making_Payment_Country",
        "Total_Amount_of_Payment_USDollars",
        "Date_of_Payment",
        "Number_of_Payments_Included_in_Total_Amount",
        "Form_of_Payment_or_Transfer_of_Value",
        "Nature_of_Payment_or_Transfer_of_Value",
        "City_of_Travel",
        "State_of_Travel",
        "Country_of_Travel",
        "Physician_Ownership_Indicator",
        "Third_Party_Payment_Recipient_Indicator",
        "Name_of_Third_Party_Entity_Receiving_Payment_or_Transfer_of_Value",
        "Charity_Indicator",
        "Third_Party_Equals_Covered_Recipient_Indicator",
        "Contextual_Information",
        "Delay_in_Publication_Indicator",
        "Record_ID",
        "Dispute_Status_for_Publication",
        "Related_Product_Indicator",
        "Covered_or_Noncovered_Indicator_1",
        "Indicate_Drug_or_Biological_or_Device_or_Medical_Supply_1",
        "Product_Category_or_Therapeutic_Area_1",
        "Name_of_Drug_or_Biological_or_Device_or_Medical_Supply_1",
        "Associated_Drug_or_Biological_NDC_1",
        "Covered_or_Noncovered_Indicator_2",
        "Indicate_Drug_or_Biological_or_Device_or_Medical_Supply_2",
        "Product_Category_or_Therapeutic_Area_2",
        "Name_of_Drug_or_Biological_or_Device_or_Medical_Supply_2",
        "Associated_Drug_or_Biological_NDC_2",
        "Covered_or_Noncovered_Indicator_3",
        "Indicate_Drug_or_Biological_or_Device_or_Medical_Supply_3",
        "Product_Category_or_Therapeutic_Area_3",
        "Name_of_Drug_or_Biological_or_Device_or_Medical_Supply_3",
        "Associated_Drug_or_Biological_NDC_3",
        "Covered_or_Noncovered_Indicator_4",
        "Indicate_Drug_or_Biological_or_Device_or_Medical_Supply_4",
        "Product_Category_or_Therapeutic_Area_4",
        "Name_of_Drug_or_Biological_or_Device_or_Medical_Supply_4",
        "Associated_Drug_or_Biological_NDC_4",
        "Covered_or_Noncovered_Indicator_5",
        "Indicate_Drug_or_Biological_or_Device_or_Medical_Supply_5",
        "Product_Category_or_Therapeutic_Area_5",
        "Name_of_Drug_or_Biological_or_Device_or_Medical_Supply_5",
        "Associated_Drug_or_Biological_NDC_5",
        "Program_Year",
        "Payment_Publication_Date"
    );

    private final String changeType;
    private final String coveredRecipientType;
    private final String teachingHospitalCcn;
    private final String teachingHospitalId;
    private final String teachingHospitalName;
    private final String physicianProfileId;
    private final String physicianFirstName;
    private final String physicianMiddleName;
    private final String physicianLastName;
    private final String physicianNameSuffix;
    private final String recipientPrimaryBusinessStreetAddressLine1;
    private final String recipientPrimaryBusinessStreetAddressLine2;
    private final String recipientCity;
    private final String recipientState;
    private final String recipientZipCode;
    private final String recipientCountry;
    private final String recipientProvince;
    private final String recipientPostalCode;
    private final String physicianPrimaryType;
    private final String physicianSpecialty;
    private final String physicianLicenseStateCode1;
    private final String physicianLicenseStateCode2;
    private final String physicianLicenseStateCode3;
    private final String physicianLicenseStateCode4;
    private final String physicianLicenseStateCode5;
    private final String submittingApplicableManufacturerOrApplicableGpoName;
    private final String applicableManufacturerOrApplicableGpoMakingPaymentId;
    private final String applicableManufacturerOrApplicableGpoMakingPaymentName;
    private final String applicableManufacturerOrApplicableGpoMakingPaymentState;
    private final String applicableManufacturerOrApplicableGpoMakingPaymentCountry;
    private final String totalAmountOfPaymentUsdollars;
    private final String dateOfPayment;
    private final String numberOfPaymentsIncludedInTotalAmount;
    private final String formOfPaymentOrTransferOfValue;
    private final String natureOfPaymentOrTransferOfValue;
    private final String cityOfTravel;
    private final String stateOfTravel;
    private final String countryOfTravel;
    private final String physicianOwnershipIndicator;
    private final String thirdPartyPaymentRecipientIndicator;
    private final String nameOfThirdPartyEntityReceivingPaymentOrTransferOfValue;
    private final String charityIndicator;
    private final String thirdPartyEqualsCoveredRecipientIndicator;
    private final String contextualInformation;
    private final String delayInPublicationIndicator;
    private final String recordId;
    private final String disputeStatusForPublication;
    private final String relatedProductIndicator;
    private final String coveredOrNoncoveredIndicator1;
    private final String indicateDrugOrBiologicalOrDeviceOrMedicalSupply1;
    private final String productCategoryOrTherapeuticArea1;
    private final String nameOfDrugOrBiologicalOrDeviceOrMedicalSupply1;
    private final String associatedDrugOrBiologicalNdc1;
    private final String coveredOrNoncoveredIndicator2;
    private final String indicateDrugOrBiologicalOrDeviceOrMedicalSupply2;
    private final String productCategoryOrTherapeuticArea2;
    private final String nameOfDrugOrBiologicalOrDeviceOrMedicalSupply2;
    private final String associatedDrugOrBiologicalNdc2;
    private final String coveredOrNoncoveredIndicator3;
    private final String indicateDrugOrBiologicalOrDeviceOrMedicalSupply3;
    private final String productCategoryOrTherapeuticArea3;
    private final String nameOfDrugOrBiologicalOrDeviceOrMedicalSupply3;
    private final String associatedDrugOrBiologicalNdc3;
    private final String coveredOrNoncoveredIndicator4;
    private final String indicateDrugOrBiologicalOrDeviceOrMedicalSupply4;
    private final String productCategoryOrTherapeuticArea4;
    private final String nameOfDrugOrBiologicalOrDeviceOrMedicalSupply4;
    private final String associatedDrugOrBiologicalNdc4;
    private final String coveredOrNoncoveredIndicator5;
    private final String indicateDrugOrBiologicalOrDeviceOrMedicalSupply5;
    private final String productCategoryOrTherapeuticArea5;
    private final String nameOfDrugOrBiologicalOrDeviceOrMedicalSupply5;
    private final String associatedDrugOrBiologicalNdc5;
    private final String programYear;
    private final String paymentPublicationDate;

    private PaymentRecord(List<String> values) {
        this.changeType = values.get(0);
        this.coveredRecipientType = values.get(1);
        this.teachingHospitalCcn = values.get(2);
        this.teachingHospitalId = values.get(3);
        this.teachingHospitalName = values.get(4);
        this.physicianProfileId = values.get(5);
        this.physicianFirstName = values.get(6);
        this.physicianMiddleName = values.get(7);
        this.physicianLastName = values.get(8);
        this.physicianNameSuffix = values.get(9);
        this.recipientPrimaryBusinessStreetAddressLine1 = values.get(10);
        this.recipientPrimaryBusinessStreetAddressLine2 = values.get(11);
        this.recipientCity = values.get(12);
        this.recipientState = values.get(13);
        this.recipientZipCode = values.get(14);
        this.recipientCountry = values.get(15);
        this.recipientProvince = values.get(16);
        this.recipientPostalCode = values.get(17);
        this.physicianPrimaryType = values.get(18);
        this.physicianSpecialty = values.get(19);
        this.physicianLicenseStateCode1 = values.get(20);
        this.physicianLicenseStateCode2 = values.get(21);
        this.physicianLicenseStateCode3 = values.get(22);
        this.physicianLicenseStateCode4 = values.get(23);
        this.physicianLicenseStateCode5 = values.get(24);
        this.submittingApplicableManufacturerOrApplicableGpoName = values.get(25);
        this.applicableManufacturerOrApplicableGpoMakingPaymentId = values.get(26);
        this.applicableManufacturerOrApplicableGpoMakingPaymentName = values.get(27);
        this.applicableManufacturerOrApplicableGpoMakingPaymentState = values.get(28);
        this.applicableManufacturerOrApplicableGpoMakingPaymentCountry = values.get(29);
        this.totalAmountOfPaymentUsdollars = values.get(30);
        this.dateOfPayment = values.get(31);
        this.numberOfPaymentsIncludedInTotalAmount = values.get(32);
        this.formOfPaymentOrTransferOfValue = values.get(33);
        this.natureOfPaymentOrTransferOfValue = values.get(34);
        this.cityOfTravel = values.get(35);
        this.stateOfTravel = values.get(36);
        this.countryOfTravel = values.get(37);
        this.physicianOwnershipIndicator = values.get(38);
        this.thirdPartyPaymentRecipientIndicator = values.get(39);
        this.nameOfThirdPartyEntityReceivingPaymentOrTransferOfValue = values.get(40);
        this.charityIndicator = values.get(41);
        this.thirdPartyEqualsCoveredRecipientIndicator = values.get(42);
        this.contextualInformation = values.get(43);
        this.delayInPublicationIndicator = values.get(44);
        this.recordId = values.get(45);
        this.disputeStatusForPublication = values.get(46);
        this.relatedProductIndicator = values.get(47);
        this.coveredOrNoncoveredIndicator1 = values.get(48);
        this.indicateDrugOrBiologicalOrDeviceOrMedicalSupply1 = values.get(49);
        this.productCategoryOrTherapeuticArea1 = values.get(50);
        this.nameOfDrugOrBiologicalOrDeviceOrMedicalSupply1 = values.get(51);
        this.associatedDrugOrBiologicalNdc1 = values.get(52);
        this.coveredOrNoncoveredIndicator2 = values.get(53);
        this.indicateDrugOrBiologicalOrDeviceOrMedicalSupply2 = values.get(54);
        this.productCategoryOrTherapeuticArea2 = values.get(55);
        this.nameOfDrugOrBiologicalOrDeviceOrMedicalSupply2 = values.get(56);
        this.associatedDrugOrBiologicalNdc2 = values.get(57);
        this.coveredOrNoncoveredIndicator3 = values.get(58);
        this.indicateDrugOrBiologicalOrDeviceOrMedicalSupply3 = values.get(59);
        this.productCategoryOrTherapeuticArea3 = values.get(60);
        this.nameOfDrugOrBiologicalOrDeviceOrMedicalSupply3 = values.get(61);
        this.associatedDrugOrBiologicalNdc3 = values.get(62);
        this.coveredOrNoncoveredIndicator4 = values.get(63);
        this.indicateDrugOrBiologicalOrDeviceOrMedicalSupply4 = values.get(64);
        this.productCategoryOrTherapeuticArea4 = values.get(65);
        this.nameOfDrugOrBiologicalOrDeviceOrMedicalSupply4 = values.get(66);
        this.associatedDrugOrBiologicalNdc4 = values.get(67);
        this.coveredOrNoncoveredIndicator5 = values.get(68);
        this.indicateDrugOrBiologicalOrDeviceOrMedicalSupply5 = values.get(69);
        this.productCategoryOrTherapeuticArea5 = values.get(70);
        this.nameOfDrugOrBiologicalOrDeviceOrMedicalSupply5 = values.get(71);
        this.associatedDrugOrBiologicalNdc5 = values.get(72);
        this.programYear = values.get(73);
        this.paymentPublicationDate = values.get(74);
    }

    public static PaymentRecord fromValues(List<String> values) {
        if (values.size() != HEADERS.size()) {
            throw new IllegalArgumentException(
                "Data line has %d values but the 1K_RECORDS.csv schema has %d columns"
                    .formatted(values.size(), HEADERS.size())
            );
        }
        return new PaymentRecord(values);
    }

    public static void validateHeaders(List<String> headers) {
        if (!HEADERS.equals(headers)) {
            throw new IllegalArgumentException("CSV header does not match the 1K_RECORDS.csv schema");
        }
    }

    public String getChangeType() {
        return changeType;
    }

    public String getCoveredRecipientType() {
        return coveredRecipientType;
    }

    public String getTeachingHospitalCcn() {
        return teachingHospitalCcn;
    }

    public String getTeachingHospitalId() {
        return teachingHospitalId;
    }

    public String getTeachingHospitalName() {
        return teachingHospitalName;
    }

    public String getPhysicianProfileId() {
        return physicianProfileId;
    }

    public String getPhysicianFirstName() {
        return physicianFirstName;
    }

    public String getPhysicianMiddleName() {
        return physicianMiddleName;
    }

    public String getPhysicianLastName() {
        return physicianLastName;
    }

    public String getPhysicianNameSuffix() {
        return physicianNameSuffix;
    }

    public String getRecipientPrimaryBusinessStreetAddressLine1() {
        return recipientPrimaryBusinessStreetAddressLine1;
    }

    public String getRecipientPrimaryBusinessStreetAddressLine2() {
        return recipientPrimaryBusinessStreetAddressLine2;
    }

    public String getRecipientCity() {
        return recipientCity;
    }

    public String getRecipientState() {
        return recipientState;
    }

    public String getRecipientZipCode() {
        return recipientZipCode;
    }

    public String getRecipientCountry() {
        return recipientCountry;
    }

    public String getRecipientProvince() {
        return recipientProvince;
    }

    public String getRecipientPostalCode() {
        return recipientPostalCode;
    }

    public String getPhysicianPrimaryType() {
        return physicianPrimaryType;
    }

    public String getPhysicianSpecialty() {
        return physicianSpecialty;
    }

    public String getPhysicianLicenseStateCode1() {
        return physicianLicenseStateCode1;
    }

    public String getPhysicianLicenseStateCode2() {
        return physicianLicenseStateCode2;
    }

    public String getPhysicianLicenseStateCode3() {
        return physicianLicenseStateCode3;
    }

    public String getPhysicianLicenseStateCode4() {
        return physicianLicenseStateCode4;
    }

    public String getPhysicianLicenseStateCode5() {
        return physicianLicenseStateCode5;
    }

    public String getSubmittingApplicableManufacturerOrApplicableGpoName() {
        return submittingApplicableManufacturerOrApplicableGpoName;
    }

    public String getApplicableManufacturerOrApplicableGpoMakingPaymentId() {
        return applicableManufacturerOrApplicableGpoMakingPaymentId;
    }

    public String getApplicableManufacturerOrApplicableGpoMakingPaymentName() {
        return applicableManufacturerOrApplicableGpoMakingPaymentName;
    }

    public String getApplicableManufacturerOrApplicableGpoMakingPaymentState() {
        return applicableManufacturerOrApplicableGpoMakingPaymentState;
    }

    public String getApplicableManufacturerOrApplicableGpoMakingPaymentCountry() {
        return applicableManufacturerOrApplicableGpoMakingPaymentCountry;
    }

    public String getTotalAmountOfPaymentUsdollars() {
        return totalAmountOfPaymentUsdollars;
    }

    public String getDateOfPayment() {
        return dateOfPayment;
    }

    public String getNumberOfPaymentsIncludedInTotalAmount() {
        return numberOfPaymentsIncludedInTotalAmount;
    }

    public String getFormOfPaymentOrTransferOfValue() {
        return formOfPaymentOrTransferOfValue;
    }

    public String getNatureOfPaymentOrTransferOfValue() {
        return natureOfPaymentOrTransferOfValue;
    }

    public String getCityOfTravel() {
        return cityOfTravel;
    }

    public String getStateOfTravel() {
        return stateOfTravel;
    }

    public String getCountryOfTravel() {
        return countryOfTravel;
    }

    public String getPhysicianOwnershipIndicator() {
        return physicianOwnershipIndicator;
    }

    public String getThirdPartyPaymentRecipientIndicator() {
        return thirdPartyPaymentRecipientIndicator;
    }

    public String getNameOfThirdPartyEntityReceivingPaymentOrTransferOfValue() {
        return nameOfThirdPartyEntityReceivingPaymentOrTransferOfValue;
    }

    public String getCharityIndicator() {
        return charityIndicator;
    }

    public String getThirdPartyEqualsCoveredRecipientIndicator() {
        return thirdPartyEqualsCoveredRecipientIndicator;
    }

    public String getContextualInformation() {
        return contextualInformation;
    }

    public String getDelayInPublicationIndicator() {
        return delayInPublicationIndicator;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getDisputeStatusForPublication() {
        return disputeStatusForPublication;
    }

    public String getRelatedProductIndicator() {
        return relatedProductIndicator;
    }

    public String getCoveredOrNoncoveredIndicator1() {
        return coveredOrNoncoveredIndicator1;
    }

    public String getIndicateDrugOrBiologicalOrDeviceOrMedicalSupply1() {
        return indicateDrugOrBiologicalOrDeviceOrMedicalSupply1;
    }

    public String getProductCategoryOrTherapeuticArea1() {
        return productCategoryOrTherapeuticArea1;
    }

    public String getNameOfDrugOrBiologicalOrDeviceOrMedicalSupply1() {
        return nameOfDrugOrBiologicalOrDeviceOrMedicalSupply1;
    }

    public String getAssociatedDrugOrBiologicalNdc1() {
        return associatedDrugOrBiologicalNdc1;
    }

    public String getCoveredOrNoncoveredIndicator2() {
        return coveredOrNoncoveredIndicator2;
    }

    public String getIndicateDrugOrBiologicalOrDeviceOrMedicalSupply2() {
        return indicateDrugOrBiologicalOrDeviceOrMedicalSupply2;
    }

    public String getProductCategoryOrTherapeuticArea2() {
        return productCategoryOrTherapeuticArea2;
    }

    public String getNameOfDrugOrBiologicalOrDeviceOrMedicalSupply2() {
        return nameOfDrugOrBiologicalOrDeviceOrMedicalSupply2;
    }

    public String getAssociatedDrugOrBiologicalNdc2() {
        return associatedDrugOrBiologicalNdc2;
    }

    public String getCoveredOrNoncoveredIndicator3() {
        return coveredOrNoncoveredIndicator3;
    }

    public String getIndicateDrugOrBiologicalOrDeviceOrMedicalSupply3() {
        return indicateDrugOrBiologicalOrDeviceOrMedicalSupply3;
    }

    public String getProductCategoryOrTherapeuticArea3() {
        return productCategoryOrTherapeuticArea3;
    }

    public String getNameOfDrugOrBiologicalOrDeviceOrMedicalSupply3() {
        return nameOfDrugOrBiologicalOrDeviceOrMedicalSupply3;
    }

    public String getAssociatedDrugOrBiologicalNdc3() {
        return associatedDrugOrBiologicalNdc3;
    }

    public String getCoveredOrNoncoveredIndicator4() {
        return coveredOrNoncoveredIndicator4;
    }

    public String getIndicateDrugOrBiologicalOrDeviceOrMedicalSupply4() {
        return indicateDrugOrBiologicalOrDeviceOrMedicalSupply4;
    }

    public String getProductCategoryOrTherapeuticArea4() {
        return productCategoryOrTherapeuticArea4;
    }

    public String getNameOfDrugOrBiologicalOrDeviceOrMedicalSupply4() {
        return nameOfDrugOrBiologicalOrDeviceOrMedicalSupply4;
    }

    public String getAssociatedDrugOrBiologicalNdc4() {
        return associatedDrugOrBiologicalNdc4;
    }

    public String getCoveredOrNoncoveredIndicator5() {
        return coveredOrNoncoveredIndicator5;
    }

    public String getIndicateDrugOrBiologicalOrDeviceOrMedicalSupply5() {
        return indicateDrugOrBiologicalOrDeviceOrMedicalSupply5;
    }

    public String getProductCategoryOrTherapeuticArea5() {
        return productCategoryOrTherapeuticArea5;
    }

    public String getNameOfDrugOrBiologicalOrDeviceOrMedicalSupply5() {
        return nameOfDrugOrBiologicalOrDeviceOrMedicalSupply5;
    }

    public String getAssociatedDrugOrBiologicalNdc5() {
        return associatedDrugOrBiologicalNdc5;
    }

    public String getProgramYear() {
        return programYear;
    }

    public String getPaymentPublicationDate() {
        return paymentPublicationDate;
    }

}