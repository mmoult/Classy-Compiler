package classy.compiler.translation;

import java.util.HashMap;
import java.util.Map;

import classy.compiler.analyzing.Type;

public class OutType {
	protected Type linked;
	protected String mangledName;
	protected int typeNum;
	
	protected int alignment = 8;
	protected int size = 8;
	
	protected Map<String, Integer> fieldLocations = new HashMap<>();

	
	public OutType(Type linked, String mangledName, int typeNum) {
		this.linked = linked;
		this.mangledName = mangledName;
		this.typeNum = typeNum;
	}
	
}