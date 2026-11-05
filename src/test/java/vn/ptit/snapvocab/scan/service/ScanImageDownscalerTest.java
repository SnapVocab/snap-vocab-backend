package vn.ptit.snapvocab.scan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanImage;

class ScanImageDownscalerTest {

	@Test
	void shrinksLargePhotoToLongEdgeAsJpeg() throws IOException {
		ScanImage photo = new ScanImage(encode(noise(1728, 2304), "jpg"), "shot.jpg", "image/jpeg");

		ScanImage sent = ScanImageDownscaler.fit(photo, 1400);

		BufferedImage decoded = decode(sent.bytes());
		assertThat(decoded.getWidth()).isEqualTo(1050);
		assertThat(decoded.getHeight()).isEqualTo(1400);
		assertThat(sent.contentType()).isEqualTo("image/jpeg");
		assertThat(sent.filename()).isEqualTo("shot.jpg");
		assertThat(sent.bytes().length).isLessThan(photo.bytes().length);
	}

	@Test
	void convertsLargePngToJpeg() throws IOException {
		ScanImage photo = new ScanImage(encode(noise(2000, 1000), "png"), "pick.png", "image/png");

		ScanImage sent = ScanImageDownscaler.fit(photo, 1400);

		assertThat(sent.contentType()).isEqualTo("image/jpeg");
		assertThat(sent.filename()).isEqualTo("pick.jpg");
		assertThat(decode(sent.bytes()).getWidth()).isEqualTo(1400);
	}

	@Test
	void leavesSmallPhotoUntouched() throws IOException {
		ScanImage photo = new ScanImage(encode(noise(800, 600), "jpg"), "small.jpg", "image/jpeg");

		assertThat(ScanImageDownscaler.fit(photo, 1400)).isSameAs(photo);
	}

	@Test
	void leavesRotatedExifPhotoUntouched() throws IOException {
		byte[] jpeg = withExifOrientation(encode(noise(2304, 1728), "jpg"), 6);
		ScanImage photo = new ScanImage(jpeg, "rotated.jpg", "image/jpeg");

		assertThat(ScanImageDownscaler.hasRotatingExif(jpeg)).isTrue();
		assertThat(ScanImageDownscaler.fit(photo, 1400)).isSameAs(photo);
	}

	@Test
	void shrinksPhotoWhoseExifSaysUpright() throws IOException {
		byte[] jpeg = withExifOrientation(encode(noise(2304, 1728), "jpg"), 1);

		assertThat(ScanImageDownscaler.hasRotatingExif(jpeg)).isFalse();
		assertThat(ScanImageDownscaler.fit(new ScanImage(jpeg, "a.jpg", "image/jpeg"), 1400).bytes())
				.isNotSameAs(jpeg);
	}

	@Test
	void leavesUndecodableOrDisabledInputUntouched() throws IOException {
		ScanImage webp = new ScanImage(new byte[] { 1, 2, 3 }, "a.webp", "image/webp");
		ScanImage garbage = new ScanImage(new byte[] { 1, 2, 3 }, "a.jpg", "image/jpeg");
		ScanImage large = new ScanImage(encode(noise(2000, 1500), "jpg"), "a.jpg", "image/jpeg");

		assertThat(ScanImageDownscaler.fit(webp, 1400)).isSameAs(webp);
		assertThat(ScanImageDownscaler.fit(garbage, 1400)).isSameAs(garbage);
		assertThat(ScanImageDownscaler.fit(large, 0)).isSameAs(large);
	}

	/** Random pixels, so the JPEG is as large as a real photo rather than a flat colour. */
	private static BufferedImage noise(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Random random = new Random(42);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, random.nextInt(0xFFFFFF));
			}
		}
		return image;
	}

	private static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private static BufferedImage decode(byte[] bytes) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}

	/** Inserts a big-endian APP1 EXIF segment holding only IFD0 tag 0x0112 right after SOI. */
	private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
		byte[] tiff = {
				'M', 'M', 0, 42, 0, 0, 0, 8, // header, IFD0 at offset 8
				0, 1, // one entry
				0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, // orientation, SHORT, count 1
				0, 0, 0, 0 // no next IFD
		};
		int length = 2 + 6 + tiff.length;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);
		out.write(0xFF);
		out.write(0xE1);
		out.write(length >> 8);
		out.write(length & 0xFF);
		out.writeBytes(new byte[] { 'E', 'x', 'i', 'f', 0, 0 });
		out.writeBytes(tiff);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}
}
