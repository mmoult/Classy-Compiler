package classy.compiler.parsing;

import classy.compiler.CompileException;

public class ParseException extends CompileException {
	private static final long serialVersionUID = 1L;

	public ParseException(Object... msg) {
		super(msg);
	}
	
	public ParseException(Throwable cause) {
		super(cause);
	}
	
	public ParseException(Throwable cause, Object... msg) {
		super(cause, msg);
	}
}
