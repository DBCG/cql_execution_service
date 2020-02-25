package org.opencds.cqf.cql.execution;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.google.gson.*;
import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.elm.execution.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.data.fhir.FhirBundleCursorStu3;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderStu3;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;
import org.opencds.cqf.cql.utils.CodeMappingUtils;
import org.opencds.cqf.cql.utils.ParameterUtils;
import org.opencds.cqf.cql.utils.TranslatorUtils;
import org.opencds.cqf.cql.utils.service.BaseCodeMapperService;
import org.opencds.cqf.cql.utils.service.FhirCodeMapperServiceStu3;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.List;

@Path("evaluate")
public class Executor {

    private Map<String, List<Integer>> locations = new HashMap<>();
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // for future use
    private ModelManager modelManager;
    private BaseCodeMapperService codeMapperService;
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
            libraryLoader = new ExecutorLibraryLoader(getLibraryManager(), getModelManager());
        }
        return libraryLoader;
    }

    private void registerQdmProviders(Context context, String termSvcUrl, String termUser,
                                      String termPass, String dataPvdrURL, String dataUser,
                                      String dataPass, String codeMapServiceUri)
    {
        String defaultEndpoint = "http://measure.eval.kanvix.com/cqf-ruler/baseDstu3/qdm";
        Qdm54DataProvider provider =
                new Qdm54DataProvider(
                        dataPvdrURL == null ? defaultEndpoint : dataPvdrURL,
                        termSvcUrl == null ? "http://measure.eval.kanvix.com/cqf-ruler/baseDstu3" : termSvcUrl
                );
        codeMapperService = codeMapServiceUri == null ? null : new FhirCodeMapperServiceStu3().setEndpoint(codeMapServiceUri);
        context.registerDataProvider("urn:healthit-gov:qdm:v5_4", provider);
        context.registerTerminologyProvider(provider.getTerminologyProvider());
        context.registerLibraryLoader(getLibraryLoader());
    }

    private void registerProviders(Context context, String termSvcUrl, String termUser,
                                   String termPass, String dataPvdrURL, String dataUser,
                                   String dataPass, String codeMapServiceUri)
    {
        // TODO: plugin authorization for data provider when available

        String defaultEndpoint = "http://measure.eval.kanvix.com/cqf-ruler/baseDstu3";

        BaseFhirDataProvider provider = new FhirDataProviderStu3()
                .setEndpoint(dataPvdrURL == null ? defaultEndpoint : dataPvdrURL);

//        if(dataUser != null && !dataUser.isEmpty() && dataPass != null && !dataPass.isEmpty()) {
//        	provider = provider.withBasicAuth(dataUser,dataPass);
//        }

        FhirContext fhirContext = provider.getFhirContext();
        fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        provider.setFhirContext(fhirContext);
        provider.getFhirClient().setEncoding(EncodingEnum.JSON);

        FhirTerminologyProvider terminologyProvider = new FhirTerminologyProvider()
                .withBasicAuth(termUser, termPass)
                .setEndpoint(termSvcUrl == null ? defaultEndpoint : termSvcUrl, false);
        
        codeMapperService = codeMapServiceUri == null ? null : new FhirCodeMapperServiceStu3().setEndpoint(codeMapServiceUri);

        provider.setTerminologyProvider(terminologyProvider);
//        provider.setSearchUsingPOST(true);
//        provider.setExpandValueSets(true);
        context.registerDataProvider("http://hl7.org/fhir", provider);
        context.registerTerminologyProvider(terminologyProvider);
        context.registerLibraryLoader(getLibraryLoader());
    }

    private void performRetrieve(Iterable result, JsonObject results) {
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

        results.add("result", new JsonPrimitive(findings.toString()));
    }

    private String resolveType(Object result) {
        String type = result == null ? "Null" : result.getClass().getSimpleName();
        switch (type) {
            case "BigDecimal": return "Decimal";
            case "ArrayList": return "List";
            case "FhirBundleCursor": return "Retrieve";
        }
        return type;
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

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public String evaluateCql(String requestData) {

        JsonArray results = new JsonArray();
        JsonObject json;
        try {
            json = gson.fromJson(requestData, JsonObject.class);
        } catch (Exception e) {
            results.add(TranslatorUtils.getErrorResponse(e.getMessage()));
            return gson.toJson(results);
        }

        String code = json.has("code") ? json.get("code").getAsString() : null;
        if (code == null) return gson.toJson(results);

        String terminologyServiceUri = json.has("terminologyServiceUri") ? json.get("terminologyServiceUri").getAsString() : null;
        String terminologyUser = json.has("terminologyUser") ? json.get("terminologyUser").getAsString() : null;
        String terminologyPass = json.has("terminologyPass") ? json.get("terminologyPass").getAsString() : null;
        String dataServiceUri = json.has("dataServiceUri") ? json.get("dataServiceUri").getAsString() : null;
        String dataUser = json.has("dataUser") ? json.get("dataUser").getAsString() : null;
        String dataPass = json.has("dataPass") ? json.get("dataPass").getAsString() : null;
        String patientId = json.has("patientId") ? json.get("patientId").getAsString() : null;
        String codeMapperServiceUri = json.has("codeMapperServiceUri") ? json.get("codeMapperServiceUri").getAsString() : null;
        JsonObject codeMapperSystemsMap =  json.has("codeMapperSystemsMap") ? json.get("codeMapperSystemsMap").getAsJsonObject() : null;
        JsonArray parameters =
                json.get("parameters") == null
                        ? null
                        : json.get("parameters").getAsJsonArray();
        
        CqlTranslator translator;
        try {
            translator = TranslatorUtils.getTranslator(code, getLibraryManager(), getModelManager());
        }
        catch (Exception e)
        {
            results.add(TranslatorUtils.getErrorResponse(e.getMessage()));
            return gson.toJson(results);
        }

        setExpressionLocations(translator.getTranslatedLibrary().getLibrary());

        if (locations.isEmpty())
        {
            results.add(TranslatorUtils.getErrorResponse("No expressions found"));
            return gson.toJson(results);
        }

        Library library = TranslatorUtils.translateLibrary(translator);

        Context context = new Context(library);

        if (library.getUsings() != null) {
            for (UsingDef using : library.getUsings().getDef()) {
                if (using.getLocalIdentifier().equals("QDM") && using.getVersion().equals("5.4")) {
                    registerQdmProviders(context, terminologyServiceUri, terminologyUser, terminologyPass, dataServiceUri, dataUser, dataPass, codeMapperServiceUri);
                }
                else {
                    registerProviders(context, terminologyServiceUri, terminologyUser, terminologyPass, dataServiceUri, dataUser, dataPass, codeMapperServiceUri);
                }
            }
        }

        ParameterUtils.resolveParameters(parameters, context);

        if (codeMapperService != null && codeMapperSystemsMap != null) {
             CodeMappingUtils.resolveCodeMapping(library, codeMapperService, codeMapperSystemsMap);
        }

        for (ExpressionDef def : library.getStatements().getDef())
        {
            context.enterContext(def.getContext());

            if (patientId != null && !patientId.isEmpty())
            {
                context.setContextValue(context.getCurrentContext(), patientId);
            }
            else
            {
                context.setContextValue(context.getCurrentContext(), "null");
            }

            JsonObject result = new JsonObject();

            try {
                result.add("name", new JsonPrimitive(def.getName()));

                String location = String.format("[%d:%d]", locations.get(def.getName()).get(0), locations.get(def.getName()).get(1));
                result.add("location", new JsonPrimitive(location));

                Object expressionResult = def instanceof FunctionDef ? "Definition successfully validated" : def.getExpression().evaluate(context);

                if (expressionResult == null)
                {
                    result.add("result", new JsonPrimitive("Null"));
                }
                else if (expressionResult instanceof FhirBundleCursorStu3)
                {
                    performRetrieve((Iterable) expressionResult, result);
                }
                else if (expressionResult instanceof List)
                {
                    if (((List) expressionResult).size() > 0 && ((List) expressionResult).get(0) instanceof IBaseResource)
                    {
                        performRetrieve((Iterable) expressionResult, result);
                    }
                    else
                    {
                        result.add("result", new JsonPrimitive(expressionResult.toString()));
                    }
                }
                else if (expressionResult instanceof IBaseResource)
                {
                    result.add("result", new JsonPrimitive(FhirContext.forDstu3().newJsonParser().setPrettyPrint(true).encodeResourceToString((IBaseResource) expressionResult)));
                }
                else
                {
                    result.add("result", new JsonPrimitive(expressionResult.toString()));
                }
                result.add("resultType", new JsonPrimitive(resolveType(expressionResult)));
            }
            catch (RuntimeException re)
            {
                result.add("error", new JsonPrimitive(re.getMessage()));
                re.printStackTrace();
            }

            results.add(result);
        }

        return gson.toJson(results);
    }

}
