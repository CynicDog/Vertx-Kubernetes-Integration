package cynicdog.io.data;

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.ProtoSchema;

@ProtoSchema(
        includeClasses = {Embedding.class},
        schemaFileName = "embedding.proto",
        schemaFilePath = "proto",
        schemaPackageName = "embedding"
)
public interface EmbeddingSchema extends GeneratedSchema {
}