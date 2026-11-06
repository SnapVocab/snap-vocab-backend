package vn.ptit.snapvocab.scan.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanImage;

/**
 * Shrinks a photo to the size the AI service works at before sending it.
 *
 * <p>The AI resizes every image so its long edge is at most MAX_INPUT_SIZE, so
 * the extra pixels of a phone photo are only upload time: a 2.4MB shot took
 * about a minute to reach a remote GPU, the same scene at 1400px ~6s, with the
 * same detections. Anything this class cannot handle safely is sent as is.
 */
public final class ScanImageDownscaler {

	private static final float JPEG_QUALITY = 0.9f;

	private ScanImageDownscaler() {
	}

	/**
	 * @param maxLongEdge target long edge in pixels; 0 or less disables downscaling
	 * @return a JPEG no larger than {@code maxLongEdge}, or {@code image} unchanged
	 */
	public static ScanImage fit(ScanImage image, int maxLongEdge) {
		if (maxLongEdge <= 0 || !isDecodable(image.contentType()) || hasRotatingExif(image.bytes())) {
			return image;
		}
		try {
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(image.bytes()));
			if (source == null || Math.max(source.getWidth(), source.getHeight()) <= maxLongEdge) {
				return image;
			}
			byte[] jpeg = encodeJpeg(scale(source, maxLongEdge));
			if (jpeg.length >= image.bytes().length) {
				return image;
			}
			return new ScanImage(jpeg, jpegName(image.filename()), "image/jpeg");
		} catch (IOException | RuntimeException e) {
			// CMYK JPEGs and the like: let the AI service decode the original.
			return image;
		}
	}

	private static boolean isDecodable(String contentType) {
		// ImageIO has no WebP reader.
		String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		return type.equals("image/jpeg") || type.equals("image/png");
	}

	private static BufferedImage scale(BufferedImage source, int maxLongEdge) {
		double ratio = (double) maxLongEdge / Math.max(source.getWidth(), source.getHeight());
		int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
		int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));

		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = target.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			// Transparent PNG areas become white rather than black.
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, width, height);
			g.drawImage(source, 0, 0, width, height, null);
		} finally {
			g.dispose();
		}
		return target;
	}

	private static byte[] encodeJpeg(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(stream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.write(null, new IIOImage(image, null, null), param);
		} finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

	private static String jpegName(String filename) {
		String base = filename == null ? "photo" : filename.replaceFirst("\\.[^.]*$", "");
		return base + ".jpg";
	}

	/**
	 * True when a JPEG carries an EXIF orientation other than "upright". ImageIO
	 * ignores that tag and the re-encoded file would drop it, turning the photo
	 * sideways, so such images are left for the AI service, which honours it.
	 */
	static boolean hasRotatingExif(byte[] bytes) {
		if (bytes.length < 4 || (bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) {
			return false;
		}
		int pos = 2;
		while (pos + 4 <= bytes.length && (bytes[pos] & 0xFF) == 0xFF) {
			int marker = bytes[pos + 1] & 0xFF;
			if (marker == 0xDA || marker == 0xD9) {
				return false; // image data starts: no EXIF before it
			}
			int length = u16(bytes, pos + 2, false);
			int segment = pos + 4;
			if (marker == 0xE1 && segment + 6 <= bytes.length && isExifHeader(bytes, segment)) {
				Integer orientation = readOrientation(bytes, segment + 6, Math.min(bytes.length, pos + 2 + length));
				return orientation != null && orientation != 1;
			}
			pos += 2 + length;
		}
		return false;
	}

	private static boolean isExifHeader(byte[] b, int at) {
		return b[at] == 'E' && b[at + 1] == 'x' && b[at + 2] == 'i' && b[at + 3] == 'f'
				&& b[at + 4] == 0 && b[at + 5] == 0;
	}

	/** Reads tag 0x0112 from IFD0 of the TIFF block starting at {@code tiff}. */
	private static Integer readOrientation(byte[] b, int tiff, int end) {
		if (tiff + 8 > end) {
			return null;
		}
		boolean littleEndian = b[tiff] == 'I' && b[tiff + 1] == 'I';
		if (!littleEndian && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) {
			return null;
		}
		int ifd = tiff + (int) u32(b, tiff + 4, littleEndian);
		if (ifd + 2 > end) {
			return null;
		}
		int entries = u16(b, ifd, littleEndian);
		for (int i = 0; i < entries; i++) {
			int entry = ifd + 2 + i * 12;
			if (entry + 12 > end) {
				return null;
			}
			if (u16(b, entry, littleEndian) == 0x0112) {
				return u16(b, entry + 8, littleEndian);
			}
		}
		return null;
	}

	private static int u16(byte[] b, int at, boolean littleEndian) {
		int hi = b[at] & 0xFF;
		int lo = b[at + 1] & 0xFF;
		return littleEndian ? (lo << 8) | hi : (hi << 8) | lo;
	}

	private static long u32(byte[] b, int at, boolean littleEndian) {
		long first = u16(b, at, littleEndian);
		long second = u16(b, at + 2, littleEndian);
		return littleEndian ? (second << 16) | first : (first << 16) | second;
	}
}
