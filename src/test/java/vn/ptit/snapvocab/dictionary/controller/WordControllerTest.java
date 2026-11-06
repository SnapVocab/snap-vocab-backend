package vn.ptit.snapvocab.dictionary.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.ptit.snapvocab.dictionary.domain.dto.BatchLookupRequest;
import vn.ptit.snapvocab.dictionary.domain.dto.LookupResultDTO;
import vn.ptit.snapvocab.dictionary.service.WordService;

@ExtendWith(MockitoExtension.class)
class WordControllerTest {

	@Mock
	private WordService wordService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new WordController(wordService)).build();
	}

	@Test
	void addAndRemovePersonalWordReturnNoContent() throws Exception {
		mockMvc.perform(put("/api/words/my/10")).andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/words/my/10")).andExpect(status().isNoContent());
		verify(wordService).addMyWord(10L);
		verify(wordService).removeMyWord(10L);
	}

	@Test
	void detailSerializesPersonalVocabularyStatusAndWordId() throws Exception {
		when(wordService.getWordById(10L)).thenReturn(LookupResultDTO.builder()
				.wordId(10L).word("dog").exists(true).inMyVocabulary(true).build());
		mockMvc.perform(get("/api/words/10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wordId").value(10))
				.andExpect(jsonPath("$.inMyVocabulary").value(true));
	}

	@Test
	void independentBatchDictionaryEndpointIsUnchanged() throws Exception {
		when(wordService.getWordsByEnglish(any())).thenReturn(List.of());

		mockMvc.perform(post("/api/words/lookup")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"words\":[\"dog\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());

		verify(wordService).getWordsByEnglish(any(BatchLookupRequest.class));
	}
}

