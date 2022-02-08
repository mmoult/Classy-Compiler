package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

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
				
				if (found instanceof Block) {
					// If in the end, the block only had one expression, it should be
					// replaced with that expression, rather than using a block
					((Block)found).reduce();
				}else if (found instanceof Operation || found instanceof Reference)
					endReady = false;
			}
		}
		
		if (subexpressions.size() == 1)
			return; // If there was only one element, chaining is unneeded.
		
		// Lastly, we want to do a run through the subexpressions for chaining expressions
		//  to find their arguments.
		// We could not do this during parsing because of order of operations:
		//  eg "2 + 4 / 1" -> "2 + (4 / 1)", even though the plus is seen first.
		TreeSet<Float> precs = new TreeSet<>();
		for (int i = 0; i < subexpressions.size(); i++) {
			Float prec = subexpressions.get(i).getPrecedence();
			if (prec != null)
				precs.add(prec);
		}
		for (Float prec: precs) {
			boolean tryAgain;
			do {
				tryAgain = false;
				for (int i = 0; i < subexpressions.size(); i++) {
					Subexpression sub = subexpressions.get(i);
					if (!sub.isLink())
						continue;
					Float subPrec = sub.getPrecedence();
					if (subPrec == null)
						continue;
					if (sub.getPrecedence().equals(prec)) {
						sub.evaluateChain(i, subexpressions);
						tryAgain = true;
						break; // restart this precedence after the list has been modified
					}
				}
			}while(tryAgain);
		}
		
		// Similarly to how we reduce blocks, if there is only one element in the value,
		// we can collapse the values if they only have one subexpression.
		// We do this regardless of whether optimize is specified, since there is no use
		//  to having excessive parentheses. 
		for (int i = 0; i < subexpressions.size(); i++) {
			Subexpression sub = subexpressions.get(i);
			if (sub instanceof Value) {
				Value value = (Value)sub;
				if (value.getSubexpressions().size() == 1) {
					// replace value in the list with its expression
					subexpressions.remove(i);
					Subexpression inner = value.getSubexpressions().get(0);
					inner.parent = this;
					subexpressions.add(i, inner);
				}
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
			return new Void(this);
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
