package com.passakornsdev.tlq.csv.transform;

import java.util.List;
import java.util.Locale;

public final class PaymentRecordSchema {
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
    private static final List<String> DATABASE_COLUMNS = HEADERS.stream()
        .map(PaymentRecordSchema::databaseColumnName)
        .toList();

    private PaymentRecordSchema() {
    }

    public static void validateHeaders(List<String> headers) {
        if (!HEADERS.equals(headers)) {
            throw new IllegalArgumentException("CSV header does not match the 1K_RECORDS.csv schema");
        }
    }

    public static void validateRow(List<String> values) {
        if (values == null || values.size() != HEADERS.size()) {
            int actualSize = values == null ? 0 : values.size();
            throw new IllegalArgumentException(
                "Data line has %d values but the 1K_RECORDS.csv schema has %d columns"
                    .formatted(actualSize, HEADERS.size())
            );
        }
    }

    public static List<String> databaseColumnNames() {
        return DATABASE_COLUMNS;
    }

    private static String databaseColumnName(String header) {
        String[] parts = header.split("_");
        StringBuilder columnName = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index].toLowerCase(Locale.ROOT);
            columnName.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return columnName.toString();
    }
}
