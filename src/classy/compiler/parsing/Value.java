package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.lexing.Token;


public class Value extends Expression implements NestingExpression {
	protected List<Expression> subexpressions = new ArrayList<>();
	
	public Value(NestingExpression parent) {
		super(parent);
	}
	
	public Value(NestingExpression parent, Block subexpression) {
		super(parent);
		subexpressions.add(subexpression);
		subexpression.parent = this;
	}

	@Override
	public void parse(TokenIterator it, int end) {
		// We can have many different subexpressions here.
		
		// Right now, we don't have any way for subexpressions to chain
		// (such as accessing a field or calling a lambda), so the
		// subexpression list will have a single element
		
		boolean chain = false;
		do {
			Expression found = typify(it, end);
			found.parse(it, end);
			
			if (found instanceof Block) {
				// If in the end, this block only had one expression, it should be
				// replaced with that expression, rather than using a block
				Block b = (Block)found;
				if (b.body.size() == 1)
					found = b.body.get(0);
			}
			
			subexpressions.add(found);
		} while (chain);
	}
	
	protected Expression typify(TokenIterator it, int end) {
		if (it.match(Token.Type.NUMBER, end))
			return new Literal(this);
		if (it.match(Token.Type.OPEN_BRACE, end))
			return new Block(this);
		if (it.match(Token.Type.IF, end))
			return new If(this);
		if (it.match(Token.Type.IDENTIFIER, end))
			return new Reference(this);
		
		throw new ParseException("Expression beginning with ", it.token(), " could not be typified!");
	}
	
	public List<Expression> getSubexpressions() {
		return subexpressions;
	}
	
	@Override
	public List<Expression> toCheck() {
		List<Expression> upper = super.toCheck();
		upper.addAll(subexpressions);
		return upper;
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer();
		if (subexpressions.size() > 1)
			buf.append("(");
		boolean first = true;
		for (Expression e: subexpressions) {
			if (first)
				first = false;
			else
				buf.append(" ");
				
			buf.append(e.pretty(indents + 
					// If there is only 1 sub, we can hide the existence of this value
					((subexpressions.size() == 1)? 0: 1)));
		}
		if (subexpressions.size() > 1)
			buf.append(")");
		return buf.toString();
	}

	@Override
	public List<Expression> getNested() {
		return subexpressions;
	}
	
}
