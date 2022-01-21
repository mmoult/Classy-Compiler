package classy.compiler.analyzing;

import classy.compiler.CompileException;

public class CheckException extends CompileException {
	private static final long serialVersionUID = 1L;

	public CheckException(Object... msg) {
		super(msg);
	}
	
	public CheckException(Throwable cause) {
		super(cause);
	}
	
	public CheckException(Throwable cause, Object... msg) {
		super(cause, msg);
	}
}
