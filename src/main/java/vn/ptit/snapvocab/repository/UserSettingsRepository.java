package vn.ptit.snapvocab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.UserSettings;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}
