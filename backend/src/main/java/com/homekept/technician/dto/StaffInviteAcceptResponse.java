package com.homekept.technician.dto;

/**
 * Response body for {@code POST /api/staff/invite/accept}. No role field — the role is
 * always TECHNICIAN by construction.
 */
public record StaffInviteAcceptResponse(Long userId) {}
