package io.pravega.connectors.flink.serialization;

import io.pravega.client.stream.Serializer;
import io.pravega.connectors.flink.table.descriptors.PravegaRegistryValidator;
import io.pravega.schemaregistry.client.SchemaRegistryClientConfig;
import io.pravega.schemaregistry.serializers.SerializerConfig;
import io.pravega.schemaregistry.serializers.SerializerFactory;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.descriptors.DescriptorProperties;
import org.apache.flink.table.factories.DeserializationSchemaFactory;
import org.apache.flink.table.factories.SerializationSchemaFactory;
import org.apache.flink.table.factories.TableFormatFactoryBase;
import org.apache.flink.types.Row;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class PravegaRegistryRowFormatFactory extends TableFormatFactoryBase<Row>
        implements SerializationSchemaFactory<Row>, DeserializationSchemaFactory<Row> {

    public PravegaRegistryRowFormatFactory() {
        super(PravegaRegistryValidator.FORMAT_TYPE_VALUE, 1, true);
    }

    @Override
    public DeserializationSchema<Row> createDeserializationSchema(Map<String, String> properties) {
        final DescriptorProperties descriptorProperties = getValidatedProperties(properties);

        SerializerConfig serializerConfig = getSerializerConfig(descriptorProperties);
        AtomicReference<TypeInformation<Row>> rowTypeInfo = new AtomicReference<>();

        Serializer<Row> serializer = SerializerFactory.deserializeAsT(serializerConfig, ((serializationFormat, o) -> {
            switch (serializationFormat) {
                case Json:
                case Avro:
                case Protobuf:
                    rowTypeInfo.set(null);
                    return new Row(1);
            }
            return new Row(0);
        }));

        return new PravegaDeserializationSchema<>(rowTypeInfo.get(), serializer);
    }

    @Override
    public SerializationSchema<Row> createSerializationSchema(Map<String, String> properties) {
        final DescriptorProperties descriptorProperties = getValidatedProperties(properties);

        SerializerConfig serializerConfig = getSerializerConfig(descriptorProperties);

        Serializer<Row> serializer = SerializerFactory.deserializeAsT(serializerConfig, ((serializationFormat, o) -> {
            switch (serializationFormat) {
                case Json:
                case Avro:
                case Protobuf:
                    return new Row(1);
            }
            return new Row(0);
        }));

        return new PravegaSerializationSchema<>(serializer);
    }

    private static DescriptorProperties getValidatedProperties(Map<String, String> propertiesMap) {
        final DescriptorProperties descriptorProperties = new DescriptorProperties(true);
        descriptorProperties.putProperties(propertiesMap);

        new PravegaRegistryValidator().validate(descriptorProperties);

        return descriptorProperties;
    }

    private SerializerConfig getSerializerConfig(DescriptorProperties descriptorProperties) {

        String schemaRegistryUri = descriptorProperties.getString(PravegaRegistryValidator.FORMAT_SCHEMA_REGISTRY_URL);
        String namespace = descriptorProperties.getString(PravegaRegistryValidator.FORMAT_SCHEMA_REGISTRY_NAMESPACE);
        String group = descriptorProperties.getString(PravegaRegistryValidator.FORMAT_SCHEMA_REGISTRY_GROUP);

        SchemaRegistryClientConfig config = SchemaRegistryClientConfig.builder()
                .schemaRegistryUri(URI.create(schemaRegistryUri)).namespace(namespace).build();

        SerializerConfig serializerConfig = SerializerConfig.builder()
                .groupId(group)
                .registryConfig(config)
                .build();

        return serializerConfig;
    }
}
