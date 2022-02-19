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
		
		// Now there are several options for what we can see. If we see
		// - an "if" token, then there is another branch to this if construct
		// - an "else" token, then we have an explicit block for the else value
		// - other, in which case, we have an implicit block
		if (it.match(Token.Type.IF, end)) {
			// This functions very similarly to the else case, but we can
			//  shortcut typification and the unnecessary block.
			If elseCondition = new If(null);
			elseCondition.parse(it, end);
			else_ = new Value(null, elseCondition);
		}else if (it.match(Token.Type.ELSE, end)) {
			it.next(end);
			else_ = new Value();
			else_.parse(it, end);
		} else {
			Block implicit = new Block(null, true);
			implicit.parse(it, end);
			else_ = new Value(null, implicit);
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
		buf.append(getIndents(indents));
		int nIndents = indents;
		// Pretty print option for an else-if branch or implicit else
		if (else_.subexpressions.size() == 1 && (else_.subexpressions.get(0) instanceof If ||
				(else_.subexpressions.get(0) instanceof Block &&
						((Block)else_.subexpressions.get(0)).impliedBounds))) {
			buf.append(else_.pretty(indents));
		}else {
			buf.append("else\n");
			buf.append(getIndents(nIndents + 1));
			buf.append(else_.pretty(nIndents + 1));
		}
		return buf.toString();
	}

	@Override
	public boolean isLink() {
		return false;
	}
	
	public If clone() {
		If cloned = new If(parent);
		cloned.condition = condition.clone();
		cloned.then = then.clone();
		cloned.else_ = else_.clone();
		return cloned;
	}

}
