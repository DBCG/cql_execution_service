package org.opencds.cqf.cql.utils;

import org.cqframework.cql.elm.execution.CodeSystemDef;
import org.cqframework.cql.elm.execution.Library;
import org.opencds.cqf.cql.utils.service.BaseCodeMapperService;

public class LibraryUtils {
	
	private static int referenceNumber = 0;
	public static CodeSystemDef getCodeSystemDefFromURI(Library library, String URI) {
		for(CodeSystemDef codeSystemDef : library.getCodeSystems().getDef()) {
			if(codeSystemDef.getId().equalsIgnoreCase(URI)) {
				return codeSystemDef;
			}
		}
		throw new BaseCodeMapperService.MissingCodeSystemDef("Unable to find Codesystem with following URI in library: " + URI);
	}
	
	public static CodeSystemDef getCodeSystemDefFromName(Library library, String name) {
		for(CodeSystemDef codeSystemDef : library.getCodeSystems().getDef()) {
			if(codeSystemDef.getName().equalsIgnoreCase(name)) {
				return codeSystemDef;
			}
		}
		throw new BaseCodeMapperService.MissingCodeSystemDef("Unable to find Codesystem with following name in library: " + name);
	}
	
	public static CodeSystemDef addCodeSystemToLibrary(Library library,String name,String URI) {
		CodeSystemDef returnCodeSystemDef = new CodeSystemDef().withName(name).withId(URI);
		library.getCodeSystems().withDef(returnCodeSystemDef);
		return returnCodeSystemDef;
	}
	
	public static String generateReferenceName(){
		String returnString = "GeneratedName" + referenceNumber;
		referenceNumber++;
		return returnString;
	}
}
