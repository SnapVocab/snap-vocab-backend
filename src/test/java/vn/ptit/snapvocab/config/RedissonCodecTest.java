package vn.ptit.snapvocab.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.buffer.ByteBuf;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.redisson.codec.JsonJacksonCodec;
import vn.ptit.snapvocab.domain.dto.topic.TopicDTO;

class RedissonCodecTest {

	@Test
	void shouldSerializeAndDeserializeTopicDTOWithInstant() throws Exception {
		JsonJacksonCodec codec = new JsonJacksonCodec();
		codec.getObjectMapper().registerModule(new JavaTimeModule());
		codec.getObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		TopicDTO dto = TopicDTO.builder()
				.id(202L)
				.name("Business & Office")
				.hasChildren(true)
				.createdAt(Instant.now())
				.updatedAt(Instant.now())
				.build();

		ByteBuf buf = codec.getValueEncoder().encode(dto);
		TopicDTO deserialized = (TopicDTO) codec.getValueDecoder().decode(buf, null);

		assertThat(deserialized).isNotNull();
		assertThat(deserialized.getId()).isEqualTo(202L);
		assertThat(deserialized.getCreatedAt()).isNotNull();
		assertThat(deserialized.getHasChildren()).isTrue();
	}
}
