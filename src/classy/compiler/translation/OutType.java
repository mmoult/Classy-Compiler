package classy.compiler.translation;

import classy.compiler.analyzing.Type;

public class OutType {
	protected Type linked;
	protected String mangledName;
	protected int typeNum;
	
	protected int alignment = 8;
	//TODO make the size work
	protected int size = 8;

	public OutType(Type linked, String mangledName, int typeNum) {
		this.linked = linked;
		this.mangledName = mangledName;
		this.typeNum = typeNum;
	}
	
	
}
