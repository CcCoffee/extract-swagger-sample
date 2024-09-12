package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.File;
import java.io.IOException;

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
        Object example = content.get("application/json").getExample();
        
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
    }
}
