package com.passakornsdev.tlq.csv.query;

import java.util.List;

public record PaymentAggregateResponse(
    PaymentAggregate overall,
    List<RecipientStatePaymentAggregate> byRecipientState,
    List<TeachingHospitalPaymentAggregate> byTeachingHospitalName
) {}
