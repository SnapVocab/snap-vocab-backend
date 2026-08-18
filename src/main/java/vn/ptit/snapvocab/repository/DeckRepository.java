package vn.ptit.snapvocab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.Deck;

@Repository
public interface DeckRepository extends JpaRepository<Deck, Long> {

}
