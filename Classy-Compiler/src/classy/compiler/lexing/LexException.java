package classy.compiler.lexing;

import classy.compiler.CompileException;

public class LexException extends CompileException {
	private static final long serialVersionUID = 1L;

	public LexException(Object... msg) {
		super(msg);
	}
	
	public LexException(Throwable cause) {
		super(cause);
	}
	
	public LexException(Throwable cause, Object... msg) {
		super(cause, msg);
	}

}
