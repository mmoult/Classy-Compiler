package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.lexing.Token;


public class Value extends Subexpression {
	protected List<Subexpression> subexpressions = new ArrayList<>();
	
	public Value() {
		super(null);
	}
	
	public Value(Value parent, Subexpression... subexps) {
		super(parent);
		for (Subexpression sub: subexps) {
			sub.parent = this;
			subexpressions.add(sub);
		}
	}

	@Override
	public void parse(TokenIterator it, int end) {
		// We can have many different subexpressions here.
		boolean endReady = false;
		
		while (it.index < end) {
			int start = it.index;
			Subexpression found;
			
			try {
				if (it.match(Token.Type.SEMICOLON, end) && !subexpressions.isEmpty())
					break; // value does not go over semicolons
			}catch(ParseException endFound) {
				break; // since we reached the end
			}
			
			if (it.match(Token.Type.OPEN_PAREN, end)) {
				// Seeing open and close parentheses can either be a parameter list or a nested value
				it.next(end);
				found = null;
				
				// We want to find the end
				int foundEnd;
				if (it.match(Token.Type.CLOSE_PAREN, end)) {
					found = new Tuple(this);
					foundEnd = it.index;
				}else {
					foundEnd = it.find(Token.Type.CLOSE_PAREN, end);
					// We can determine the type if the next token is a equals or if there is
					//  a comma before the end. Alternatively, if there are no tokens, then
					//  we have a void argument list
					
					if (it.find(Token.Type.COMMA, foundEnd) != -1)
						found = new Tuple(this);
					else {
						// Even if there are no commas, this may still be an argument list.
						// We can know if a default value is set. Since there are no commas,
						//  the default value must be set for the first parameter. If there
						//  is no default value, then we guess it is a regular value, which
						//  can be sorted out later during checking as needed.
						if (it.match(Token.Type.IDENTIFIER, foundEnd)) {
							int startAt = it.index;
							it.next(foundEnd);
							if (it.match(Token.Type.ASSIGN, foundEnd + 1))
								found = new Tuple(this);
							it.index = startAt;
						}
						if (found == null)
							found = new Value(this);
					}
				}
				
				found.parse(it, foundEnd);
				// We need to verify that the inner value ended at the expected close
				if (it.index != foundEnd)
					throw new ParseException("Unexpected token ", it.token(),
							" in value! Close paranthesis ')' expected instead.");
				
				it.next(end); // to get past the close
			}else if (it.match(Token.Type.COMMA, end)) {
				// We have an argument list here!
				// Put everything found thus far into a value as the first param to the list
				Tuple ls = new Tuple(this);
				Subexpression[] subs = {};
				Value val = new Value(null, subexpressions.toArray(subs));
				subexpressions.clear();
				ls.addArg(new Tuple.LabeledValue(val));
				it.next(end);
				ls.parse(it, end);
				subexpressions.add(ls);
				break; // list finishes the value, so we move on
			} else {
				try {
					found = typify(it, end);
				}catch(ParseException unknown) {
					// If the expression could not be typified, that means
					//  we are at the end of the value.
					if (!endReady)
						throw new ParseException("Unexpected token ", it.token(),
								" found where value expression expected!");
					break;
				}
				found.parse(it, end);
			}
			
			if (!found.isLink() && endReady) {
				// we don't want to add the found
				it.index = start;
				break;
			}else {
				subexpressions.add(found);
				
				if (!endReady)
					endReady = true;
				
				if (found instanceof Operation || found instanceof Reference)
					endReady = false;
			}
		}
	}
	
	protected Subexpression typify(TokenIterator it, int end) {
		it.match(Token.Type.PERIOD, end);
		switch(it.token().getType()) {
		case NUMBER:
		case TRUE:
		case FALSE:
			return new Literal(this);
		case OPEN_BRACE:
			return new Block(this, false);
		case IF:
			return new If(this);
		case IDENTIFIER:
		case SELF:
			return new Reference(this);
		case PLUS:
			return new BinOp.Addition(this);
		case MINUS:
			if (this.subexpressions.size() == 0)
				return new Operation.Negation(this);
			return new BinOp.Subtraction(this);
		case STAR:
			return new BinOp.Multiplication(this);
		case SLASH:
			return new BinOp.Division(this);
		case BANG:
			return new Operation.Not(this);
		case PERCENT:
			return new BinOp.Modulus(this);
		case EQUAL:
			return new BinOp.Equal(this);
		case NEQUAL:
			return new BinOp.NEqual(this);
		case LESS_THAN:
			return new BinOp.LessThan(this);
		case LESS_EQUAL:
			return new BinOp.LessEqual(this);
		case GREATER_THAN:
			return new BinOp.GreaterThan(this);
		case GREATER_EQUAL:
			return new BinOp.GreaterEqual(this);
		case VOID:
			return new Tuple(this);
		case AMPERSAND:
			return new BinOp.And(this);
		case BAR:
			return new BinOp.Or(this);
		default:
			throw new ParseException("Expression beginning with ", it.token(), " could not be typified!");
		}		
	}
	
	public List<Subexpression> getSubexpressions() {
		return subexpressions;
	}
	
	public Value clone() {
		Value cloned = new Value();
		cloned.parent = parent;
		for (Subexpression sub: subexpressions) {
			Subexpression sclone = sub.clone();
			sclone.parent = cloned;
			cloned.subexpressions.add(sclone);
		}
		return cloned;
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
	public boolean isLink() {
		return false;
	}
	
}
