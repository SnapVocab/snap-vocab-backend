package vn.ptit.snapvocab.storage.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import java.net.URI;
import org.junit.jupiter.api.Test;

class MinioConfigTest {

	@Test
	void internalEndpointDoesNotLeakIntoFrontendUploadUrls() throws Exception {
		var properties = properties();
		properties.setInternalEndpoint("http://127.0.0.1:9000");
		var configuration = new MinioConfig(properties);
		try (var internal = configuration.minioClient(); var signer = configuration.presigningMinioClient(internal)) {
			var args = GetPresignedObjectUrlArgs.builder().method(Method.PUT)
					.bucket("snapvocab").object("public/avatars/avatar.jpeg").expiry(300).build();
			assertThat(URI.create(internal.getPresignedObjectUrl(args)).getHost()).isEqualTo("127.0.0.1");
			assertThat(URI.create(signer.getPresignedObjectUrl(args)).getHost()).isEqualTo("storage.example.com");
		}
	}

	@Test
	void deploymentsWithoutInternalEndpointReuseThePublicClient() throws Exception {
		var configuration = new MinioConfig(properties());
		try (var client = configuration.minioClient()) {
			assertThat(configuration.presigningMinioClient(client)).isSameAs(client);
		}
	}

	private MinioProperties properties() {
		var properties = new MinioProperties();
		properties.setEndpoint("https://storage.example.com");
		properties.setAccessKey("test-access-key");
		properties.setSecretKey("test-secret-key");
		return properties;
	}
}
