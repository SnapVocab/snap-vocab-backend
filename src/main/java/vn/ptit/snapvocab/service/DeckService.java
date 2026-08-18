package vn.ptit.snapvocab.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.repository.DeckRepository;

@Service
@RequiredArgsConstructor
public class DeckService {

	private final DeckRepository deckRepository;
}
