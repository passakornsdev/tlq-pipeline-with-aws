-- Schema used by the loader and payment aggregate query Lambda.

CREATE TABLE IF NOT EXISTS loaded_destination_objects (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    object_key VARCHAR(1024) NOT NULL,
    loaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payments_raw (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(1024) NOT NULL,
    source_object_key VARCHAR(1024) NOT NULL,
    changeType TEXT,
    coveredRecipientType TEXT,
    teachingHospitalCcn TEXT,
    teachingHospitalId TEXT,
    teachingHospitalName TEXT,
    physicianProfileId TEXT,
    physicianFirstName TEXT,
    physicianMiddleName TEXT,
    physicianLastName TEXT,
    physicianNameSuffix TEXT,
    recipientPrimaryBusinessStreetAddressLine1 TEXT,
    recipientPrimaryBusinessStreetAddressLine2 TEXT,
    recipientCity TEXT,
    recipientState VARCHAR(255),
    recipientZipCode TEXT,
    recipientCountry TEXT,
    recipientProvince TEXT,
    recipientPostalCode TEXT,
    physicianPrimaryType TEXT,
    physicianSpecialty TEXT,
    physicianLicenseStateCode1 TEXT,
    physicianLicenseStateCode2 TEXT,
    physicianLicenseStateCode3 TEXT,
    physicianLicenseStateCode4 TEXT,
    physicianLicenseStateCode5 TEXT,
    submittingApplicableManufacturerOrApplicableGpoName TEXT,
    applicableManufacturerOrApplicableGpoMakingPaymentId TEXT,
    applicableManufacturerOrApplicableGpoMakingPaymentName TEXT,
    applicableManufacturerOrApplicableGpoMakingPaymentState TEXT,
    applicableManufacturerOrApplicableGpoMakingPaymentCountry TEXT,
    totalAmountOfPaymentUsdollars VARCHAR(64),
    dateOfPayment TEXT,
    numberOfPaymentsIncludedInTotalAmount TEXT,
    formOfPaymentOrTransferOfValue TEXT,
    natureOfPaymentOrTransferOfValue TEXT,
    cityOfTravel TEXT,
    stateOfTravel TEXT,
    countryOfTravel TEXT,
    physicianOwnershipIndicator TEXT,
    thirdPartyPaymentRecipientIndicator TEXT,
    nameOfThirdPartyEntityReceivingPaymentOrTransferOfValue TEXT,
    charityIndicator TEXT,
    thirdPartyEqualsCoveredRecipientIndicator TEXT,
    contextualInformation TEXT,
    delayInPublicationIndicator TEXT,
    recordId TEXT,
    disputeStatusForPublication TEXT,
    relatedProductIndicator TEXT,
    coveredOrNoncoveredIndicator1 TEXT,
    indicateDrugOrBiologicalOrDeviceOrMedicalSupply1 TEXT,
    productCategoryOrTherapeuticArea1 TEXT,
    nameOfDrugOrBiologicalOrDeviceOrMedicalSupply1 TEXT,
    associatedDrugOrBiologicalNdc1 TEXT,
    coveredOrNoncoveredIndicator2 TEXT,
    indicateDrugOrBiologicalOrDeviceOrMedicalSupply2 TEXT,
    productCategoryOrTherapeuticArea2 TEXT,
    nameOfDrugOrBiologicalOrDeviceOrMedicalSupply2 TEXT,
    associatedDrugOrBiologicalNdc2 TEXT,
    coveredOrNoncoveredIndicator3 TEXT,
    indicateDrugOrBiologicalOrDeviceOrMedicalSupply3 TEXT,
    productCategoryOrTherapeuticArea3 TEXT,
    nameOfDrugOrBiologicalOrDeviceOrMedicalSupply3 TEXT,
    associatedDrugOrBiologicalNdc3 TEXT,
    coveredOrNoncoveredIndicator4 TEXT,
    indicateDrugOrBiologicalOrDeviceOrMedicalSupply4 TEXT,
    productCategoryOrTherapeuticArea4 TEXT,
    nameOfDrugOrBiologicalOrDeviceOrMedicalSupply4 TEXT,
    associatedDrugOrBiologicalNdc4 TEXT,
    coveredOrNoncoveredIndicator5 TEXT,
    indicateDrugOrBiologicalOrDeviceOrMedicalSupply5 TEXT,
    productCategoryOrTherapeuticArea5 TEXT,
    nameOfDrugOrBiologicalOrDeviceOrMedicalSupply5 TEXT,
    associatedDrugOrBiologicalNdc5 TEXT,
    programYear VARCHAR(32),
    paymentPublicationDate TEXT,
    loaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_payments_raw_recipient_state (recipientState),
    INDEX idx_payments_raw_teaching_hospital_name (teachingHospitalName(255))
);

-- Invalid or blank amount values are excluded from sums but remain included
-- in record counts.
WITH payment_amounts AS (
    SELECT
        recipientState,
        teachingHospitalName,
        CASE
            WHEN TRIM(totalAmountOfPaymentUsdollars) REGEXP '^-?[0-9]+(\\.[0-9]+)?$'
            THEN CAST(TRIM(totalAmountOfPaymentUsdollars) AS DECIMAL(30, 2))
            ELSE 0
        END AS amount
    FROM payments_raw
)
SELECT COUNT(*) AS record_count, COALESCE(SUM(amount), 0) AS total_amount
FROM payment_amounts;

WITH payment_amounts AS (
    SELECT
        recipientState,
        teachingHospitalName,
        CASE
            WHEN TRIM(totalAmountOfPaymentUsdollars) REGEXP '^-?[0-9]+(\\.[0-9]+)?$'
            THEN CAST(TRIM(totalAmountOfPaymentUsdollars) AS DECIMAL(30, 2))
            ELSE 0
        END AS amount
    FROM payments_raw
)
SELECT
    COALESCE(NULLIF(recipientState, ''), 'UNIDENTIFIED'),
    COUNT(*) AS record_count,
    COALESCE(SUM(amount), 0) AS total_amount
FROM payment_amounts
GROUP BY recipientState
ORDER BY recipientState;

WITH payment_amounts AS (
    SELECT
        recipientState,
        teachingHospitalName,
        CASE
            WHEN TRIM(totalAmountOfPaymentUsdollars) REGEXP '^-?[0-9]+(\\.[0-9]+)?$'
            THEN CAST(TRIM(totalAmountOfPaymentUsdollars) AS DECIMAL(30, 2))
            ELSE 0
        END AS amount
    FROM payments_raw
)
SELECT
    COALESCE(NULLIF(teachingHospitalName, ''), 'UNIDENTIFIED'),
    COUNT(*) AS record_count,
    COALESCE(SUM(amount), 0) AS total_amount
FROM payment_amounts
GROUP BY teachingHospitalName
ORDER BY teachingHospitalName;
