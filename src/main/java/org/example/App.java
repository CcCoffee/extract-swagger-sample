package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.models.*;
import io.swagger.models.properties.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.parser.SwaggerParser;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

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
            // Map<HttpMethod,String> sampleResponse = getSampleResponse("src/main/resources/static/api.yml", "/pet");
            Map<HttpMethod,String> sampleResponse = getSampleResponse("src/main/resources/static/swagger-2.0.yml", "/pet/findByStatus");;
            System.out.println(sampleResponse);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<HttpMethod, String> getSampleResponse(String apiJsonPath, String endpoint) throws IOException {
        Map<HttpMethod, String> responses = new HashMap<>();
        if (isOAS3(apiJsonPath)) {
            SwaggerParseResult result = new OpenAPIV3Parser().readLocation(apiJsonPath, null, null);
            openAPI = result.getOpenAPI();
            responses.putAll(getOAS3SampleResponse(endpoint));
        } else {
            swagger = new SwaggerParser().read(apiJsonPath);
            responses.putAll(getSwagger2SampleResponse(endpoint));
        }
        return responses;
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

    private static Map<HttpMethod, String> getOAS3SampleResponse(String endpoint) throws IOException {
        Map<HttpMethod, String> responses = new HashMap<>();
        Paths paths = openAPI.getPaths();
        paths.get(endpoint).readOperationsMap().forEach((method, operation) -> {
            try {
                responses.put(HttpMethod.valueOf(method.name()), getOAS3ResponseForOperation(operation));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return responses;
    }

    private static String getOAS3ResponseForOperation(Operation operation) throws IOException {
        ApiResponses apiResponses = operation.getResponses();
        ApiResponse apiResponse = apiResponses.get("200");
        Content content = apiResponse.getContent();
        MediaType mediaType = content.get("application/json");
        Object example = mediaType.getExample();

        if (example == null) {
            Schema<?> schema = mediaType.getSchema();
            example = generateExampleFromSchema(schema);
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
    }

    private static Map<HttpMethod, String> getSwagger2SampleResponse(String endpoint) throws IOException {
        Map<HttpMethod, String> responses = new HashMap<>();
        io.swagger.models.Path path = swagger.getPath(endpoint);
        path.getOperationMap().forEach((method, operation) -> {
            try {
                responses.put(method, getSwagger2ResponseForOperation(operation));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return responses;
    }

    private static String getSwagger2ResponseForOperation(io.swagger.models.Operation operation) throws IOException {
        io.swagger.models.Response response = operation.getResponses().get("200");
        if (response == null) {
            return "{}"; // 或者返回一个默认的空JSON对象
        }

        Object example = response.getExamples() != null ? response.getExamples().get("application/json") : null;

        if (example == null) {
            io.swagger.models.Model schema = response.getResponseSchema();
            example = generateExampleFromSwagger2Schema(schema);
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
    }

    private static Object generateExampleFromSwagger2Schema(io.swagger.models.Model schema) {
        if (schema instanceof ArrayModel) {
            Property property = ((ArrayModel) schema).getItems();
            if (property instanceof RefProperty) {
                RefProperty refModel = (RefProperty) property;
                Model ref = resolveSwagger2SchemaReference(refModel.get$ref());
                return generateExampleFromSwagger2Schema(ref);
            } else {
                return getDefaultExampleValueForSwagger2((Model) property);
            }
        } else if (schema instanceof RefModel) {
            RefModel refModel = (RefModel) schema;
            io.swagger.models.Model refSchema = resolveSwagger2SchemaReference(refModel.get$ref());
            return generateExampleFromSwagger2Schema(refSchema);
        } else if (schema instanceof ModelImpl) {
            ModelImpl modelImpl = (ModelImpl) schema;
            Map<String, Object> example = new HashMap<>();
            if (modelImpl.getProperties() != null) {
                modelImpl.getProperties().forEach((key, value) -> {
                    Property property = value;
                    example.put(key, generateExampleFromSwagger2Property(property));
                });
            }
            return example;
        } else if (schema instanceof ComposedModel) {
            ComposedModel composedModel = (ComposedModel) schema;
            if (composedModel.getAllOf() != null && !composedModel.getAllOf().isEmpty()) {
                return generateExampleFromSwagger2Schema(composedModel.getAllOf().get(0));
            } else {
                return getDefaultExampleValueForSwagger2(schema);
            }
        } else {
            return getDefaultExampleValueForSwagger2(schema);
        }
    }

    private static Object generateExampleFromSwagger2Property(Property property) {
        if (property instanceof LongProperty) {
            return property.getExample() != null ? property.getExample() : 0L;
        } else if (property instanceof IntegerProperty) {
            return property.getExample() != null ? property.getExample() : 0;
        } else if (property instanceof BooleanProperty) {
            return property.getExample() != null ? property.getExample() : true;
        } else if (property instanceof StringProperty) {
            if (property.getExample() != null) {
                return property.getExample();
            } else if (((StringProperty) property).getEnum() != null && !((StringProperty) property).getEnum().isEmpty()) {
                return ((StringProperty) property).getEnum().get(0);
            } else {
                return "string";
            }
        } else if (property instanceof ArrayProperty) {
            Property items = ((ArrayProperty) property).getItems();
            return new Object[]{generateExampleFromSwagger2Property(items)};
        } else if (property instanceof MapProperty) {
            Property additionalProperties = ((MapProperty) property).getAdditionalProperties();
            Map<String, Object> exampleMap = new HashMap<>();
            exampleMap.put("key", generateExampleFromSwagger2Property(additionalProperties));
            return exampleMap;
        } else if (property instanceof DateProperty) {
            return property.getExample() != null ? property.getExample() : LocalDate.now().toString();
        } else if (property instanceof DateTimeProperty) {
            return property.getExample() != null ? property.getExample() : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        } else if (property instanceof BinaryProperty || property instanceof ByteArrayProperty || property instanceof FileProperty) {
            return property.getExample() != null ? property.getExample() : "string";
        } else if (property instanceof EmailProperty) {
            return property.getExample() != null ? property.getExample() : "user@example.com";
        } else if (property instanceof PasswordProperty) {
            return property.getExample() != null ? property.getExample() : "password";
        } else if (property instanceof UUIDProperty) {
            return property.getExample() != null ? property.getExample() : "123e4567-e89b-12d3-a456-426614174000";
        } else if (property instanceof RefProperty) {
            RefProperty refProperty = (RefProperty) property;
            Model refModel = resolveSwagger2SchemaReference(refProperty.get$ref());
            return generateExampleFromSwagger2Schema(refModel);
        } else {
            return null;
        }
    }

    // 新增方法，用于解析$ref引用的Swagger 2.0 Schema
    private static io.swagger.models.Model resolveSwagger2SchemaReference(String ref) {
        // 实现解析逻辑，例如从Swagger对象中获取引用的Schema
        // 这里假设有一个全局的Swagger对象
        return swagger.getDefinitions().get(ref.replace("#/definitions/", ""));
    }

    private static Object getDefaultExampleValueForSwagger2(io.swagger.models.Model schema) {
        if (schema instanceof io.swagger.models.ModelImpl) {
            return schema.getExample() != null ? schema.getExample() : new HashMap<>();
        } else if (schema instanceof io.swagger.models.properties.ArrayProperty) {
            return schema.getExample() != null ? schema.getExample() : new Object[]{};
        } else if (schema instanceof io.swagger.models.properties.StringProperty) {
            if (schema.getExample() != null) {
                return schema.getExample();
            } else if (((StringProperty) schema).getEnum() != null && !((StringProperty) schema).getEnum().isEmpty()) {
                return ((StringProperty) schema).getEnum().get(0).toString();
            } else {
                return "string";
            }
        } else if (schema instanceof io.swagger.models.properties.IntegerProperty) {
            return schema.getExample() != null ? schema.getExample() : 0;
        } else if (schema instanceof io.swagger.models.properties.BooleanProperty) {
            return schema.getExample() != null ? schema.getExample() : true;
//        } else if (schema instanceof io.swagger.models.properties.NumberProperty) {
//            return schema.getExample() != null ? schema.getExample() : 0;
        } else if (schema instanceof io.swagger.models.properties.DateProperty) {
            return schema.getExample() != null ? schema.getExample() : LocalDate.now().toString();
        } else if (schema instanceof io.swagger.models.properties.DateTimeProperty) {
            return schema.getExample() != null ? schema.getExample() : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        } else if (schema instanceof io.swagger.models.properties.BinaryProperty) {
            return schema.getExample() != null ? schema.getExample() : "string";
        } else if (schema instanceof io.swagger.models.properties.ByteArrayProperty) {
            return schema.getExample() != null ? schema.getExample() : "string";
        } else if (schema instanceof io.swagger.models.properties.EmailProperty) {
            return schema.getExample() != null ? schema.getExample() : "user@example.com";
        } else if (schema instanceof io.swagger.models.properties.FileProperty) {
            return schema.getExample() != null ? schema.getExample() : "string";
        } else if (schema instanceof io.swagger.models.properties.PasswordProperty) {
            return schema.getExample() != null ? schema.getExample() : "string";
        } else if (schema instanceof io.swagger.models.properties.UUIDProperty) {
            return schema.getExample() != null ? schema.getExample() : "123e4567-e89b-12d3-a456-426614174000";
        } else if (schema instanceof io.swagger.models.properties.MapProperty) {
            return schema.getExample() != null ? schema.getExample() : new HashMap<>();
        } else {
            return null;
        }
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
