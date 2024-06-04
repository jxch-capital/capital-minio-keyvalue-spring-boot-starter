package io.github.jxch.capital.minio;

import org.springframework.data.keyvalue.core.KeyValueAdapter;
import org.springframework.data.keyvalue.core.KeyValueTemplate;

public class MinioTemplate extends KeyValueTemplate {

    public MinioTemplate(KeyValueAdapter adapter) {
        super(adapter);
    }

}
