package classy.compiler.parsing;

import classy.compiler.analyzing.Variable;

public abstract class NameBinding extends Expression {
	protected Variable sourced;
	
	public void setSourced(Variable sourced) {
		this.sourced = sourced;
	}
	public Variable getSourced() {
		return sourced;
	}
}
