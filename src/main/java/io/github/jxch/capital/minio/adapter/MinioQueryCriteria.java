package io.github.jxch.capital.minio.adapter;

import io.github.jxch.capital.minio.MinioObject;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Objects;
import java.util.function.Function;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class MinioQueryCriteria {
    private Function<MinioObject, Boolean> minioObjectMatches;

    public boolean matches(final MinioObject minioObject) {
        return Objects.isNull(minioObject) || minioObjectMatches.apply(minioObject);
    }

}
