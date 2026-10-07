package vn.ptit.snapvocab.mission.service;

import java.util.List;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;

/** Test-only bridge to the package-private §5.1 catalog. */
public final class MissionCatalogSeederAccess {

	private MissionCatalogSeederAccess() {
	}

	public static List<MissionTemplate> mvpCatalog() {
		return MissionCatalogSeeder.mvpCatalog();
	}
}
