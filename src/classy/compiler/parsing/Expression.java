package classy.compiler.parsing;

public abstract class Expression {
	
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
