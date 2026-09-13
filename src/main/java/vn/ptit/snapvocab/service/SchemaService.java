package vn.ptit.snapvocab.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.Schema;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaDTO;
import vn.ptit.snapvocab.domain.mapper.SchemaMapper;
import vn.ptit.snapvocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.repository.SchemaRepository;
import vn.ptit.snapvocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.repository.TopicSchemaRepository;
import vn.ptit.snapvocab.util.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchemaService {

	private final SchemaRepository schemaRepository;
	private final TopicSchemaRepository topicSchemaRepository;
	private final SchemaAttributeGroupRepository schemaAttributeGroupRepository;
	private final TopicItemAttributeValueRepository topicItemAttributeValueRepository;
	private final TemplateFieldRepository templateFieldRepository;
	private final SchemaMapper schemaMapper;
	private final Logger LOG = LoggerFactory.getLogger(SchemaService.class);

	@Transactional
	public SchemaDTO createSchema(CreateSchemaDTO createSchemaDTO) {
		LOG.info("Creating new independent schema: {}", createSchemaDTO.getName());

		Schema schema = this.schemaMapper.toEntity(createSchemaDTO);
		Schema saved = this.schemaRepository.save(schema);

		return getSchemaById(saved.getId());
	}

	@Cacheable(
			cacheNames = "schemas",
			key = "#schemaId"
	)
	public SchemaDTO getSchemaById(Long schemaId) {
		LOG.info("Fetching schema by id: {}", schemaId);
		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		List<SchemaAttributeGroup> groups = this.schemaAttributeGroupRepository.findBySchemaIdWithAttributes(schemaId);
		return this.schemaMapper.toDto(schema, groups);
	}

	public List<SchemaDTO> getAllSchemas() {
		LOG.info("Fetching all independent schemas");
		List<Schema> schemas = this.schemaRepository.findAll();
		return schemas.stream()
				.map(schema -> {
					List<SchemaAttributeGroup> groups = this.schemaAttributeGroupRepository.findBySchemaIdWithAttributes(schema.getId());
					return this.schemaMapper.toDto(schema, groups);
				})
				.toList();
	}

	@Transactional
	@CacheEvict(
			cacheNames = "schemas",
			key = "#schemaId"
	)
	public SchemaDTO updateSchema(Long schemaId, CreateSchemaDTO createSchemaDTO) {
		LOG.info("Updating schema with id: {}", schemaId);
		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		// Additive Evolution protection: verify that no attribute currently in use is being deleted
		List<SchemaAttributeGroup> existingGroups = this.schemaAttributeGroupRepository.findBySchemaIdWithAttributes(schemaId);
		Set<String> newGroupAttrKeys = new HashSet<>();
		if (createSchemaDTO.getSchemaGroups() != null) {
			for (var g : createSchemaDTO.getSchemaGroups()) {
				if (g.getAttributes() != null) {
					for (var a : g.getAttributes()) {
						newGroupAttrKeys.add(g.getName().trim().toLowerCase() + "::" + a.getName().trim().toLowerCase());
					}
				}
			}
		}

		for (SchemaAttributeGroup eg : existingGroups) {
			if (eg.getAttributes() != null) {
				for (SchemaAttribute ea : eg.getAttributes()) {
					String key = eg.getName().trim().toLowerCase() + "::" + ea.getName().trim().toLowerCase();
					if (!newGroupAttrKeys.contains(key)) {
						long valueCount = this.topicItemAttributeValueRepository.countByAttributeId(ea.getId());
						long templateCount = this.templateFieldRepository.countBySchemaAttributeId(ea.getId());
						if (valueCount > 0 || templateCount > 0) {
							throw new IllegalStateException(
									"Cannot delete attribute '" + ea.getName() + "' because it is currently used by "
											+ valueCount + " vocabulary item(s) and " + templateCount
											+ " template field(s). Please fork the schema for your topic to customize it independently."
							);
						}
					}
				}
			}
		}

		this.schemaMapper.updateEntityFromDto(createSchemaDTO, schema);
		Schema updated = this.schemaRepository.save(schema);

		return getSchemaById(updated.getId());
	}

	@Transactional
	@CacheEvict(
			cacheNames = "schemas",
			key = "#schemaId"
	)
	public void deleteSchema(Long schemaId) {
		LOG.info("Deleting schema with id: {}", schemaId);
		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		long assignedCount = this.topicSchemaRepository.countBySchemaId(schemaId);
		if (assignedCount > 0) {
			throw new IllegalStateException(
					"Cannot delete schema because it is currently assigned to " + assignedCount + " topic(s). Fork or reassign those topics first.");
		}

		this.schemaRepository.delete(schema);
	}
}
