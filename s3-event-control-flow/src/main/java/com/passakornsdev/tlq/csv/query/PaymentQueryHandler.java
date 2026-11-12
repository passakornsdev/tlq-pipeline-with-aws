package com.passakornsdev.tlq.csv.query;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.passakornsdev.tlq.csv.database.DatabaseConfig;

public class PaymentQueryHandler implements RequestHandler<Object, PaymentAggregateResponse> {
    private final PaymentQueryRepository repository;

    public PaymentQueryHandler() {
        this(new PaymentQueryRepository(DatabaseConfig.fromEnvironment()));
    }

    public static void main(String[] args) {
        var res = new PaymentQueryHandler().handleRequest(null ,null);
        System.out.println(res);
    }

    PaymentQueryHandler(PaymentQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public PaymentAggregateResponse handleRequest(Object request, Context context) {
        return repository.query();
    }
}
