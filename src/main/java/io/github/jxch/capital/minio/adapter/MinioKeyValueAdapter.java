package io.github.jxch.capital.minio.adapter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import io.github.jxch.capital.minio.MinioObject;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.keyvalue.core.KeyValueAdapter;
import org.springframework.data.keyvalue.core.query.KeyValueQuery;
import org.springframework.data.util.CloseableIterator;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
public class MinioKeyValueAdapter implements KeyValueAdapter {
    private final static String META_DATA_KEY = "X-Amz-Meta-Data";
    private final MinioClient minioClient;

    private String objectName(Object id) {
        if (id instanceof String) {
            return id.toString();
        }
        throw new IllegalArgumentException("id must be a string");
    }

    @SneakyThrows
    private boolean hasKeyspace(String keyspace) {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(keyspace).build());
    }

    @SneakyThrows
    private String createBucketIfNeed(String keyspace) {
        if (!hasKeyspace(keyspace)) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(keyspace).build());
        }
        return keyspace;
    }

    @NonNull
    @SneakyThrows
    private Map<String, String> toMetadata(@NonNull MinioObject minioObject) {
        return Map.of(META_DATA_KEY, JSON.toJSONString(minioObject, JSONWriter.Feature.PrettyFormat));
    }

    @NonNull
    @Override
    @SneakyThrows
    public Object put(@NonNull Object id, @NonNull Object item, @NonNull String keyspace) {
        if (contains(id, keyspace)) {
            delete(id, keyspace);
        }
        MinioObject minioObject = (MinioObject) item;
        PutObjectArgs args = PutObjectArgs.builder()
                .bucket(createBucketIfNeed(keyspace))
                .object(objectName(id))
                .stream(minioObject.inputStream(), minioObject.objectSize(), minioObject.partSize())
                .headers(toMetadata(minioObject))
                .contentType(minioObject.contentType())
                .build();
        minioClient.putObject(args);
        return item;
    }

    @Override
    @SneakyThrows
    public boolean contains(@NonNull Object id, @NonNull String keyspace) {
        if (hasKeyspace(keyspace)) {
            try {
                minioClient.statObject(StatObjectArgs.builder().bucket(keyspace).object(objectName(id)).build());
                return true;
            } catch (ErrorResponseException e) {
                if (e.errorResponse().code().equals("NoSuchKey")) {
                    return false;
                }
                throw e;
            }
        }
        return false;
    }

    @Nullable
    @Override
    @SneakyThrows
    public Object get(@NonNull Object id, @NonNull String keyspace) {
        if (contains(id, keyspace)) {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(keyspace).object(objectName(id)).build());
            MinioObject minioObject = (MinioObject) JSON.parse(stat.userMetadata().get(META_DATA_KEY));
            try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder().bucket(keyspace).object(objectName(id)).build())) {
                minioObject.output(inputStream);
            }
            return minioObject;
        }
        return null;
    }

    @Nullable
    @Override
    public <T> T get(@NonNull Object id, @NonNull String keyspace, @NonNull Class<T> type) {
        return Optional.ofNullable(get(id, keyspace)).map(type::cast).orElse(null);
    }

    @Nullable
    @Override
    @SneakyThrows
    public Object delete(@NonNull Object id, @NonNull String keyspace) {
        if (contains(id, keyspace)) {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(keyspace).object(objectName(id)).build());
        }
        return null;
    }

    @Nullable
    @Override
    public <T> T delete(@NonNull Object id, @NonNull String keyspace, @NonNull Class<T> type) {
        return Optional.ofNullable(delete(id, keyspace)).map(type::cast).orElse(null);
    }

    @SneakyThrows
    private MinioObject getMinioObject(Result<Item> result, String keyspace) {
        StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(keyspace).object(objectName(result.get().objectName())).build());
        return (MinioObject) JSON.parse(stat.userMetadata().get(META_DATA_KEY));
    }

    @NonNull
    @Override
    public Iterable<?> getAllOf(@NonNull String keyspace) {
        return StreamSupport.stream(minioClient.listObjects(ListObjectsArgs.builder().bucket(keyspace).build()).spliterator(), true)
                .map(itemResult -> getMinioObject(itemResult, keyspace)).toList();
    }

    @SneakyThrows
    private String getId(@NonNull MinioObject minioObject) {
        for (Field field : minioObject.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Id id = AnnotationUtils.getAnnotation(field, Id.class);
            if (Objects.nonNull(id)) {
                return field.get(minioObject).toString();
            }
        }
        throw new IllegalArgumentException("no id found");
    }

    @NonNull
    @Override
    public CloseableIterator<Map.Entry<Object, Object>> entries(@NonNull String keyspace) {
        return new CloseableIterator<>() {
            private final Iterator<Result<Item>> iterator = minioClient.listObjects(ListObjectsArgs.builder().bucket(keyspace).build()).iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Map.Entry<Object, Object> next() {
                MinioObject minioObject = getMinioObject(iterator.next(), keyspace);
                return new AbstractMap.SimpleEntry<>(getId(minioObject), minioObject);
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    @SneakyThrows
    public void deleteAllOf(@NonNull String keyspace) {
        try (var entries = entries(keyspace)) {
            entries.forEachRemaining(entry -> delete(entry.getKey(), keyspace));
        }
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(keyspace).build());
    }

    @Override
    public long count(@NonNull String keyspace) {
        Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder().bucket(keyspace).build());
        return StreamSupport.stream(results.spliterator(), false).count();
    }

    @Override
    public long count(@NonNull KeyValueQuery<?> query, @NonNull String keyspace) {
        MinioQueryCriteria criteria = (MinioQueryCriteria) query.getCriteria();
        try (var entries = entries(keyspace)) {
            return entries.stream()
                    .filter(entry -> Objects.isNull(criteria) || criteria.matches((MinioObject) entry.getValue()))
                    .count();
        }
    }

    @NonNull
    @Override
    public <T> Iterable<T> find(@NonNull KeyValueQuery<?> query, @NonNull String keyspace, @NonNull Class<T> type) {
        MinioQueryCriteria criteria = (MinioQueryCriteria) query.getCriteria();
        try (var entries = entries(keyspace)) {
            return entries.stream()
                    .filter(entry -> Objects.isNull(criteria) || criteria.matches((MinioObject) entry.getValue()))
                    .map(entry -> type.cast(entry.getValue()))
                    .toList();
        }
    }

    @Override
    @SneakyThrows
    public void clear() {
        minioClient.close();
    }

    @Override
    public void destroy() throws Exception {
        minioClient.close();
    }
}
