# capital-minio-keyvalue-spring-boot-starter
Minio 集成 SpringDataKeyValue

## 示例

```properties
spring.minio.url=
spring.minio.access-key=
spring.minio.secret-key=
```

```java
import io.github.jxch.capital.minio.config.MinioAutoConfig;
import io.github.jxch.capital.rocksdb.config.RocksDBAutoConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.map.repository.config.EnableMapRepositories;

@Configuration
@EnableMapRepositories(keyValueTemplateRef = MinioAutoConfig.MINIO_KV_TEMPLATE)
public class config {
}
```

```java
import io.github.jxch.capital.minio.MinioObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@KeySpace("test")
public class Entity implements MinioObject {
    @Id
    private String id;
    private String metaData1;
    private String metaData2;
    private File file;

    public InputStream inputStream() {
        return new FileInputStream(file);
    }

    public void output(InputStream inputStream) {
        Files.copy(inputStream, Path.of());
    }

    public long objectSize() {
        return file.length();
    }
}
```

### Repository 用法

```java
import org.springframework.data.keyvalue.repository.KeyValueRepository;

public interface EntityRepository extends KeyValueRepository<Entity, String> {
    // 就像 Spring Data Jpa 一样使用
}
```

### RocksdbTemplate 用法

```java
import io.github.jxch.capital.minio.MinioTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class MinioTemplateTest {
    @Autowired
    private MinioTemplate minioTemplate;

    public void test() {
        rocksdbTemplate.insert(new Entity("id", "name", "passwd", new File()));
        rocksdbTemplate.findAll(Entity.class);
    }
}
```

### 直接使用 MinioClient

```java
@Autowired
private MinioClient minioClient;
```

```xml
<dependency>
    <groupId>io.github.jxch</groupId>
    <artifactId>capital-minio-keyvalue-spring-boot-starter</artifactId>
</dependency>
```
