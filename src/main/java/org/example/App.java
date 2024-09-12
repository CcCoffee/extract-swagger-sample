package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Hello world!
 */
public class App {
    public static OpenAPI openAPI;

    public static void main(String[] args) {
        System.out.println("Hello World!");

        try {
            String sampleResponse = getSampleResponse("src/main/resources/static/api.yml", "/pet");
            System.out.println(sampleResponse);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getSampleResponse(String apiJsonPath, String endpoint) throws IOException {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(apiJsonPath, null, null);
        openAPI = result.getOpenAPI();
        Paths paths = openAPI.getPaths();

        Content content = paths.get(endpoint).getPost().getResponses().get("200").getContent();
        MediaType mediaType = content.get("application/json");
        Object example = mediaType.getExample();

        if (example == null) {
            Schema<?> schema = mediaType.getSchema();
            example = generateExampleFromSchema(schema);
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
    }

    private static Object generateExampleFromSchema(Schema<?> schema) {
        if (schema.getType() == null) {
            Map<String, Object> example = new HashMap<>();
            if (schema.get$ref() != null) {
                // 获取引用的Schema
                String ref = schema.get$ref();
                Schema<?> refSchema = resolveSchemaReference(ref);
                return generateExampleFromSchema(refSchema);
            } else {
                schema.getProperties().forEach((key, value) -> {
                    Schema<?> propertySchema = (Schema<?>) value;
                    example.put(key, generateExampleFromSchema(propertySchema));
                });
            }
            return example;
        } else if (schema instanceof ObjectSchema) {
            Map<String, Object> example = new HashMap<>();
            if (schema.get$ref() != null) {
                // 获取引用的Schema
                String ref = schema.get$ref();
                Schema<?> refSchema = resolveSchemaReference(ref);
                return generateExampleFromSchema(refSchema);
            } else {
                schema.getProperties().forEach((key, value) -> {
                    Schema<?> propertySchema = (Schema<?>) value;
                    example.put(key, generateExampleFromSchema(propertySchema));
                });
            }
            return example;
        } else if (schema instanceof ArraySchema) {
            Schema<?> itemsSchema = ((ArraySchema) schema).getItems();
            if (itemsSchema.get$ref() != null) {
                // 获取引用的Schema
                String ref = itemsSchema.get$ref();
                Schema<?> refSchema = resolveSchemaReference(ref);
                return new Object[]{generateExampleFromSchema(refSchema)};
            } else {
                return new Object[]{generateExampleFromSchema(itemsSchema)};
            }
        } else {
            return getDefaultExampleValue(schema);
        }
    }

    // 新增方法，用于解析$ref引用的Schema
    private static Schema<?> resolveSchemaReference(String ref) {
        // 实现解析逻辑，例如从OpenAPI对象中获取引用的Schema
        // 这里假设有一个全局的OpenAPI对象
        return openAPI.getComponents().getSchemas().get(ref.replace("#/components/schemas/", ""));
    }

    private static Object getDefaultExampleValue(Schema<?> schema) {
        if (schema instanceof ObjectSchema) {
            return schema.getExample() != null ? schema.getExample() : new HashMap<>();
        } else if (schema instanceof ArraySchema) {
            return schema.getExample() != null ? schema.getExample() : new Object[]{};
        } else if (schema instanceof StringSchema) {
            return schema.getExample() != null ? schema.getExample() : "string";
        } else if (schema instanceof IntegerSchema) {
            return schema.getExample() != null ? schema.getExample() : 0;
        } else if (schema instanceof BooleanSchema) {
            return schema.getExample() != null ? schema.getExample() : true;
        } else if (schema instanceof NumberSchema) {
            return schema.getExample() != null ? schema.getExample() : 0.0;
        } else {
            return null;
        }
    }
}
