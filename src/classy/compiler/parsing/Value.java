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
				// We want to create a new value from the open to the close
				it.next(end);
				int foundEnd = it.find(Token.Type.CLOSE_PAREN, end);
				Value val = new Value(this);
				val.parse(it, foundEnd);
				// We need to verify that the inner value ended at the expected close
				if (it.index != foundEnd)
					throw new ParseException("Unexpected token ", it.token(),
							" in value! Close paranthesis ')' expected instead.");
				
				found = val;
				it.next(end); // to get past the close
			}else if (it.match(Token.Type.COMMA, end)) {
				// We have an argument list here!
				// Put everything found thus far into a value as the first param to the list
				ArgumentList ls = new ArgumentList(this);
				Subexpression[] subs = {};
				Value val = new Value(null, subexpressions.toArray(subs));
				subexpressions.clear();
				ls.addArg(val);
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
			return new Literal(this);
		case OPEN_BRACE:
			return new Block(this, false);
		case IF:
			return new If(this);
		case IDENTIFIER:
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
			return new ArgumentList(this);
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
