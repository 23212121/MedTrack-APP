package com.medtrack.booking.service;

/** Authenticated doctor identity resolved from the login token / assignment tables. */
public record DoctorSession(String doctorId, Long hospitalId, String role) {}
