package classy.compiler.parsing;

import java.util.List;

import classy.compiler.lexing.Token;

public class Assignment extends Expression {
	protected Block parent;
	protected String varName;
	protected Value value;
	
	public Assignment(Block parent) {
		this.parent = parent;
	}

	@Override
	public void parse(TokenIterator it, int end) {
		// Syntax of: let varName = value
		if (!it.match(Token.Type.LET, end))
			throw new ParseException("Value definition must begin with \"let\" keyword! ", it.token(), 
					" found instead.");
		it.next(end);
		
		if (!it.match(Token.Type.IDENTIFIER, end))
			throw new ParseException("Value definition must have identifier following \"let\" keyword! ",
					it.token(), " found instead.");
		varName = it.token().getValue();
		it.next(end);
		
		if (!it.match(Token.Type.ASSIGN, end))
			throw new ParseException("Value definition must have '=' following the value name! ",
					it.token(), " found instead.");
		it.next(end);
		
		value = new Value();
		value.parse(it, end);
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer("let ");
		buf.append(varName);
		buf.append(" = ");
		buf.append(value.pretty(indents + 1));
		return buf.toString();
	}
	
	@Override
	public List<Expression> toCheck() {
		List<Expression> upper = super.toCheck();
		upper.add(value);
		return upper;
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

}
