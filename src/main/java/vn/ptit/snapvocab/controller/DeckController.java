package vn.ptit.snapvocab.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.service.DeckService;

@RestController
@RequestMapping("/api/decks")
@RequiredArgsConstructor
public class DeckController {

	private final DeckService deckService;


}
