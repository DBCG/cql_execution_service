package org.opencds.cqf.cql.util;

import org.cqframework.cql.elm.execution.CodeSystemDef;
import org.cqframework.cql.elm.execution.Library;

public class LibraryUtil {
	
	private static int referenceNumber = 0;
	public static CodeSystemDef getCodeSystemDefFromURI(Library library, String URI) {
		for(CodeSystemDef codeSystemDef : library.getCodeSystems().getDef()) {
			if(codeSystemDef.getId().equalsIgnoreCase(URI)) {
				return codeSystemDef;
			}
		}
		return null;
	}
	
	public static CodeSystemDef getCodeSystemDefFromName(Library library, String name) {
		for(CodeSystemDef codeSystemDef : library.getCodeSystems().getDef()) {
			if(codeSystemDef.getName().equalsIgnoreCase(name)) {
				return codeSystemDef;
			}
		}
		return null;
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
