package vn.ptit.snapvocab.vocab.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.SchemaDTO;
import vn.ptit.snapvocab.vocab.domain.mapper.SchemaMapper;
import vn.ptit.snapvocab.vocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchemaService {

	private final SchemaRepository schemaRepository;
	private final TopicRepository topicRepository;
	private final SchemaAttributeGroupRepository schemaAttributeGroupRepository;

	private final TopicItemAttributeValueRepository topicItemAttributeValueRepository;
	private final TemplateFieldRepository templateFieldRepository;
	private final UserRepository userRepository;
	private final SchemaMapper schemaMapper;
	private final Logger LOG = LoggerFactory.getLogger(SchemaService.class);

	@Transactional
	public SchemaDTO createSchema(CreateSchemaDTO createSchemaDTO, Long userId) {
		LOG.info("Creating new independent schema: {} for userId: {}", createSchemaDTO.getName(), userId);

		Schema schema = this.schemaMapper.toEntity(createSchemaDTO);
		if (userId != null) {
			User user = this.userRepository.findById(userId).orElse(null);
			schema.setUser(user);
		}
		schema.setIsSystem(false);
		Schema saved = this.schemaRepository.save(schema);

		return getSchemaById(saved.getId(), userId);
	}

	@Transactional
	public SchemaDTO createSchema(CreateSchemaDTO createSchemaDTO) {
		return createSchema(createSchemaDTO, null);
	}

	public SchemaDTO getSchemaById(Long schemaId, Long userId) {
		LOG.info("Fetching schema by id: {} for userId: {}", schemaId, userId);
		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		validateSchemaAccess(schema, userId);

		List<SchemaAttributeGroup> groups = this.schemaAttributeGroupRepository.findBySchemaIdWithAttributes(schemaId);
		return this.schemaMapper.toDto(schema, groups);
	}

	public SchemaDTO getSchemaById(Long schemaId) {
		return getSchemaById(schemaId, null);
	}

	public List<SchemaDTO> getAllSchemas(Long userId) {
		LOG.info("Fetching accessible schemas for userId: {}", userId);
		boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		List<Schema> schemas;
		if (isAdmin) {
			schemas = this.schemaRepository.findAll();
		} else if (userId != null) {
			schemas = this.schemaRepository.findAccessibleSchemas(userId);
		} else {
			schemas = this.schemaRepository.findByIsSystemTrue();
		}
		return schemas.stream()
				.map(schema -> {
					List<SchemaAttributeGroup> groups = this.schemaAttributeGroupRepository
							.findBySchemaIdWithAttributes(schema.getId());
					return this.schemaMapper.toDto(schema, groups);
				})
				.toList();
	}

	public List<SchemaDTO> getAllSchemas() {
		return getAllSchemas(null);
	}

	@Transactional
	public SchemaDTO updateSchema(Long schemaId, CreateSchemaDTO createSchemaDTO, Long userId) {
		LOG.info("Updating schema with id: {} by userId: {}", schemaId, userId);
		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		validateSchemaModification(schema, userId);

		// Additive Evolution protection: verify that no attribute currently in use is
		// being deleted
		List<SchemaAttributeGroup> existingGroups = this.schemaAttributeGroupRepository
				.findBySchemaIdWithAttributes(schemaId);
		Set<String> newGroupAttrKeys = new HashSet<>();
		if (createSchemaDTO.getSchemaGroups() != null) {
			for (var g : createSchemaDTO.getSchemaGroups()) {
				if (g.getAttributes() != null) {
					for (var a : g.getAttributes()) {
						newGroupAttrKeys
								.add(g.getName().trim().toLowerCase() + "::" + a.getName().trim().toLowerCase());
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
											+ " template field(s). Please fork the schema for your topic to customize it independently.");
						}
					}
				}
			}
		}

		this.schemaMapper.updateEntityFromDto(createSchemaDTO, schema);
		Schema updated = this.schemaRepository.save(schema);

		return getSchemaById(updated.getId(), userId);
	}

	@Transactional
	public SchemaDTO updateSchema(Long schemaId, CreateSchemaDTO createSchemaDTO) {
		return updateSchema(schemaId, createSchemaDTO, null);
	}

	@Transactional
	public void deleteSchema(Long schemaId, Long userId) {
		LOG.info("Deleting schema with id: {} by userId: {}", schemaId, userId);
		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		validateSchemaModification(schema, userId);

		long assignedCount = this.topicRepository.countBySchemaId(schemaId);

		if (assignedCount > 0) {
			throw new IllegalStateException(
					"Cannot delete schema because it is currently assigned to " + assignedCount
							+ " topic(s). Fork or reassign those topics first.");
		}

		this.schemaRepository.delete(schema);
	}

	@Transactional
	public void deleteSchema(Long schemaId) {
		deleteSchema(schemaId, null);
	}

	private void validateSchemaAccess(Schema schema, Long userId) {
		if (schema.isSystem() || schema.getUser() == null) {
			return; // System schemas or public templates accessible to all
		}
		boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		if (isAdmin) {
			return;
		}
		if (userId == null || !schema.getUser().getId().equals(userId)) {
			throw new AccessDeniedException("You do not have permission to access this schema");
		}
	}

	private void validateSchemaModification(Schema schema, Long userId) {
		boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		if (schema.isSystem() || schema.getUser() == null) {
			if (!isAdmin) {
				throw new AccessDeniedException("Only administrators can modify or delete system schemas");
			}
			return;
		}
		if (!isAdmin && (userId == null || !schema.getUser().getId().equals(userId))) {
			throw new AccessDeniedException("You do not have permission to modify or delete this schema");
		}
	}
}

