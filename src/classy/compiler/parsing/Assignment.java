package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.lexing.Token;

public class Assignment extends NameBinding {
	protected Block parent;
	protected String varName;
	protected List<Parameter> paramList = null;
	protected Value value;
	
	public Assignment(Block parent) {
		this.parent = parent;
	}

	@Override
	public void parse(TokenIterator it, int end) {
		// Syntax of: let varName = value
		// For a function definition: let varName (param1, ...) = value
		if (!it.match(Token.Type.LET, end))
			throw new ParseException("Definition must begin with \"let\" keyword! ", it.token(), 
					" found instead.");
		int assignmentStart = it.index;
		it.next(end);
		
		if (!it.match(Token.Type.IDENTIFIER, end))
			throw new ParseException("Definition must have identifier following \"let\" keyword! ",
					it.token(), " found instead.");
		varName = it.token().getValue();
		it.next(end);
		
		// Now here is the interesting part. We can see an open paren, in which case, we know that
		// this is a function definition.
		if (it.match(Token.Type.OPEN_PAREN, end)) {
			// Parse out the param list
			paramList = new ArrayList<>();
			int start = it.index;
			// We do not have any support for type annotations yet, so we just look for identifiers
			it.next(end);
			// We need to find the end paren and verify that it is before this's end
			int close = it.find(Token.Type.CLOSE_PAREN, end);
			if (close == -1)
				throw new ParseException("Missing close paren to parameter list beginning with ",
						it.tokens.get(start), "!");
			while(true) {
				int nextComma = it.find(Token.Type.COMMA, close);
				int stop = (nextComma != -1? nextComma: close) + 1;
				if (it.match(Token.Type.IDENTIFIER, stop)) {
					Parameter param = new Parameter();
					param.parse(it, end);
					this.paramList.add(param);
					// After the identifier, we must see either the end or a comma
					if (it.match(Token.Type.COMMA, stop)) {
						it.next(close);
						// We are ready to parse another. We don't need to see another though,
						//  comma ended lists are acceptable.
						continue;
					}
				}else if (it.index == close)
					break; // successfully found the end
				else
					throw new ParseException("Unexpected token \"", it.token(),
							"\" found in parameter list beginning with ", it.tokens.get(start), "!");
			}
			it.next(end);
		}
		
		if (!it.match(Token.Type.ASSIGN, end))
			throw new ParseException("Definition of \"", varName, "\" must have '=' following the value name! ",
					it.token(), " found instead.");
		it.next(end);
		
		try {
			// We want to match to the next token (does not matter which)
			// This allows us to put the value on a different line than the =
			it.match(Token.Type.PERIOD, end);
		}catch(ParseException pe) {
			// If we reach an end, then the value is missing
			throw new ParseException("Missing value in assignment of \"", varName, "\" beginning with ",
					it.tokens.get(assignmentStart), ".");
		}
		value = new Value();
		value.parse(it, end);
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer("let ");
		buf.append(varName);
		if (paramList != null) {
			buf.append('(');
			boolean first = true;
			for (Parameter param: paramList) {
				if (first)
					first = false;
				else
					buf.append(", ");
				buf.append(param.pretty(indents));
			}
			buf.append(')');
		}
		buf.append(" = ");
		buf.append(value.pretty(indents+1));
		return buf.toString();
	}
	
	public String getVarName() {
		return varName;
	}
	
	public Value getValue() {
		return value;
	}
	
	public Block getParent() {
		return parent;
	}
	
	public List<Parameter> getParamList() {
		return paramList;
	}

}
