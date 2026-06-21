package com.homekept.booking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WalkthroughBookingStateMachine}.
 * No Spring context — pure logic. Covers every legal and illegal transition
 * per arch doc §4.3.
 *
 * Legal transitions:
 *   PENDING → CONFIRMED
 *   PENDING → LOST
 *   CONFIRMED → PERFORMED
 *   CONFIRMED → NO_SHOW
 *   PERFORMED → CONVERTED
 *   PERFORMED → LOST
 *
 * Terminal (no outbound): CONVERTED, LOST, NO_SHOW
 */
class WalkthroughBookingStateMachineTest {

    private final WalkthroughBookingStateMachine machine = new WalkthroughBookingStateMachine();

    // ── Legal transitions ─────────────────────────────────────────────────────

    @Test
    void pending_to_confirmed_isLegal() {
        assertThat(machine.canTransition(BookingStatus.PENDING, BookingStatus.CONFIRMED)).isTrue();
    }

    @Test
    void pending_to_lost_isLegal() {
        assertThat(machine.canTransition(BookingStatus.PENDING, BookingStatus.LOST)).isTrue();
    }

    @Test
    void confirmed_to_performed_isLegal() {
        assertThat(machine.canTransition(BookingStatus.CONFIRMED, BookingStatus.PERFORMED)).isTrue();
    }

    @Test
    void confirmed_to_noShow_isLegal() {
        assertThat(machine.canTransition(BookingStatus.CONFIRMED, BookingStatus.NO_SHOW)).isTrue();
    }

    @Test
    void performed_to_converted_isLegal() {
        assertThat(machine.canTransition(BookingStatus.PERFORMED, BookingStatus.CONVERTED)).isTrue();
    }

    @Test
    void performed_to_lost_isLegal() {
        assertThat(machine.canTransition(BookingStatus.PERFORMED, BookingStatus.LOST)).isTrue();
    }

    // ── Illegal transitions from PENDING ─────────────────────────────────────

    @Test
    void pending_to_performed_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.PENDING, BookingStatus.PERFORMED)).isFalse();
    }

    @Test
    void pending_to_converted_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.PENDING, BookingStatus.CONVERTED)).isFalse();
    }

    @Test
    void pending_to_noShow_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.PENDING, BookingStatus.NO_SHOW)).isFalse();
    }

    @Test
    void pending_to_pending_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.PENDING, BookingStatus.PENDING)).isFalse();
    }

    // ── Illegal transitions from CONFIRMED ───────────────────────────────────

    @Test
    void confirmed_to_pending_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.CONFIRMED, BookingStatus.PENDING)).isFalse();
    }

    @Test
    void confirmed_to_lost_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.CONFIRMED, BookingStatus.LOST)).isFalse();
    }

    @Test
    void confirmed_to_converted_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.CONFIRMED, BookingStatus.CONVERTED)).isFalse();
    }

    @Test
    void confirmed_to_confirmed_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.CONFIRMED, BookingStatus.CONFIRMED)).isFalse();
    }

    // ── Illegal transitions from PERFORMED ───────────────────────────────────

    @Test
    void performed_to_pending_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.PERFORMED, BookingStatus.PENDING)).isFalse();
    }

    @Test
    void performed_to_confirmed_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.PERFORMED, BookingStatus.CONFIRMED)).isFalse();
    }

    @Test
    void performed_to_noShow_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.PERFORMED, BookingStatus.NO_SHOW)).isFalse();
    }

    @Test
    void performed_to_performed_isIllegal() {
        assertThat(machine.canTransition(BookingStatus.PERFORMED, BookingStatus.PERFORMED)).isFalse();
    }

    // ── Terminal states: no outbound transitions ──────────────────────────────

    @Test
    void converted_hasNoOutboundTransitions() {
        for (BookingStatus to : BookingStatus.values()) {
            assertThat(machine.canTransition(BookingStatus.CONVERTED, to))
                    .as("CONVERTED → " + to + " should be illegal")
                    .isFalse();
        }
    }

    @Test
    void lost_hasNoOutboundTransitions() {
        for (BookingStatus to : BookingStatus.values()) {
            assertThat(machine.canTransition(BookingStatus.LOST, to))
                    .as("LOST → " + to + " should be illegal")
                    .isFalse();
        }
    }

    @Test
    void noShow_hasNoOutboundTransitions() {
        for (BookingStatus to : BookingStatus.values()) {
            assertThat(machine.canTransition(BookingStatus.NO_SHOW, to))
                    .as("NO_SHOW → " + to + " should be illegal")
                    .isFalse();
        }
    }

    // ── Null guard ────────────────────────────────────────────────────────────

    @Test
    void nullFrom_returnsFalse() {
        assertThat(machine.canTransition(null, BookingStatus.CONFIRMED)).isFalse();
    }

    @Test
    void nullTo_returnsFalse() {
        assertThat(machine.canTransition(BookingStatus.PENDING, null)).isFalse();
    }

    @Test
    void bothNull_returnsFalse() {
        assertThat(machine.canTransition(null, null)).isFalse();
    }
}
