package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
        
        try {
            String sampleResponse = getSampleResponse("src/main/resources/static/api.json", "/api/generate");
            System.out.println(sampleResponse);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getSampleResponse(String apiJsonPath, String endpoint) throws IOException {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(apiJsonPath, null, null);
        OpenAPI openAPI = result.getOpenAPI();
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
        if (schema.getType().equals("object")) {
            Map<String, Object> example = new HashMap<>();
            schema.getProperties().forEach((key, value) -> {
                Schema<?> propertySchema = (Schema<?>) value;
                example.put(key, generateExampleFromSchema(propertySchema));
            });
            return example;
        } else if (schema.getType().equals("array")) {
            Map<String, Object> example = new HashMap<>();
            schema.getProperties().forEach((key, value) -> {
                Schema<?> propertySchema = (Schema<?>) value;
                example.put(key, generateExampleFromSchema(propertySchema));
            });
            return new Object[]{example};
        } else {
            return getDefaultExampleValue(schema);
        }
    }

    private static Object getDefaultExampleValue(Schema<?> schema) {
        switch (schema.getType()) {
            case "string":
                return schema.getExample() != null ? schema.getExample() : "string";
            case "integer":
                return schema.getExample() != null ? schema.getExample() : 0;
            case "boolean":
                return schema.getExample() != null ? schema.getExample() : true;
            case "number":
                return schema.getExample() != null ? schema.getExample() : 0.0;
            default:
                return null;
        }
    }
}
