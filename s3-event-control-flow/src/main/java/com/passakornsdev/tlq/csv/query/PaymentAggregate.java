package com.passakornsdev.tlq.csv.query;

import java.math.BigDecimal;

public record PaymentAggregate(long recordCount, BigDecimal totalAmountOfPaymentsUsdollars) {}
