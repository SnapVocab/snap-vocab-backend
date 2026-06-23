package vn.ptit.snapvocab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.Authority;

@Repository
public interface AuthorityRepository extends JpaRepository<Authority, String> {

}
