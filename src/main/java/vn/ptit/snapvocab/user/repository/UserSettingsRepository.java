package vn.ptit.snapvocab.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.user.domain.UserSettings;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}

