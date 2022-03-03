package classy.compiler.parsing;

import classy.compiler.analyzing.Type;
import classy.compiler.analyzing.Variable;

public abstract class NameBinding extends Expression {
	protected Variable sourced;
	protected Type annotation = null;
	
	public void setSourced(Variable sourced) {
		this.sourced = sourced;
	}
	public Variable getSourced() {
		return sourced;
	}
	
	public Type getAnnotation() {
		return annotation;
	}
	public void setAnnotation(Type annotation) {
		this.annotation = annotation;
	}
}
