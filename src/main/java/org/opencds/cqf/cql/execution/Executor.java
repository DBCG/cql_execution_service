package org.opencds.cqf.cql.execution;

import ca.uhn.fhir.context.FhirContext;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.ExpressionDef;
import org.cqframework.cql.elm.execution.FunctionDef;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.data.fhir.FhirBundleCursorStu3;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderStu3;
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

    private Map<String, List<Integer>> locations = new HashMap<>();

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
            libraryManager.getLibrarySourceLoader().clearProviders();
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

    private void registerProviders(Context context, String termSvcUrl, String termUser,
                                   String termPass, String dataPvdrURL, String dataUser, String dataPass)
    {
        // TODO: plugin authorization for data provider when available

        String defaultEndpoint = "http://measure.eval.kanvix.com/cqf-ruler/baseDstu3";

        BaseFhirDataProvider provider = new FhirDataProviderStu3()
                .setEndpoint(dataPvdrURL == null ? defaultEndpoint : dataPvdrURL);

        FhirTerminologyProvider terminologyProvider = new FhirTerminologyProvider()
                .withBasicAuth(termUser, termPass)
                .withEndpoint(termSvcUrl == null ? defaultEndpoint : termSvcUrl);

        provider.setTerminologyProvider(terminologyProvider);
        provider.setExpandValueSets(true);
        context.registerDataProvider("http://hl7.org/fhir", provider);
        context.registerLibraryLoader(getLibraryLoader());
    }

    private void performRetrieve(Iterable result, JSONObject results) {
        FhirContext fhirContext = FhirContext.forDstu3(); // for JSON parsing
        Iterator it = result.iterator();
        List<Object> findings = new ArrayList<>();
        while (it.hasNext()) {
            // returning full JSON retrieve response
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

    public CqlTranslator getTranslator(String cql, LibraryManager libraryManager, ModelManager modelManager) {
        return getTranslator(new ByteArrayInputStream(cql.getBytes(StandardCharsets.UTF_8)), libraryManager, modelManager);
    }

    public CqlTranslator getTranslator(InputStream cqlStream, LibraryManager libraryManager, ModelManager modelManager) {
        ArrayList<CqlTranslator.Options> options = new ArrayList<>();
        options.add(CqlTranslator.Options.EnableDateRangeOptimization);
        options.add(CqlTranslator.Options.EnableAnnotations);
        options.add(CqlTranslator.Options.EnableDetailedErrors);
        CqlTranslator translator;
        try {
            translator = CqlTranslator.fromStream(cqlStream, modelManager, libraryManager,
                    options.toArray(new CqlTranslator.Options[options.size()]));
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Errors occurred translating library: %s", e.getMessage()));
        }

        if (translator.getErrors().size() > 0) {
            throw new IllegalArgumentException(errorsToString(translator.getErrors()));
        }

        return translator;
    }

    public String errorsToString(Iterable<CqlTranslatorException> exceptions) {
        ArrayList<String> errors = new ArrayList<>();
        for (CqlTranslatorException error : exceptions) {
            TrackBack tb = error.getLocator();
            String lines = tb == null ? "[n/a]" : String.format("%s[%d:%d, %d:%d]",
                    (tb.getLibrary() != null ? tb.getLibrary().getId() + (tb.getLibrary().getVersion() != null
                            ? ("-" + tb.getLibrary().getVersion()) : "") : ""),
                    tb.getStartLine(), tb.getStartChar(), tb.getEndLine(), tb.getEndChar());
            errors.add(lines + error.getMessage() + "\n");
        }

        return errors.toString();
    }

    private void setExpressionLocations(org.hl7.elm.r1.Library library) {
        if (library.getStatements() == null) return;
        for (org.hl7.elm.r1.ExpressionDef def : library.getStatements().getDef()) {
            int startLine = def.getTrackbacks().isEmpty() ? 0 : def.getTrackbacks().get(0).getStartLine();
            int startChar = def.getTrackbacks().isEmpty() ? 0 : def.getTrackbacks().get(0).getStartChar();
            List<Integer> loc = Arrays.asList(startLine, startChar);
            locations.put(def.getName(), loc);
        }
    }

    public Library readLibrary(InputStream xmlStream) {
        try {
            return CqlLibraryReader.read(xmlStream);
        } catch (IOException | JAXBException e) {
            throw new IllegalArgumentException("Error encountered while reading ELM xml: " + e.getMessage());
        }
    }

    public Library translateLibrary(CqlTranslator translator) {
        return readLibrary(new ByteArrayInputStream(translator.toXml().getBytes(StandardCharsets.UTF_8)));
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public String evaluateCql(String requestData) throws JAXBException, IOException, ParseException {

        JSONParser parser = new JSONParser();
        JSONObject json;
        try {
            json = (JSONObject) parser.parse(requestData);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Error parsing JSON request: " + e.getMessage());
        }

        String code = (String) json.get("code");
        String fhirServiceUri = (String) json.get("fhirServiceUri");
        String fhirUser = (String) json.get("fhirUser");
        String fhirPass = (String) json.get("fhirPass");
        String dataServiceUri = (String) json.get("dataServiceUri");
        String dataUser = (String) json.get("dataUser");
        String dataPass = (String) json.get("dataPass");
        String patientId = (String) json.get("patientId");

        CqlTranslator translator;
        try {
            translator = getTranslator(code, getLibraryManager(), getModelManager());
        }
            catch (IllegalArgumentException iae) {
            JSONObject result = new JSONObject();
            JSONArray resultArr = new JSONArray();
            result.put("translation-error", iae.getMessage());
            resultArr.add(result);
            return resultArr.toJSONString();
        }

        setExpressionLocations(translator.getTranslatedLibrary().getLibrary());

        if (locations.isEmpty()) {
            JSONObject result = new JSONObject();
            JSONArray resultArr = new JSONArray();
            result.put("result", "Please provide valid CQL named expressions for execution output");
            result.put("name", "No expressions found");
            String location = String.format("[%d:%d]", 0, 0);
            result.put("location", location);

            resultArr.add(result);
            return resultArr.toJSONString();
        }

        Library library = translateLibrary(translator);

        Context context = new Context(library);
        registerProviders(context, fhirServiceUri, fhirUser, fhirPass, dataServiceUri, dataUser, dataPass);

        JSONArray resultArr = new JSONArray();
        for (ExpressionDef def : library.getStatements().getDef()) {
            context.enterContext(def.getContext());
            if (!patientId.equals("null") && !patientId.isEmpty()) {
                context.setContextValue(context.getCurrentContext(), patientId);
            }
            else {
                context.setContextValue(context.getCurrentContext(), "null");
            }
            JSONObject result = new JSONObject();

            try {
                result.put("name", def.getName());

                String location = String.format("[%d:%d]", locations.get(def.getName()).get(0), locations.get(def.getName()).get(1));
                result.put("location", location);

                Object res = def instanceof FunctionDef ? "Definition successfully validated" : def.getExpression().evaluate(context);

                if (res instanceof FhirBundleCursorStu3) {
                    performRetrieve((Iterable) res, result);
                }
                else if (res instanceof List) {
                    if (((List) res).size() > 0 && ((List) res).get(0) instanceof IBaseResource) {
                        performRetrieve((Iterable) res, result);
                    }
                    else {
                        result.put("result", res == null ? "Null" : res.toString());
                    }
                }
                else if (res instanceof IBaseResource) {
                    result.put("result", FhirContext.forDstu3().newJsonParser().setPrettyPrint(true).encodeResourceToString((IBaseResource) res));
                }
                else {
                    result.put("result", res == null ? "Null" : res.toString());
                }
                result.put("resultType", resolveType(res));
            }
            catch (RuntimeException re) {
                result.put("error", re.getMessage());
                re.printStackTrace();
            }
            resultArr.add(result);
        }
        return resultArr.toJSONString();
    }

}
