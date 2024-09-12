package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Hello world!
 */
public class App {
    public static OpenAPI openAPI;
    public static Swagger swagger;

    public static void main(String[] args) {
        System.out.println("Hello World!");

        try {
//            String sampleResponse = getSampleResponse("src/main/resources/static/api.yml", "/pet");
            String sampleResponse = getSampleResponse("src/main/resources/static/swagger-2.0.yml", "/pet");
            System.out.println(sampleResponse);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getSampleResponse(String apiJsonPath, String endpoint) throws IOException {
        if (isOAS3(apiJsonPath)) {
            SwaggerParseResult result = new OpenAPIV3Parser().readLocation(apiJsonPath, null, null);
            openAPI = result.getOpenAPI();
            return getOAS3SampleResponse(endpoint);
        } else {
            swagger = new SwaggerParser().read(apiJsonPath);
            return getSwagger2SampleResponse(endpoint);
        }
    }

    private static boolean isOAS3(String apiJsonPath) throws IOException {
        ObjectMapper mapper;
        if (apiJsonPath.toLowerCase().endsWith(".yml") || apiJsonPath.toLowerCase().endsWith(".yaml")) {
            mapper = new ObjectMapper(new YAMLFactory());
        } else {
            mapper = new ObjectMapper();
        }

        JsonNode rootNode = mapper.readTree(new File(apiJsonPath));
        if (rootNode.has("openapi")) {
            return true;
        } else if (rootNode.has("swagger")) {
            return false;
        } else {
            throw new IllegalArgumentException("Unknown API specification format");
        }
    }

    private static String getOAS3SampleResponse(String endpoint) throws IOException {
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

    private static String getSwagger2SampleResponse(String endpoint) throws IOException {
        io.swagger.models.Path path = swagger.getPath(endpoint);
        io.swagger.models.Response response = path.getPost().getResponses().get("200");
        Object example = response.getExamples().get("application/json");

        if (example == null) {
            io.swagger.models.Model schema = response.getResponseSchema();
            example = generateExampleFromSwagger2Schema(schema);
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
    }

    private static Object generateExampleFromSwagger2Schema(io.swagger.models.Model schema) {
        // 实现Swagger 2.0的示例生成逻辑
        // 类似于generateExampleFromSchema方法
        return null; // 具体实现略
    }

    private static Object generateExampleFromSchema(Schema<?> schema) {
        if (schema instanceof ComposedSchema) {
             ComposedSchema composedSchema = (ComposedSchema) schema;
             if (composedSchema.getAllOf()!= null && !composedSchema.getAllOf().isEmpty()) {
                 return generateExampleFromSchema(composedSchema.getAllOf().get(0));
             } else if (composedSchema.getOneOf()!= null && !composedSchema.getOneOf().isEmpty()) {
                 return generateExampleFromSchema(composedSchema.getOneOf().get(0));
             } else if (composedSchema.getAnyOf()!= null && !composedSchema.getAnyOf().isEmpty()) {
                 return generateExampleFromSchema(composedSchema.getAnyOf().get(0));
             } else {
                 return getDefaultExampleValue(schema);
             }
        } else if (schema.getType() == null) {
            Map<String, Object> example = new HashMap<>();
            if (schema.get$ref() != null) {
                // 获取引用的Schema
                String ref = schema.get$ref();
                Schema<?> refSchema = resolveSchemaReference(ref);
                return generateExampleFromSchema(refSchema);
            } else if (schema.get$ref() != null) {
                schema.getProperties().forEach((key, value) -> {
                    Schema<?> propertySchema = (Schema<?>) value;
                    example.put(key, generateExampleFromSchema(propertySchema));
                });
            } else if (schema.getNot() != null) {
                return generateExampleFromSchema(schema.getNot());
            }
            return example;
        } else if (schema instanceof ObjectSchema) {
            Map<String, Object> example = new HashMap<>();
            if (schema.get$ref() != null) {
                // 获取引用的Schema
                String ref = schema.get$ref();
                Schema<?> refSchema = resolveSchemaReference(ref);
                return generateExampleFromSchema(refSchema);
            } else if (schema.getProperties() != null) {
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
            if (schema.getExample() != null) {
                return schema.getExample();
            } else if (schema.getEnum()!=null && !schema.getEnum().isEmpty()) {
                return schema.getEnum().get(0).toString();
            } else {
                return "string";
            }
        } else if (schema instanceof IntegerSchema) {
            return schema.getExample() != null ? schema.getExample() : 0;
        } else if (schema instanceof BooleanSchema) {
            return schema.getExample() != null ? schema.getExample() : true;
        } else if (schema instanceof NumberSchema) {
            return schema.getExample() != null ? schema.getExample() : 0;
        } else if (schema instanceof DateSchema) {
            return schema.getExample() != null ? schema.getExample() : LocalDate.now().toString();
        } else if (schema instanceof DateTimeSchema) {
            return schema.getExample() != null ? schema.getExample() : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        } else if (schema instanceof BinarySchema) {
            return schema.getExample() != null ? schema.getExample() : "string";
        } else if (schema instanceof ByteArraySchema) {
            return schema.getExample() != null ? schema.getExample() : "string";
        } else if (schema instanceof EmailSchema) {
            return schema.getExample() != null ? schema.getExample() : "user@example.com";
        } else if (schema instanceof FileSchema) {
            return schema.getExample() != null ? schema.getExample() : "string";
        } else if (schema instanceof PasswordSchema) {
            return schema.getExample() != null ? schema.getExample() : "string";
        } else if (schema instanceof UUIDSchema) {
            return schema.getExample() != null ? schema.getExample() : "123e4567-e89b-12d3-a456-426614174000";
        } else if (schema instanceof MapSchema) {
            return schema.getExample() != null ? schema.getExample() : new HashMap<>();
        } else {
            return null;
        }
    }
}
