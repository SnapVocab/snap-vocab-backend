package vn.ptit.snapvocab.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.ptit.snapvocab.domain.dto.collection.CollectionDTO;
import vn.ptit.snapvocab.domain.dto.collection.CreateCollectionDTO;
import vn.ptit.snapvocab.domain.dto.collection.UpdateCollectionDTO;
import vn.ptit.snapvocab.domain.dto.common.Meta;
import vn.ptit.snapvocab.domain.dto.common.PaginationObject;
import vn.ptit.snapvocab.domain.dto.topic.CreateTopicDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.service.CollectionService;
import vn.ptit.snapvocab.service.TopicService;
import vn.ptit.snapvocab.service.UserService;
import vn.ptit.snapvocab.util.exception.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class CollectionControllerTest {

	private MockMvc mockMvc;
	private ObjectMapper objectMapper;

	@Mock
	private CollectionService collectionService;

	@Mock
	private TopicService topicService;

	@Mock
	private UserService userService;

	@InjectMocks
	private CollectionController collectionController;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		mockMvc = MockMvcBuilders.standaloneSetup(collectionController)
				.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void getCollections_authenticated_returnsPublicAndUserCollections() throws Exception {
		when(userService.getCurrentUserId()).thenReturn(Optional.of(1L));
		when(collectionService.getAllCollections(1L)).thenReturn(List.of(
				CollectionDTO.builder().id(10L).name("System Col").type(CollectionType.SYSTEM).build(),
				CollectionDTO.builder().id(20L).name("User Col").type(CollectionType.USER).ownerId(1L).build()
		));

		mockMvc.perform(get("/api/collections"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("System Col"))
				.andExpect(jsonPath("$[1].name").value("User Col"));

		verify(collectionService).getAllCollections(1L);
	}

	@Test
	void getCollections_unauthenticated_returnsPublicCollectionsOnly() throws Exception {
		when(userService.getCurrentUserId()).thenReturn(Optional.empty());
		when(collectionService.getAllCollections(null)).thenReturn(List.of(
				CollectionDTO.builder().id(10L).name("System Col").type(CollectionType.SYSTEM).build()
		));

		mockMvc.perform(get("/api/collections"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("System Col"));

		verify(collectionService).getAllCollections(null);
	}

	@Test
	void getSystemCollections_returnsSystemCollections() throws Exception {
		when(collectionService.getSystemCollections()).thenReturn(List.of(
				CollectionDTO.builder().id(10L).name("System Col").type(CollectionType.SYSTEM).build()
		));

		mockMvc.perform(get("/api/collections/system"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("SYSTEM"));

		verify(collectionService).getSystemCollections();
	}

	@Test
	void getPublicCollections_returnsSystemCollections() throws Exception {
		when(collectionService.getSystemCollections()).thenReturn(List.of(
				CollectionDTO.builder().id(10L).name("System Col").type(CollectionType.SYSTEM).build()
		));

		mockMvc.perform(get("/api/collections/public"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("SYSTEM"));

		verify(collectionService).getSystemCollections();
	}

	@Test
	void getMyCollections_authenticated_returnsUserCollections() throws Exception {
		when(userService.getCurrentUserId()).thenReturn(Optional.of(1L));
		when(collectionService.getMyCollections(1L)).thenReturn(List.of(
				CollectionDTO.builder().id(20L).name("User Col").type(CollectionType.USER).ownerId(1L).build()
		));

		mockMvc.perform(get("/api/collections/my"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("User Col"));

		verify(collectionService).getMyCollections(1L);
	}

	@Test
	void getCollectionById_returnsCollection() throws Exception {
		when(userService.getCurrentUserId()).thenReturn(Optional.of(1L));
		when(collectionService.getCollectionById(20L, 1L)).thenReturn(
				CollectionDTO.builder().id(20L).name("User Col").type(CollectionType.USER).ownerId(1L).build()
		);

		mockMvc.perform(get("/api/collections/20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(20))
				.andExpect(jsonPath("$.name").value("User Col"));

		verify(collectionService).getCollectionById(20L, 1L);
	}

	@Test
	void createCollection_createsSuccessfully() throws Exception {
		CreateCollectionDTO createDto = CreateCollectionDTO.builder()
				.name("New Collection")
				.translation("Bộ sưu tập mới")
				.build();

		when(userService.getCurrentUserId()).thenReturn(Optional.of(1L));
		when(collectionService.createCollection(any(CreateCollectionDTO.class), eq(1L))).thenReturn(
				CollectionDTO.builder().id(30L).name("New Collection").type(CollectionType.USER).ownerId(1L).build()
		);

		mockMvc.perform(post("/api/collections")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(createDto)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(30))
				.andExpect(jsonPath("$.name").value("New Collection"));

		verify(collectionService).createCollection(any(CreateCollectionDTO.class), eq(1L));
	}

	@Test
	void updateCollection_updatesSuccessfully() throws Exception {
		UpdateCollectionDTO updateDto = UpdateCollectionDTO.builder()
				.name("Updated Collection")
				.translation("Bản dịch mới")
				.build();

		when(userService.getCurrentUserId()).thenReturn(Optional.of(1L));
		when(collectionService.updateCollection(eq(20L), any(UpdateCollectionDTO.class), eq(1L))).thenReturn(
				CollectionDTO.builder().id(20L).name("Updated Collection").type(CollectionType.USER).ownerId(1L).build()
		);

		mockMvc.perform(put("/api/collections/20")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updateDto)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(20))
				.andExpect(jsonPath("$.name").value("Updated Collection"));

		verify(collectionService).updateCollection(eq(20L), any(UpdateCollectionDTO.class), eq(1L));
	}

	@Test
	void deleteCollection_deletesSuccessfully() throws Exception {
		when(userService.getCurrentUserId()).thenReturn(Optional.of(1L));

		mockMvc.perform(delete("/api/collections/20"))
				.andExpect(status().isNoContent());

		verify(collectionService).deleteCollection(20L, 1L);
	}

	@Test
	void getTopicsFromCollection_returnsTopics() throws Exception {
		PaginationObject<List<TopicDTO>> paginationObject = PaginationObject.<List<TopicDTO>>builder()
				.data(List.of(TopicDTO.builder().id(100L).name("Topic 1").build()))
				.meta(Meta.builder().page(0L).pages(1L).size(10L).total(1L).build())
				.build();

		when(topicService.getTopicsByCollection(eq(10L), isNull(), any(Pageable.class)))
				.thenReturn(paginationObject);

		mockMvc.perform(get("/api/collections/10/topics"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].name").value("Topic 1"))
				.andExpect(jsonPath("$.meta.total").value(1));

		verify(topicService).getTopicsByCollection(eq(10L), isNull(), any(Pageable.class));
	}

	@Test
	void createCollectionTopic_createsTopic() throws Exception {
		CreateTopicDTO topicDto = CreateTopicDTO.builder()
				.name("New Topic")
				.build();

		when(topicService.createCollectionTopic(eq(10L), any(CreateTopicDTO.class))).thenReturn(
				TopicDTO.builder().id(101L).name("New Topic").build()
		);

		mockMvc.perform(post("/api/collections/10/topics")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(topicDto)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(101))
				.andExpect(jsonPath("$.name").value("New Topic"));

		verify(topicService).createCollectionTopic(eq(10L), any(CreateTopicDTO.class));
	}
}
