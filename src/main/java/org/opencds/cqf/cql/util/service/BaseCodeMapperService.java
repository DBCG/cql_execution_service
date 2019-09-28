package org.opencds.cqf.cql.util.service;


import java.util.List;

import org.cqframework.cql.elm.execution.Code;
import org.cqframework.cql.elm.execution.Library;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;

public abstract class BaseCodeMapperService {
	// Data members
    protected FhirContext fhirContext;
    protected String endpoint;
    protected IGenericClient fhirClient;
    
 // getters & setters
    public FhirContext getFhirContext() {
        return fhirContext;
    }
    public void setFhirContext(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
        fhirContext.getRestfulClientFactory().setSocketTimeout(1200 * 10000);
    }

    public String getEndpoint() {
        return endpoint;
    }
    public BaseCodeMapperService setEndpoint(String endpoint){
        this.endpoint = endpoint;
        this.fhirClient = getFhirContext().newRestfulGenericClient(endpoint);
        return this;
    }

    public abstract List<Code> translateCode(Code code, String sourceSystem, String targetSystem,Library library) throws CodeMapperIncorrectEquivalenceException, CodeMapperNotFoundException;
    
    public class CodeMapperIncorrectEquivalenceException extends Exception {
		private static final long serialVersionUID = 1L;
		public CodeMapperIncorrectEquivalenceException(String message) {
			super(message);
		}
	}
	
	public class CodeMapperNotFoundException extends Exception {
		private static final long serialVersionUID = 1L;
		public CodeMapperNotFoundException(String message) {
			super(message);
		}
	}
}
