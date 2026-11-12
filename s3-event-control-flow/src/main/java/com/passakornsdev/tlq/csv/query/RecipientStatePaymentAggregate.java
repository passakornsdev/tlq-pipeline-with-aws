package com.passakornsdev.tlq.csv.query;

import java.math.BigDecimal;

public record RecipientStatePaymentAggregate(
    String recipientState,
    long recordCount,
    BigDecimal totalAmountOfPaymentsUsdollars
) {}
