package com.homekept.visit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link VisitStateMachine}.
 * No Spring context — instantiated directly. Covers every legal and illegal transition
 * per arch doc §4.2 and the state machine's own Javadoc.
 *
 * <p>Legal transitions:
 * <pre>
 *   SCHEDULED    → IN_PROGRESS
 *   SCHEDULED    → CANCELLED
 *   SCHEDULED    → RESCHEDULED
 *   IN_PROGRESS  → COMPLETED
 *   IN_PROGRESS  → INCOMPLETE
 * </pre>
 *
 * <p>Terminals (no outbound transitions): COMPLETED, INCOMPLETE, CANCELLED, RESCHEDULED.
 */
class VisitStateMachineTest {

    private final VisitStateMachine machine = new VisitStateMachine();

    // ── Legal transitions from SCHEDULED ─────────────────────────────────────

    @Test
    void scheduled_to_inProgress_isLegal() {
        assertThat(machine.canTransition(VisitStatus.SCHEDULED, VisitStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void scheduled_to_cancelled_isLegal() {
        assertThat(machine.canTransition(VisitStatus.SCHEDULED, VisitStatus.CANCELLED)).isTrue();
    }

    @Test
    void scheduled_to_rescheduled_isLegal() {
        assertThat(machine.canTransition(VisitStatus.SCHEDULED, VisitStatus.RESCHEDULED)).isTrue();
    }

    // ── Legal transitions from IN_PROGRESS ───────────────────────────────────

    @Test
    void inProgress_to_completed_isLegal() {
        assertThat(machine.canTransition(VisitStatus.IN_PROGRESS, VisitStatus.COMPLETED)).isTrue();
    }

    @Test
    void inProgress_to_incomplete_isLegal() {
        assertThat(machine.canTransition(VisitStatus.IN_PROGRESS, VisitStatus.INCOMPLETE)).isTrue();
    }

    // ── Illegal transitions from SCHEDULED ───────────────────────────────────

    @Test
    void scheduled_to_scheduled_isIllegal() {
        assertThat(machine.canTransition(VisitStatus.SCHEDULED, VisitStatus.SCHEDULED)).isFalse();
    }

    @Test
    void scheduled_to_completed_isIllegal() {
        assertThat(machine.canTransition(VisitStatus.SCHEDULED, VisitStatus.COMPLETED)).isFalse();
    }

    @Test
    void scheduled_to_incomplete_isIllegal() {
        assertThat(machine.canTransition(VisitStatus.SCHEDULED, VisitStatus.INCOMPLETE)).isFalse();
    }

    // ── Illegal transitions from IN_PROGRESS ─────────────────────────────────

    @Test
    void inProgress_to_inProgress_isIllegal() {
        assertThat(machine.canTransition(VisitStatus.IN_PROGRESS, VisitStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void inProgress_to_scheduled_isIllegal() {
        assertThat(machine.canTransition(VisitStatus.IN_PROGRESS, VisitStatus.SCHEDULED)).isFalse();
    }

    @Test
    void inProgress_to_cancelled_isIllegal() {
        assertThat(machine.canTransition(VisitStatus.IN_PROGRESS, VisitStatus.CANCELLED)).isFalse();
    }

    @Test
    void inProgress_to_rescheduled_isIllegal() {
        assertThat(machine.canTransition(VisitStatus.IN_PROGRESS, VisitStatus.RESCHEDULED)).isFalse();
    }

    // ── Terminal states: no outbound transitions ──────────────────────────────

    @Test
    void completed_hasNoOutboundTransitions() {
        for (VisitStatus to : VisitStatus.values()) {
            assertThat(machine.canTransition(VisitStatus.COMPLETED, to))
                    .as("COMPLETED → %s should be illegal", to)
                    .isFalse();
        }
    }

    @Test
    void incomplete_hasNoOutboundTransitions() {
        for (VisitStatus to : VisitStatus.values()) {
            assertThat(machine.canTransition(VisitStatus.INCOMPLETE, to))
                    .as("INCOMPLETE → %s should be illegal", to)
                    .isFalse();
        }
    }

    @Test
    void cancelled_hasNoOutboundTransitions() {
        for (VisitStatus to : VisitStatus.values()) {
            assertThat(machine.canTransition(VisitStatus.CANCELLED, to))
                    .as("CANCELLED → %s should be illegal", to)
                    .isFalse();
        }
    }

    @Test
    void rescheduled_hasNoOutboundTransitions() {
        for (VisitStatus to : VisitStatus.values()) {
            assertThat(machine.canTransition(VisitStatus.RESCHEDULED, to))
                    .as("RESCHEDULED → %s should be illegal", to)
                    .isFalse();
        }
    }

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void nullFrom_returnsFalse() {
        assertThat(machine.canTransition(null, VisitStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void nullTo_returnsFalse() {
        assertThat(machine.canTransition(VisitStatus.SCHEDULED, null)).isFalse();
    }

    @Test
    void bothNull_returnsFalse() {
        assertThat(machine.canTransition(null, null)).isFalse();
    }
}
