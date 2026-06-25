package vn.ptit.snapvocab.domain.enumeration;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class CodeLabelMapper {

    private static final Map<Class<?>, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private CodeLabelMapper() {}

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T> & Labeled> String fromCode(String code, Class<T> enumClass) {
        if (code == null) return null;
        return CACHE.computeIfAbsent(enumClass, k -> {
            Class<T> ec = (Class<T>) k;
            return Arrays.stream(ec.getEnumConstants())
                    .collect(Collectors.toUnmodifiableMap(Labeled::getCode, Labeled::getLabel));
        }).getOrDefault(code, code);
    }
}
