package vn.ptit.snapvocab.audit.repository;

import java.time.LocalDate;

public record DayCount(LocalDate day, Long count) {
}
