package com.homekept.technician;

import com.homekept.identity.UserQueryService;
import com.homekept.technician.dto.AdminTechnicianListItem;
import com.homekept.technician.dto.CreateTechnicianRequest;
import com.homekept.technician.dto.TechnicianProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 * {@link com.homekept.common.GlobalExceptionHandler} as a 409. Roster reads that need a
 * technician's name/email/status go through {@link UserQueryService} — never the identity
 * repository or entity directly.
 */
@Service
public class TechnicianAdminService {

    private static final Logger log = LoggerFactory.getLogger(TechnicianAdminService.class);

    private final TechnicianProfileRepository technicianProfileRepository;
    private final UserQueryService userQueryService;

    public TechnicianAdminService(TechnicianProfileRepository technicianProfileRepository,
                                  UserQueryService userQueryService) {
        this.technicianProfileRepository = technicianProfileRepository;
        this.userQueryService = userQueryService;
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

    /**
     * Returns the full technician roster, newest first. Small dataset at MVP (two rows
     * at launch — see {@link TechnicianProfile}), so no pagination is offered.
     *
     * <p>Resolves name/email/role/status for each profile's {@code userId} in a single
     * batch call to {@link UserQueryService} (identity domain's service, not its
     * repository). If the linked user is somehow missing, the identity fields are null
     * rather than dropping the row.
     */
    @Transactional(readOnly = true)
    public List<AdminTechnicianListItem> listTechnicians() {
        List<TechnicianProfile> profiles = technicianProfileRepository.findAllByOrderByIdDesc();

        List<Long> userIds = profiles.stream()
                .map(TechnicianProfile::getUserId)
                .collect(Collectors.toList());
        Map<Long, UserQueryService.UserSummary> summaries = userQueryService.findSummariesByIds(userIds);

        return profiles.stream()
                .map(p -> toListItem(p, summaries.get(p.getUserId())))
                .collect(Collectors.toList());
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

    private AdminTechnicianListItem toListItem(TechnicianProfile p, UserQueryService.UserSummary summary) {
        return new AdminTechnicianListItem(
                p.getId(),
                p.getUserId(),
                summary != null ? summary.firstName() : null,
                summary != null ? summary.lastName() : null,
                summary != null ? summary.email() : null,
                summary != null ? summary.role() : null,
                summary != null ? summary.status() : null,
                p.getEmployeeStatus(),
                p.getHireDate(),
                p.getFullyLoadedHourlyCostCents(),
                p.getCreatedAt()
        );
    }
}
