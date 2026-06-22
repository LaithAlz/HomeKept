package com.homekept.property;

/**
 * Property type enum matching the booking domain's {@link com.homekept.booking.PropertyType}.
 * Duplicated here so each domain owns its vocabulary; the booking property type is
 * mapped to this enum when a property is created during activation.
 */
public enum PropertyType {
    DETACHED,
    SEMI,
    TOWNHOUSE
}
