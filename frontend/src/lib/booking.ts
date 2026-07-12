/**
 * Shared contract for `POST /api/bookings/walkthrough` — the public walk-through
 * booking endpoint. No auth required; rate limited 3/IP/hour.
 *
 * Field shape mirrors backend/api-contract.md and
 * backend/src/main/java/com/homekept/booking/dto/WalkthroughBookingRequest.java
 * exactly. Both the customer booking wizard (`routes/book.tsx`) and the admin
 * "New booking" sheet (`routes/admin.index.tsx`, for phone/in-person bookings)
 * submit through this one type + function so the two callers can't drift out
 * of sync with the backend DTO.
 */

import { post } from "@/lib/api";

export interface WalkthroughBookingRequest {
  fullName: string;
  email: string;
  phone: string;
  streetAddress: string;
  city: string;
  postalCode: string;
  /** Optional. */
  yearBuilt?: number;
  /** Optional. */
  squareFootageRange?: "<1500" | "1500-2500" | "2500-4000" | ">4000";
  propertyType: "DETACHED" | "SEMI" | "TOWNHOUSE";
  /** ISO date, Monday of the chosen week, e.g. "2026-06-15". */
  preferredWeek: string;
  timeOfDay: "MORNING" | "AFTERNOON" | "EVENING";
  /** Optional. Max 7 entries (one per day of week). */
  dayPreferences?: ("MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN")[];
  /** Optional. */
  notes?: string;
  /** Optional — defaults to WEBSITE_DIRECT server-side if omitted. */
  leadSource?:
    | "NEXTDOOR"
    | "FACEBOOK_GROUP"
    | "REFERRAL"
    | "DOOR_KNOCK"
    | "WEBSITE_ORGANIC"
    | "WEBSITE_DIRECT"
    | "OTHER";
  /** CASL: must be true or the request is rejected with 400. */
  contactConsent: true;
  /** Optional anonymous PostHog id — omitted for staff-entered bookings. */
  posthogDistinctId?: string;
}

export interface WalkthroughBookingResponse {
  id: number;
  status: "PENDING";
}

/** `POST /api/bookings/walkthrough` — public, no auth, rate limited 3/IP/hour. */
export function submitWalkthroughBooking(
  payload: WalkthroughBookingRequest,
): Promise<WalkthroughBookingResponse> {
  return post<WalkthroughBookingResponse>("/api/bookings/walkthrough", payload);
}
