package classy.compiler;

public class CompileException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	public CompileException(Object... msg) {
		super(appendTogether(msg));
	}
	
	public CompileException(Throwable cause) {
		super(cause);
	}
	
	public CompileException(Throwable cause, Object... msg) {
		super(appendTogether(msg), cause);
	}
	
	protected static String appendTogether(Object...msg) {
		StringBuffer buf = new StringBuffer();
		for(Object m: msg) {
			buf.append(m);
		}
		return buf.toString();
	}
}
