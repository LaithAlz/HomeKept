package com.homekept.subscription;

/**
 * Thrown when a checkout is requested for a plan/billing-cycle combination that has no
 * Stripe price id configured yet (e.g. COMPLETE's prices were cleared by
 * V12__remove_essential_and_founding.sql pending the founder creating new Stripe prices
 * at the repositioned amount).
 *
 * <p>Fails closed: checkout must never fall through to Stripe with a blank price id, and
 * must never charge a stale price. Mapped to HTTP 409 ({@code PLAN_NOT_PURCHASABLE}) in
 * {@link com.homekept.common.GlobalExceptionHandler}.
 */
public class PlanNotPurchasableException extends RuntimeException {

    public PlanNotPurchasableException() {
        super("This plan can't be purchased yet.");
    }
}
