package br.com.holding.payments.plan;

import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.plan.dto.PlanLimitDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Serializa/deserializa a coluna JSONB de limites e features.
 *
 * Formato canonico (novo):
 *   [{ "key": "funcionarios", "label": "Funcionarios", "type": "NUMBER", "value": 3 },
 *    { "key": "relatorios",   "label": "Relatorios",   "type": "BOOLEAN", "enabled": false },
 *    { "key": "armazenamento","label": "Armazenamento","type": "UNLIMITED" }]
 *
 * Formatos antigos tolerados na leitura:
 *   - [{"text":"Texto", "included": true}]     -> BOOLEAN, key derivada do texto
 *   - {"users": 10, "projects": 5}             -> NUMBER
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanLimitCodec {

    private final ObjectMapper objectMapper;

    public String serialize(List<PlanLimitDto> limits) {
        if (limits == null || limits.isEmpty()) {
            return null;
        }
        validate(limits);
        try {
            return objectMapper.writeValueAsString(limits);
        } catch (Exception e) {
            throw new BusinessException("Falha ao serializar limites/features: " + e.getMessage());
        }
    }

    public List<PlanLimitDto> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                return parseArray(root);
            }
            if (root.isObject()) {
                return parseLegacyMap(root);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Falha ao deserializar limites, retornando vazio: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<PlanLimitDto> parseArray(JsonNode array) {
        List<PlanLimitDto> result = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            if (node.has("type") && node.has("key")) {
                // Formato novo
                PlanLimitDto dto = objectMapper.convertValue(node, PlanLimitDto.class);
                result.add(dto);
            } else if (node.has("text") || node.has("included")) {
                // Legado: [{text, included}]
                String label = node.path("text").asText("");
                boolean included = node.path("included").asBoolean(false);
                result.add(new PlanLimitDto(slugify(label), label, PlanLimitType.BOOLEAN, null, included));
            }
        }
        return result;
    }

    private List<PlanLimitDto> parseLegacyMap(JsonNode object) {
        List<PlanLimitDto> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = object.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String key = entry.getKey();
            JsonNode val = entry.getValue();
            if (val.isNumber()) {
                result.add(new PlanLimitDto(key, key, PlanLimitType.NUMBER, val.asLong(), null));
            } else if (val.isBoolean()) {
                result.add(new PlanLimitDto(key, key, PlanLimitType.BOOLEAN, null, val.asBoolean()));
            }
        }
        return result;
    }

    private void validate(List<PlanLimitDto> limits) {
        Set<String> seen = new HashSet<>();
        for (PlanLimitDto dto : limits) {
            if (!seen.add(dto.key())) {
                throw new BusinessException("Key duplicada em limites/features: '" + dto.key() + "'.");
            }
            switch (dto.type()) {
                case NUMBER -> {
                    if (dto.value() == null) {
                        throw new BusinessException("Limite '" + dto.key() + "' do tipo NUMBER requer campo 'value'.");
                    }
                }
                case BOOLEAN -> {
                    if (dto.enabled() == null) {
                        throw new BusinessException("Limite '" + dto.key() + "' do tipo BOOLEAN requer campo 'enabled'.");
                    }
                }
                case UNLIMITED -> { /* sem campos obrigatorios */ }
            }
        }
    }

    /**
     * Mapa legado {key -> Integer} para manter compatibilidade com PlanLimitsValidator existente.
     * Converte somente limites do tipo NUMBER.
     */
    public Map<String, Integer> toNumberMap(String json) {
        List<PlanLimitDto> parsed = deserialize(json);
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        for (PlanLimitDto dto : parsed) {
            if (dto.type() == PlanLimitType.NUMBER && dto.value() != null) {
                result.put(dto.key(), dto.value().intValue());
            }
        }
        // Fallback: se nao achou nenhum e o JSON parece um mapa legado simples
        if (result.isEmpty() && json != null && !json.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(json);
                if (root.isObject()) {
                    Map<String, Integer> legacy = objectMapper.convertValue(
                            root, new TypeReference<Map<String, Integer>>() {});
                    if (legacy != null) {
                        return legacy;
                    }
                }
            } catch (Exception ignored) {
                // mantem vazio
            }
        }
        return result;
    }

    private static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "item";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isEmpty() || !Character.isLetter(slug.charAt(0))) {
            slug = "item_" + slug;
        }
        return slug;
    }
}
