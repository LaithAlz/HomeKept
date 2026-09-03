package com.homekept.technician;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link TechnicianProfile}.
 */
public interface TechnicianProfileRepository extends JpaRepository<TechnicianProfile, Long> {

    /** Returns true if a profile already exists for this user (idempotency guard). */
    boolean existsByUserId(Long userId);

    /**
     * Admin roster: all technician profiles, newest first. The roster is small at MVP
     * (see {@link TechnicianProfile} — two rows at launch), so no pagination is offered.
     */
    List<TechnicianProfile> findAllByOrderByIdDesc();
}
