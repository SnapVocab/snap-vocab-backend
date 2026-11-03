package vn.ptit.snapvocab.audit.repository;

/** {@code actorId} null = the system. */
public record ActorCount(Long actorId, String email, Long count) {
}
