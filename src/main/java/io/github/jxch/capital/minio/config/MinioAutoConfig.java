package io.github.jxch.capital.minio.config;

import io.github.jxch.capital.minio.MinioTemplate;
import io.github.jxch.capital.minio.adapter.MinioKeyValueAdapter;
import io.minio.MinioClient;
import lombok.Data;
import okhttp3.HttpUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.keyvalue.core.KeyValueOperations;
import org.springframework.data.keyvalue.core.KeyValueTemplate;

import java.util.Objects;

@Data
@Configuration
public class MinioAutoConfig {
    public static final String MINIO_KV_TEMPLATE = "minioKeyValueTemplate";
    @Value("${spring.minio.url}")
    private String url;
    @Value("${spring.minio.access-key}")
    private String accessKey;
    @Value("${spring.minio.secret-key}")
    private String secretKey;

    @Bean
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient() {
        return MinioClient.builder()
                .credentials(accessKey, secretKey)
                .endpoint(Objects.requireNonNull(HttpUrl.parse(url)))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(MinioKeyValueAdapter.class)
    public MinioKeyValueAdapter minioKeyValueAdapter(MinioClient minioClient) {
        return new MinioKeyValueAdapter(minioClient);
    }

    @Bean
    @ConditionalOnMissingBean(MinioTemplate.class)
    public MinioTemplate minioTemplate(MinioKeyValueAdapter minioKeyValueAdapter) {
        return new MinioTemplate(minioKeyValueAdapter);
    }

    @Bean(MINIO_KV_TEMPLATE)
    @ConditionalOnMissingBean(name = MINIO_KV_TEMPLATE)
    public KeyValueOperations minioKeyValueTemplate(MinioKeyValueAdapter minioKeyValueAdapter) {
        return new KeyValueTemplate(minioKeyValueAdapter);
    }
}
