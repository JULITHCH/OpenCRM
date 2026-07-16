package de.julith.opencrm.shared.storage;

import java.io.InputStream;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3-kompatible Ablage (AWS S3 oder MinIO) für Import-/Exportdateien — persistent und
 * pod-übergreifend (docs/11). Aktiv bei opencrm.storage.type=s3. Zugangsdaten aus dem
 * Secret-Store (Env), niemals im Code. Path-Style-Access für MinIO-Kompatibilität.
 */
@Component
@ConditionalOnProperty(name = "opencrm.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final String bucket;

    public S3FileStorage(
            @Value("${opencrm.storage.s3.endpoint:}") String endpoint,
            @Value("${opencrm.storage.s3.region:us-east-1}") String region,
            @Value("${opencrm.storage.s3.bucket}") String bucket,
            @Value("${opencrm.storage.s3.access-key}") String accessKey,
            @Value("${opencrm.storage.s3.secret-key}") String secretKey,
            @Value("${opencrm.storage.s3.path-style:true}") boolean pathStyle) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        this.client = builder.build();
        this.bucket = bucket;
    }

    @Override
    public void put(String key, InputStream content, long contentLength) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromInputStream(content, contentLength));
    }

    @Override
    public InputStream get(String key) {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
