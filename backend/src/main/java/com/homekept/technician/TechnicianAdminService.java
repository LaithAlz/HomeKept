package com.homekept.technician;

import com.homekept.technician.dto.CreateTechnicianRequest;
import com.homekept.technician.dto.TechnicianProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin service for managing technician profiles.
 *
 * <p>MVP note: founders ARE the technicians. This service creates {@code technician_profile}
 * rows for users who already have the TECHNICIAN role in the identity domain. Role
 * assignment itself is the founder's concern (done outside this service — e.g. via a DB
 * seed or identity-domain endpoint).
 *
 * <h2>Domain boundaries</h2>
 * <p>This service does NOT call the identity repository or entity directly. It stores
 * the {@code userId} as a bare foreign key. User existence is enforced by the DB FK
 * constraint ({@code technician_profile.user_id REFERENCES users(id)}), which surfaces
 * as a {@link DataIntegrityViolationException} if the user doesn't exist — handled by
 * {@link com.homekept.common.GlobalExceptionHandler} as a 409.
 */
@Service
public class TechnicianAdminService {

    private static final Logger log = LoggerFactory.getLogger(TechnicianAdminService.class);

    private final TechnicianProfileRepository technicianProfileRepository;

    public TechnicianAdminService(TechnicianProfileRepository technicianProfileRepository) {
        this.technicianProfileRepository = technicianProfileRepository;
    }

    /**
     * Creates a {@code technician_profile} for an existing user.
     *
     * <p>Idempotency: if a profile already exists for this user, throws
     * {@link TechnicianAlreadyExistsException} (409). The DB unique constraint on
     * {@code user_id} is the last line of defence.
     *
     * @param request the onboarding data
     * @return the created profile
     */
    @Transactional
    public TechnicianProfileResponse createProfile(CreateTechnicianRequest request) {
        if (technicianProfileRepository.existsByUserId(request.userId())) {
            throw new TechnicianAlreadyExistsException(
                    "A technician profile already exists for userId=" + request.userId());
        }

        TechnicianProfile profile = new TechnicianProfile(
                request.userId(),
                request.employeeStatus(),
                request.hireDate(),
                request.fullyLoadedHourlyCostCents()
        );

        TechnicianProfile saved = technicianProfileRepository.save(profile);

        log.info("admin_technician_created profileId={} userId={}", saved.getId(), saved.getUserId());

        return toResponse(saved);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private TechnicianProfileResponse toResponse(TechnicianProfile p) {
        return new TechnicianProfileResponse(
                p.getId(),
                p.getUserId(),
                p.getEmployeeStatus(),
                p.getHireDate(),
                p.getFullyLoadedHourlyCostCents(),
                p.getCreatedAt()
        );
    }
}
