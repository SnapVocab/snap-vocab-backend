package vn.ptit.snapvocab.dictionary.domain.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LinksParser {

    private final ObjectMapper objectMapper;

    public LinksParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> parseLinks(String links) {
        if (links == null || links.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(links, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}

