package com.passakornsdev.tlq.csv.query;

import java.math.BigDecimal;

public record TeachingHospitalPaymentAggregate(
    String teachingHospitalName,
    long recordCount,
    BigDecimal totalAmountOfPaymentsUsdollars
) {}
