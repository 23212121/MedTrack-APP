package com.medtrack.booking.domain;

/** Numeric login status codes stored on {@code svc.login.status} (see {@code svc.status}). */
public final class LoginStatus {
  public static final int INACTIVE = 0;
  public static final int ACTIVE = 1;

  private LoginStatus() {}

  public static boolean isActive(Integer status) {
    return status != null && status == ACTIVE;
  }

  /** Accepts 0/1, "0"/"1", or legacy ACTIVE/INACTIVE labels. */
  public static int parse(Object raw) {
    if (raw == null) {
      return ACTIVE;
    }
    if (raw instanceof Number n) {
      return n.intValue() == INACTIVE ? INACTIVE : ACTIVE;
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty()) {
      return ACTIVE;
    }
    if ("0".equals(s)
        || "INACTIVE".equalsIgnoreCase(s)
        || "FALSE".equalsIgnoreCase(s)
        || "NO".equalsIgnoreCase(s)) {
      return INACTIVE;
    }
    return ACTIVE;
  }
}
