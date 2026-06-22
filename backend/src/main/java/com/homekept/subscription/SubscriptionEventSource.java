package com.homekept.subscription;

/** Source of a subscription event. */
public enum SubscriptionEventSource {
    STRIPE_WEBHOOK,
    MANUAL,
    SYSTEM
}
