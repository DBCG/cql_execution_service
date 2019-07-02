package org.opencds.cqf.cql.util.service;

import java.util.ArrayList;
import java.util.List;

import org.cqframework.cql.elm.execution.Code;
import org.cqframework.cql.elm.execution.CodeSystemDef;
import org.cqframework.cql.elm.execution.CodeSystemRef;
import org.cqframework.cql.elm.execution.Library;
import org.opencds.cqf.cql.util.LibraryUtil;
import org.opencds.cqf.cql.util.service.BaseCodeMapperService.CodeMapperNotFoundException;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.resource.ConceptMap;
import ca.uhn.fhir.model.dstu2.resource.Parameters;
import ca.uhn.fhir.model.dstu2.resource.Parameters.Parameter;
import ca.uhn.fhir.model.primitive.CodeDt;
import ca.uhn.fhir.model.primitive.StringDt;

public class FhirCodeMapperServiceDstu2 extends BaseCodeMapperService {

	public FhirCodeMapperServiceDstu2() {
		this.fhirContext = FhirContext.forDstu2();
        fhirContext.getRestfulClientFactory().setSocketTimeout(1200 * 10000);
        
	}
	
	@Override
	public List<Code> translateCode(Code code, String sourceSystem, String targetSystem,Library library) throws CodeMapperIncorrectEquivalenceException, CodeMapperNotFoundException {
		List<Code> returnList = new ArrayList<Code>(); 
		Parameters inParams = new Parameters();
		inParams.addParameter().setName("system").setValue(new StringDt(sourceSystem));
		inParams.addParameter().setName("code").setValue(new StringDt(code.getCode()));
		inParams.addParameter().setName("targetsystem").setValue(new StringDt(targetSystem));
		Parameters outParams = fhirClient.operation()
				.onType(ConceptMap.class)
				.named("$translate")
				.withParameters(inParams)
				.useHttpGet()
				.execute();
		if(!outParams.isEmpty()) {
			for(Parameter outParam:outParams.getParameter()) {
				if(outParam.getName() != null && outParam.getName().equals("match")) {
					for(Parameter matchpart:outParam.getPart()){
						if(matchpart.getName() != null && matchpart.getName().equals("equivalence")) {
							String equivalenceValue = ((CodeDt)matchpart.getValue()).getValue();
							if(!equivalenceValue.equals("equivalent")) {
								throw new CodeMapperIncorrectEquivalenceException("Translated code expected an equivalent match but found a match of equivalence " + equivalenceValue + "instead");
							}
						}
						if(matchpart.getName() != null && matchpart.getName().equals("concept")) {
							CodingDt coding = (CodingDt)matchpart.getValue();
							Code translatedCode = new Code().withCode(coding.getCode());
							if(coding.getSystem() != null) {
								CodeSystemDef targetSystemDef = LibraryUtil.getCodeSystemDefFromURI(library, coding.getSystem());
								if(targetSystemDef == null) {
									targetSystemDef = LibraryUtil.addCodeSystemToLibrary(library, LibraryUtil.generateReferenceName(), coding.getSystem());
								}
								CodeSystemRef targetSystemRef = new CodeSystemRef().withName(targetSystemDef.getName());
								translatedCode.withSystem(targetSystemRef);
							}
							if(coding.getDisplay() != null) {
								translatedCode.withDisplay(coding.getDisplay());
							}
							returnList.add(translatedCode);
						}
					}
				}
			}
		}
		if(returnList.isEmpty()) {
			throw new CodeMapperNotFoundException("No translation found for code " + code.toString() + " in target codesystem " + targetSystem);
		}
		return returnList;
	}
	
}