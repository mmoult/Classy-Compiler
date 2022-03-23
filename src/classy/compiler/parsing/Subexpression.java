package classy.compiler.parsing;

import java.util.List;

public abstract class Subexpression extends Expression {
	protected Value parent;
	
	public Subexpression(Value parent) {
		this.parent = parent;
	}

	/**
	 * Return whether this subexpression needs chained components to be added.
	 * Or thought of as whether this subexpression is an operation with precedence
	 * that needs to be resolved.
	 * @return a boolean to whether the subexpression is a link
	 */
	public abstract boolean isLink();
	
	public void evaluateChain(int index, List<Subexpression> subs) {
		// do nothing. Most subexpressions will not need to evaluate any chains.
	}
	
	/**
	 * Returns the level of precedence that this operation (if this is an operation.
	 * If so, should be indicated through {@link #isLink()}).
	 * <p>
	 * For reference, multiplication and division have precedence 1, addition and
	 * subtraction have precedence 2.
	 * <p>
	 * If this is not an operation, return null (which is default).
	 * @return the precedence order of this chained operation
	 */
	public Float getPrecedence() {
		return null;
	}
	
	public Value getParent() {
		return parent;
	}
	public void setParent(Value parent) {
		this.parent = parent;
	}
	
	public abstract Subexpression clone();
	
}
