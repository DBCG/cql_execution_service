package org.opencds.cqf.cql.execution;

import ca.uhn.fhir.context.FhirContext;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opencds.cqf.cql.data.fhir.FhirBundleCursor;
import org.opencds.cqf.cql.data.fhir.FhirDataProvider;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by Christopher on 1/13/2017.
 */
@Path("evaluate")
public class Executor {

    Map<String, List<Integer> > expressions = new HashMap<>();

    // for future use
    private ModelManager modelManager;
    private ModelManager getModelManager() {
        if (modelManager == null) {
            modelManager = new ModelManager();
        }
        return modelManager;
    }

    private LibraryManager libraryManager;
    private LibraryManager getLibraryManager() {
        if (libraryManager == null) {
            libraryManager = new LibraryManager(getModelManager());
            libraryManager.getLibrarySourceLoader().registerProvider(getLibrarySourceProvider());

        }
        return libraryManager;
    }

    private ExecutorLibrarySourceProvider librarySourceProvider;
    private ExecutorLibrarySourceProvider getLibrarySourceProvider() {
        if (librarySourceProvider == null) {
            librarySourceProvider = new ExecutorLibrarySourceProvider();
        }
        return librarySourceProvider;
    }

    private LibraryLoader libraryLoader;
    private LibraryLoader getLibraryLoader() {
        if (libraryLoader == null) {
            libraryLoader = new ExecutorLibraryLoader(getLibraryManager());
        }
        return libraryLoader;
    }

    private void registerProviders(Context context, String termSvcURL, String termUser, String termPass, String dataPvdrURL, String dataUser, String dataPass) {
        // TODO: plugin data provider authorization when available within the engine

        String defaultEndpoint = "http://fhirtest.uhn.ca/baseDstu3";

        FhirTerminologyProvider terminologyProvider = termUser.equals("user ID") || termPass.isEmpty() ?
                new FhirTerminologyProvider().withEndpoint(termSvcURL == null ? defaultEndpoint : termSvcURL) :
                new FhirTerminologyProvider().withBasicAuth(termUser, termPass)
                        .withEndpoint(termSvcURL == null ? defaultEndpoint : termSvcURL);

        context.registerTerminologyProvider(terminologyProvider);

        FhirDataProvider provider = new FhirDataProvider()
                .withEndpoint(dataPvdrURL == null ? defaultEndpoint : dataPvdrURL);

        provider.setTerminologyProvider(terminologyProvider);

        provider.setExpandValueSets(true);
        context.registerDataProvider("http://hl7.org/fhir", provider);
        context.registerLibraryLoader(getLibraryLoader());
    }

    private void performRetrieve(Object result, JSONObject results) {
        FhirContext fhirContext = FhirContext.forDstu3(); // for JSON parsing
        Iterator<Object> it = ((FhirBundleCursor)result).iterator();
        List<Object> findings = new ArrayList<>();
        while (it.hasNext()) {
            // TODO: currently returning full JSON retrieve response -- ugly and unwieldy
            findings.add(fhirContext
                    .newJsonParser()
                    .setPrettyPrint(true)
                    .encodeResourceToString((org.hl7.fhir.instance.model.api.IBaseResource)it.next()));
        }
        results.put("result", findings.toString());
    }

    private String resolveType(Object result) {
        String type = result == null ? "Null" : result.getClass().getSimpleName();
        if (type.equals("BigDecimal")) { type = "Decimal"; }
        else if (type.equals("ArrayList")) { type = "List"; }
        else if (type.equals("FhirBundleCursor")) { type = "Retrieve"; }
        return type;
    }

    private JSONArray getResultsBuildResponse(Library library, Context context) {
        JSONArray resultArr = new JSONArray();
        for (String expression : expressions.keySet()) {
            JSONObject results = new JSONObject();
            try {
                results.put("name", expression);

                int row = expressions.get(expression).get(0);
                int col = expressions.get(expression).get(1);
                results.put("location", "[" + row + ":" + col + "]");

                Object result = context.resolveExpressionRef(expression).getExpression().evaluate(context);

                if (result instanceof FhirBundleCursor) { // retrieve
                    performRetrieve(result, results);
                }
                else {
                    results.put("result", result == null ? "Null" : result.toString());
                }
                results.put("resultType", resolveType(result));
            }
            catch (RuntimeException e) {
                results.put("error", e.getMessage());
            }
            resultArr.add(results);
        }
        return resultArr;
    }

    private void populateExpressionMap(List<org.hl7.elm.r1.ExpressionDef> defs) {
        for (org.hl7.elm.r1.ExpressionDef expression : defs)
        {
            int startLine;
            int startChar;
            if (expression.getTrackbacks().isEmpty()) {
                startLine = 0;
                startChar = 0;
            }
            else {
                startLine = expression.getTrackbacks().get(0).getStartLine();
                startChar = expression.getTrackbacks().get(0).getStartChar();
            }
            List<Integer> location = Arrays.asList(startLine, startChar);
            expressions.put(expression.getName(), location);
        }
    }

    private Library translate(String cql) {
        try {
            ArrayList<CqlTranslator.Options> options = new ArrayList<>();
            options.add(CqlTranslator.Options.EnableDateRangeOptimization);
            CqlTranslator translator = CqlTranslator.fromText(cql, getModelManager(), getLibraryManager(), options.toArray(new CqlTranslator.Options[options.size()]));

            if (translator.getErrors().size() > 0) {
                System.err.println("Translation failed due to errors:");
                ArrayList<String> errors = new ArrayList<>();
                for (CqlTranslatorException error : translator.getErrors()) {
                    TrackBack tb = error.getLocator();
                    String lines = tb == null ? "[n/a]" : String.format("[%d:%d, %d:%d]",
                            tb.getStartLine(), tb.getStartChar(), tb.getEndLine(), tb.getEndChar());
                    errors.add(lines + error.getMessage());
                }
                throw new IllegalArgumentException(errors.toString());
            }

            populateExpressionMap(translator.getTranslatedLibrary().getLibrary().getStatements().getDef());

            InputStream xmlStream = new ByteArrayInputStream(translator.toXml().getBytes(StandardCharsets.UTF_8));
            return CqlLibraryReader.read(xmlStream);
        }
        catch (IOException | JAXBException e) {
            throw new IllegalArgumentException("An error was encountered while reading the ELM library: " + e.getMessage());
        }
    }

    @POST
    @Consumes({MediaType.TEXT_PLAIN})
    @Produces({MediaType.TEXT_PLAIN})
    public String evaluateCql(String requestData) throws JAXBException, IOException, ParseException {

        JSONParser parser = new JSONParser();
        JSONObject data = (JSONObject) parser.parse(requestData);
        String code = (String) data.get("code");
        String fhirServiceUri = (String) data.get("fhirServiceUri");
        String fhirUser = (String) data.get("fhirUser");
        String fhirPass = (String) data.get("fhirPass");
        String dataServiceUri = (String) data.get("dataServiceUri");
        String dataUser = (String) data.get("dataUser");
        String dataPass = (String) data.get("dataPass");
        String patientId = (String) data.get("patientId");

        Library library;
        try {
            library = translate(code);
        }
        catch (IllegalArgumentException e) {
            JSONObject results = new JSONObject();
            JSONArray resultArr = new JSONArray();
            results.put("translation-error", e.getMessage());
            resultArr.add(results);
            return resultArr.toJSONString();
        }

        Context context = new Context(library);
        context.enterContext(library.getStatements().getDef().get(0).getContext());
        if (!patientId.equals("null") && !patientId.isEmpty()) {
            context.setContextValue(context.getCurrentContext(), patientId);
        }

        registerProviders(context, fhirServiceUri, fhirUser, fhirPass, dataServiceUri, dataUser, dataPass);
        return getResultsBuildResponse(library, context).toJSONString();
    }

}
