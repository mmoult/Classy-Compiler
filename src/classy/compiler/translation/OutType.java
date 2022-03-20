package classy.compiler.translation;

import classy.compiler.analyzing.Type;

public class OutType {
	protected Type linked;
	protected String mangledName;
	protected int typeNum;
	
	//TODO make the size work
	protected int alignment = 8;
	protected int size = 8;

	public OutType(Type linked, String mangledName, int typeNum) {
		this.linked = linked;
		this.mangledName = mangledName;
		this.typeNum = typeNum;
	}
}
