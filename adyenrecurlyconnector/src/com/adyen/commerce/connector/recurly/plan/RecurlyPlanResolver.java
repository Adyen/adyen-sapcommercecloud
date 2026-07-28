package com.adyen.commerce.connector.recurly.plan;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.BillingException;

public interface RecurlyPlanResolver
{
    PlanRef resolve(PlanResolutionRequest request) throws BillingException;
}
