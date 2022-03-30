package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.analyzing.Type;
import classy.compiler.lexing.Token;

public class Parameter extends NameBinding {
	protected String name;
	protected Value defaultVal = null;
	
	
	public Parameter() {}
	
	public Parameter(String name, Value defaultVal) {
		this.name = name;
		this.defaultVal = defaultVal;
	}

	@Override
	public void parse(TokenIterator it, int end) {
		if (!it.match(Token.Type.IDENTIFIER, end))
			throw new ParseException("Identifier should be the first token in a parameter! However, ",
					it.token(), " was found instead.");
		startToken = it.token();
		name = it.token().getValue();
		it.next(end);
		try {
			// We could see a type annotation or a default value
			if (it.match(Token.Type.COLON, end)) {
				it.next(end);
				if (it.match(Token.Type.IDENTIFIER, end))
					annotation = new Type.Stub(it.token().getValue());
				else
					throw new ParseException("Type name must be given after ':' in parameter declaration beginning with ",
							startToken, "!");
				it.next(end);
			} // We can still see a default value even if we saw an annotation
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
	
	public static List<Parameter> parseParamList(TokenIterator it, int end) {
		List<Parameter> paramList = new ArrayList<>();
		int start = it.index;
		it.next(end); // assumed that the first token of '(' has already been checked
		// We need to find the end paren and verify that it is before this's end
		int close = it.find(Token.Type.CLOSE_PAREN, end);
		if (close == -1)
			throw new ParseException("Missing close paren to parameter list beginning with ",
					it.tokens.get(start), "!");
		while(true) {
			int nextComma = it.find(Token.Type.COMMA, close);
			int stop = (nextComma != -1? nextComma: close);
			
			boolean paramFound = false;
			if (it.match(Token.Type.IDENTIFIER, stop + 1)) {
				Parameter param = new Parameter();
				param.parse(it, stop);
				paramList.add(param);
				paramFound = true;
			}
			// After the identifier, we must see either the end or a comma
			if (paramFound && it.match(Token.Type.COMMA, stop + 1)) {
				it.next(close);
				// We are ready to parse another. We don't need to see another though,
				//  comma ended lists are acceptable.
				continue;
			}else if (it.index == close)
				break; // successfully found the end
			else
				throw new ParseException("Unexpected token \"", it.token(),
						"\" found in parameter list beginning with ", it.tokens.get(start), "!");
		}
		it.next(end);
		
		return paramList;
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer(name);
		if (annotation != null) {
			buf.append(": ");
			buf.append(annotation.pretty());
		}
		if (defaultVal != null) {
			buf.append(" = ");
			buf.append(defaultVal.pretty(indents));
		}
		return buf.toString();
	}

}
