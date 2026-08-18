package vn.ptit.snapvocab.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.domain.dto.collection.CollectionDTO;
import vn.ptit.snapvocab.domain.mapper.CollectionMapper;
import vn.ptit.snapvocab.repository.CollectionRepository;

@Service
@RequiredArgsConstructor
public class CollectionService {

	private final CollectionMapper collectionMapper;
	private final CollectionRepository collectionRepository;

	@Cacheable(
			cacheNames = "all-collections"
	)
	public List<CollectionDTO> getAllCollections() {
		return this.collectionRepository.findAll().stream()
				.map(collectionMapper::toDto)
				.collect(Collectors.toList());
	}
}
