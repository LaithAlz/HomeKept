package com.homekept.identity.dto;

import com.homekept.identity.Role;
import com.homekept.identity.User;

/**
 * Response body for GET /api/auth/me.
 * Never exposes the password hash or internal fields.
 */
public record MeResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Role role
) {
    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
