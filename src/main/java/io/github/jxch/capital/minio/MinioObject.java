package io.github.jxch.capital.minio;

import java.io.InputStream;

public interface MinioObject {

    InputStream inputStream();

    void output(InputStream inputStream);

    long objectSize();

    default String contentType() {
        return "application/octet-stream";
    }

    default long partSize() {
        return -1;
    }

}
