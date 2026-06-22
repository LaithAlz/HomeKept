package com.homekept.subscription;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link SubscriberStateMachine}.
 * No Spring context — instantiated directly. Covers every legal and illegal transition
 * per arch doc §4.1 and the state machine's own Javadoc.
 *
 * <p>Legal transitions:
 * <pre>
 *   PENDING_ACTIVATION → ACTIVE
 *   PENDING_ACTIVATION → CANCELLED
 *   ACTIVE             → PAUSED
 *   ACTIVE             → PAYMENT_ISSUE
 *   ACTIVE             → CANCELLED
 *   PAUSED             → ACTIVE
 *   PAUSED             → CANCELLED
 *   PAYMENT_ISSUE      → ACTIVE
 *   PAYMENT_ISSUE      → CANCELLED
 * </pre>
 *
 * <p>Terminal state: CANCELLED — no outbound transitions.
 */
class SubscriberStateMachineTest {

    private final SubscriberStateMachine machine = new SubscriberStateMachine();

    // ── Legal transitions from PENDING_ACTIVATION ─────────────────────────────

    @Test
    void pendingActivation_to_active_isLegal() {
        assertThat(machine.canTransition(SubscriberStatus.PENDING_ACTIVATION, SubscriberStatus.ACTIVE)).isTrue();
    }

    @Test
    void pendingActivation_to_cancelled_isLegal() {
        assertThat(machine.canTransition(SubscriberStatus.PENDING_ACTIVATION, SubscriberStatus.CANCELLED)).isTrue();
    }

    // ── Legal transitions from ACTIVE ────────────────────────────────────────

    @Test
    void active_to_paused_isLegal() {
        assertThat(machine.canTransition(SubscriberStatus.ACTIVE, SubscriberStatus.PAUSED)).isTrue();
    }

    @Test
    void active_to_paymentIssue_isLegal() {
        assertThat(machine.canTransition(SubscriberStatus.ACTIVE, SubscriberStatus.PAYMENT_ISSUE)).isTrue();
    }

    @Test
    void active_to_cancelled_isLegal() {
        assertThat(machine.canTransition(SubscriberStatus.ACTIVE, SubscriberStatus.CANCELLED)).isTrue();
    }

    // ── Legal transitions from PAUSED ────────────────────────────────────────

    @Test
    void paused_to_active_isLegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAUSED, SubscriberStatus.ACTIVE)).isTrue();
    }

    @Test
    void paused_to_cancelled_isLegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAUSED, SubscriberStatus.CANCELLED)).isTrue();
    }

    // ── Legal transitions from PAYMENT_ISSUE ─────────────────────────────────

    @Test
    void paymentIssue_to_active_isLegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAYMENT_ISSUE, SubscriberStatus.ACTIVE)).isTrue();
    }

    @Test
    void paymentIssue_to_cancelled_isLegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAYMENT_ISSUE, SubscriberStatus.CANCELLED)).isTrue();
    }

    // ── Illegal transitions from PENDING_ACTIVATION ───────────────────────────

    @Test
    void pendingActivation_to_pendingActivation_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.PENDING_ACTIVATION, SubscriberStatus.PENDING_ACTIVATION)).isFalse();
    }

    @Test
    void pendingActivation_to_paused_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.PENDING_ACTIVATION, SubscriberStatus.PAUSED)).isFalse();
    }

    @Test
    void pendingActivation_to_paymentIssue_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.PENDING_ACTIVATION, SubscriberStatus.PAYMENT_ISSUE)).isFalse();
    }

    // ── Illegal transitions from ACTIVE ──────────────────────────────────────

    @Test
    void active_to_active_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.ACTIVE, SubscriberStatus.ACTIVE)).isFalse();
    }

    @Test
    void active_to_pendingActivation_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.ACTIVE, SubscriberStatus.PENDING_ACTIVATION)).isFalse();
    }

    // ── Illegal transitions from PAUSED ──────────────────────────────────────

    @Test
    void paused_to_paused_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAUSED, SubscriberStatus.PAUSED)).isFalse();
    }

    @Test
    void paused_to_pendingActivation_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAUSED, SubscriberStatus.PENDING_ACTIVATION)).isFalse();
    }

    @Test
    void paused_to_paymentIssue_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAUSED, SubscriberStatus.PAYMENT_ISSUE)).isFalse();
    }

    // ── Illegal transitions from PAYMENT_ISSUE ────────────────────────────────

    @Test
    void paymentIssue_to_paymentIssue_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAYMENT_ISSUE, SubscriberStatus.PAYMENT_ISSUE)).isFalse();
    }

    @Test
    void paymentIssue_to_pendingActivation_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAYMENT_ISSUE, SubscriberStatus.PENDING_ACTIVATION)).isFalse();
    }

    @Test
    void paymentIssue_to_paused_isIllegal() {
        assertThat(machine.canTransition(SubscriberStatus.PAYMENT_ISSUE, SubscriberStatus.PAUSED)).isFalse();
    }

    // ── Terminal state: CANCELLED — no outbound transitions ───────────────────

    @Test
    void cancelled_hasNoOutboundTransitions() {
        for (SubscriberStatus to : SubscriberStatus.values()) {
            assertThat(machine.canTransition(SubscriberStatus.CANCELLED, to))
                    .as("CANCELLED → %s should be illegal", to)
                    .isFalse();
        }
    }

    // ── Null guard ────────────────────────────────────────────────────────────

    @Test
    void nullFrom_returnsFalse() {
        assertThat(machine.canTransition(null, SubscriberStatus.ACTIVE)).isFalse();
    }

    @Test
    void nullTo_returnsFalse() {
        assertThat(machine.canTransition(SubscriberStatus.PENDING_ACTIVATION, null)).isFalse();
    }

    @Test
    void bothNull_returnsFalse() {
        assertThat(machine.canTransition(null, null)).isFalse();
    }
}
