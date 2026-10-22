package vn.ptit.snapvocab.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionEligibilityRule;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;

/**
 * TD-ADS-09: the Admin outlook (snapAdmin dailyOutlook) re-implements {@link MissionTracker#choose} in the browser.
 * Both sides check the same vectors (resources/mission/outlook-vectors.json, also read by snapAdmin's tests): every
 * learner, on every app, passing any mix of gates, can be handed exactly the sets {@link MissionTracker#outcomes} lists.
 */
class MissionOutlookVectorsTest {

	private static final List<MissionEligibilityRule> GATES = List.of(MissionEligibilityRule.HAS_IDLE_XP_BOOSTER,
			MissionEligibilityRule.HAS_PURCHASABLE_ITEM, MissionEligibilityRule.HAS_STUDY_CARDS_FOR_TARGET);

	@Test
	void backendChoiceMatchesTheAdminOutlookVectors() throws Exception {
		JsonNode vectors;
		try (InputStream in = getClass().getResourceAsStream("/mission/outlook-vectors.json")) {
			vectors = new ObjectMapper().readTree(in);
		}
		assertThat(vectors).isNotEmpty();
		for (JsonNode vector : vectors) {
			List<MissionTemplate> templates = new ArrayList<>();
			for (JsonNode t : vector.path("templates")) {
				templates.add(MissionTemplate.builder()
						.code(t.path("code").asText())
						.category(MissionCategory.valueOf(t.path("category").asText()))
						.triggerEvent(MissionTriggerEvent.valueOf(t.path("triggerEvent").asText()))
						.eligibilityRule(MissionEligibilityRule.valueOf(t.path("eligibilityRule").asText()))
						.rewardCoin(t.path("rewardCoin").asInt())
						.rewardXp(t.path("rewardXp").asInt())
						.rewardItemCode(t.path("rewardItemCode").isNull() ? null : t.path("rewardItemCode").asText())
						.rewardItemFallbackCoin(t.path("rewardItemFallbackCoin").isNull() ? null
								: t.path("rewardItemFallbackCoin").asInt())
						.active(t.path("isActive").asBoolean())
						.build());
			}
			Outlook outlook = new Outlook();
			for (JsonNode client : vector.path("clients")) {
				Set<MissionTriggerEvent> renderable = client.asText().equals("legacy") ? MissionTriggerEvent.LEGACY
						: EnumSet.allOf(MissionTriggerEvent.class);
				for (int mask = 0; mask < 1 << GATES.size(); mask++) {
					int passed = mask;
					List<MissionTemplate> eligible = templates.stream()
							.filter(MissionTemplate::getActive)
							.filter(t -> renderable.contains(t.getTriggerEvent()))
							.filter(t -> t.getEligibilityRule() == MissionEligibilityRule.NONE
									|| (passed & 1 << GATES.indexOf(t.getEligibilityRule())) != 0)
							.toList();
					outlook.add(MissionTracker.outcomes(eligible));
				}
			}
			JsonNode expect = vector.path("expect");
			String name = vector.path("name").asText();
			assertThat(outlook.insufficient).as(name + " insufficient").isEqualTo(expect.path("insufficient").asBoolean());
			assertThat(outlook.range(outlook.coin)).as(name + " coin").isEqualTo(range(expect.path("coin")));
			assertThat(outlook.range(outlook.xp)).as(name + " xp").isEqualTo(range(expect.path("xp")));
			for (int slot = 0; slot < 5; slot++) {
				List<String> codes = new ArrayList<>();
				expect.path("reachable").path(slot).forEach(code -> codes.add(code.asText()));
				assertThat(outlook.reachable.get(slot)).as(name + " slot " + (slot + 1)).containsExactlyElementsOf(codes);
			}
		}
	}

	private static List<Integer> range(JsonNode node) {
		return node.isNull() ? null : List.of(node.path(0).asInt(), node.path(1).asInt());
	}

	/** Same aggregation as dailyOutlook: Coin max counts an item reward's fallback Coin (shop.md S17). */
	private static final class Outlook {
		final List<Integer> coin = new ArrayList<>();
		final List<Integer> xp = new ArrayList<>();
		final List<TreeSet<String>> reachable = List.of(new TreeSet<>(), new TreeSet<>(), new TreeSet<>(),
				new TreeSet<>(), new TreeSet<>());
		boolean insufficient;

		void add(List<List<MissionTemplate>> sets) {
			if (sets.isEmpty()) {
				insufficient = true;
			}
			for (List<MissionTemplate> set : sets) {
				int c = set.stream().mapToInt(MissionTemplate::getRewardCoin).sum();
				int fallback = set.stream().filter(t -> t.getRewardItemCode() != null)
						.mapToInt(MissionTemplate::getRewardItemFallbackCoin).sum();
				coin.add(c);
				coin.add(c + fallback);
				xp.add(set.stream().mapToInt(MissionTemplate::getRewardXp).sum());
				for (int slot = 0; slot < set.size(); slot++) {
					reachable.get(slot).add(set.get(slot).getCode());
				}
			}
		}

		List<Integer> range(List<Integer> values) {
			return values.isEmpty() ? null
					: List.of(values.stream().mapToInt(Integer::intValue).min().getAsInt(),
							values.stream().mapToInt(Integer::intValue).max().getAsInt());
		}
	}
}
