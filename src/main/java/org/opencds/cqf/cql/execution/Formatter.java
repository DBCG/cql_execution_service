package org.opencds.cqf.cql.execution;

import org.cqframework.cql.tools.formatter.CqlFormatterVisitor;
import org.cqframework.cql.tools.formatter.Main;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
/**
 * Created by Christopher on 7/30/2017.
 */
@Path("format")
public class Formatter {

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public String formatCql(String unformattedCql) throws IOException {
        JSONParser parser = new JSONParser();
        JSONObject json;
        try {
            json = (JSONObject) parser.parse(unformattedCql);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Error parsing JSON request: " + e.getMessage());
        }

        String code = (String) json.get("code");

        CqlFormatterVisitor.FormatResult formatResult = CqlFormatterVisitor.getFormattedOutput(new ByteArrayInputStream(code.getBytes()));
        StringBuilder output = new StringBuilder();
        if (formatResult.getErrors() != null && !formatResult.getErrors().isEmpty()) {
            for (Exception e : formatResult.getErrors()) {
                output.append(e.getMessage()).append("\n");
            }
        }

        output.append(formatResult.getOutput());

        JSONArray result = new JSONArray();
        JSONObject element = new JSONObject();
        element.put("formatted-cql", output.toString());
        result.add(element);

        return result.toJSONString();
    }
}
