package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

public abstract class Expression {
	protected NestingExpression parent;
	
	public Expression(NestingExpression parent) {
		this.parent = parent;
	}
	
	/**
	 * Parses important data for the expression from the token
	 * iterator. Should throw a {@link ParseException} in subclasses
	 * if there is a syntax error in the source token list.
	 * <p>
	 * The token iterator should be returned at one index beyond the end of the
	 * tokens used for this particular expression.
	 * @param it the iterator used to traverse the token list
	 * @param end the index of the token list that may not be reached in processing.
	 */
	public abstract void parse(TokenIterator it, int end);
	
	public NestingExpression getParent() {
		return parent;
	}
	
	/**
	 * Should be overridden in subclasses to return all nested expressions in
	 * sequential order. These will be checked by the {@link Checker}.
	 * @return the list of expressions to check, in order. Default returns an
	 * empty list.
	 */
	public List<Expression> toCheck() {
		return new ArrayList<>();
	}
	
	/**
	 * Creates a pretty print representation of this structure, and all nested
	 * structures.
	 * @param indents the number of indents that should be used if this expression
	 * requires another line to be drawn. Should be used in conjunction with
	 * {@link #getIndents(int)}, which will use the uniform indent string.
	 * @return the pretty print string representation of this structure.
	 */
	public String pretty(int indents) {
		return toString();
	}
	
	protected String getIndents(int indents) {
		StringBuffer buf = new StringBuffer();
		String indentStr = " ";
		for (int i=0; i<indents; i++)
			buf.append(indentStr);
		return buf.toString();
	}

}
