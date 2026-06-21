package com.homekept.booking;

/**
 * Channel that generated the walk-through booking lead.
 * Track from day 1 — by customer #50 the attribution data becomes valuable.
 * See arch doc §2.5.
 */
public enum LeadSource {
    NEXTDOOR,
    FACEBOOK_GROUP,
    REFERRAL,
    DOOR_KNOCK,
    WEBSITE_ORGANIC,
    WEBSITE_DIRECT,
    OTHER
}
