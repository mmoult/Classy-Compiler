package classy.compiler.parsing;

import classy.compiler.lexing.Token;

public class Parameter extends NameBinding {
	protected String name;
	protected Value defaultVal = null;
	protected boolean implicit = false;
	
	public Parameter() {}
	
	public Parameter(String name, Value defaultVal) {
		this.name = name;
		this.defaultVal = defaultVal;
		implicit = true;
	}

	@Override
	public void parse(TokenIterator it, int end) {
		if (!it.match(Token.Type.IDENTIFIER, end))
			throw new ParseException("Identifier should be the first token in a parameter! However, ",
					it.token(), " was found instead.");
		name = it.token().getValue();
		it.next(end);
		try {
			if (it.match(Token.Type.ASSIGN, end)) {
				// We are going to set a default value for this parameter
				it.next(end);
				defaultVal = new Value();
			}
		}catch(ParseException ignore) {
			// If we get a parse exception for going out of bounds, then we have no default value
		}
		// We don't want any exceptions from parsing the default value (assuming there is one)
		//  to be ignored, so it must be outside the try block
		if (defaultVal != null)
			defaultVal.parse(it, end);
	}
	
	public String getName() {
		return name;
	}
	
	public Value getDefaultVal() {
		return defaultVal;
	}
	
	public boolean getImplicit() {
		return implicit;
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer(name);
		if (defaultVal != null) {
			buf.append(" = ");
			buf.append(defaultVal.pretty(indents));
		}
		return buf.toString();
	}

}
