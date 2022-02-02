package classy.compiler.parsing;

import classy.compiler.lexing.Token;

public class If extends Subexpression {
	protected Value condition;
	protected Value then;
	protected Value else_;
	
	public If(Value parent) {
		super(parent);
	}

	@Override
	public void parse(TokenIterator it, int end) {
		if (!it.match(Token.Type.IF, end))
			throw new ParseException("\"if\" token must be the first in an if statement! ", it.token(),
					" found instead.");
		//int start = it.index;
		it.next(end);
		
		// First we must encounter the condition. This condition is a value
		condition = new Value();
		condition.parse(it, end);
		
		// Now we will encounter two values. The first is the then condition.
		then = new Value();
		then.parse(it, end);
		
		// Now we can either see an "else" token, in which case a following block
		// must have an explicit bound, or we can see no else and an implicit block
		if (it.match(Token.Type.ELSE, end)) {
			it.next(end);
			Block implicit = new Block(null, true);
			implicit.parse(it, end);
			else_ = new Value(null, implicit);
		} else {
			else_ = new Value();
			else_.parse(it, end);
		}
	}
	
	public Value getCondition() {
		return condition;
	}
	public Value getThen() {
		return then;
	}
	public Value getElse() {
		return else_;
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer("if ");
		buf.append(condition.pretty(indents));
		buf.append("\n");
		buf.append(getIndents(indents + 1));
		buf.append(then.pretty(indents + 1));
		buf.append("\n");
		buf.append(getIndents(indents + 1));
		buf.append("else ");
		buf.append(else_.pretty(indents + 1));
		return buf.toString();
	}

	@Override
	public boolean isLink() {
		return false;
	}

}
