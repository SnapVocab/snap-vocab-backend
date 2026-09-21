package vn.ptit.snapvocab.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScanObjectKeysTest {

	@Test
	void generatedKeyBelongsToItsUserOnly() {
		String key = ScanObjectKeys.newKey(7L, "image/JPEG");

		assertThat(key).startsWith("scans/7/").endsWith(".jpg");
		assertThat(ScanObjectKeys.belongsTo(key, 7L)).isTrue();
		assertThat(ScanObjectKeys.belongsTo(key, 70L)).isFalse();
		assertThat(ScanObjectKeys.belongsTo(key, 8L)).isFalse();
	}

	@Test
	void pathTricksAndForeignKeysAreRejected() {
		assertThat(ScanObjectKeys.belongsTo("scans/7/../8/x.jpg", 7L)).isFalse();
		assertThat(ScanObjectKeys.belongsTo("scans/7/sub/x.jpg", 7L)).isFalse();
		assertThat(ScanObjectKeys.belongsTo("scans/7/x.gif", 7L)).isFalse();
		assertThat(ScanObjectKeys.belongsTo("avatars/7/x.jpg", 7L)).isFalse();
		assertThat(ScanObjectKeys.belongsTo(null, 7L)).isFalse();
	}

	@Test
	void contentTypeComesFromTheExtension() {
		assertThat(ScanObjectKeys.contentTypeOf("scans/7/a.webp")).contains("image/webp");
		assertThat(ScanObjectKeys.contentTypeOf("scans/7/a")).isEmpty();
		assertThat(ScanObjectKeys.isSupportedType("image/gif")).isFalse();
	}
}
