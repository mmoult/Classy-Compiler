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
				found = new Value(this);
				found.parse(it, foundEnd);
				// We need to verify that the inner value ended at the expected close
				if (it.index != foundEnd)
					throw new ParseException("Unexpected token ", it.token(),
							" in value! Close paranthesis ')' expected instead.");
				it.next(end); // to get past the close
			}else {
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
				}else if (found instanceof Operation)
					endReady = false;
			}
		}
		
		// Similarly to how we reduce blocks, if this value only has one child, which is
		//  a value, it can be reduced.
		if (subexpressions.size() == 1) {
			Subexpression first = subexpressions.get(0);
			if (first instanceof Value) {
				Value val = (Value)first;
				subexpressions = val.subexpressions;
			}
			
			return; // If there was only one element, chaining is unneeded.
		}
		
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
	}
	
	protected Subexpression typify(TokenIterator it, int end) {
		if (it.match(Token.Type.NUMBER, end))
			return new Literal(this);
		if (it.match(Token.Type.OPEN_BRACE, end))
			return new Block(this, false);
		if (it.match(Token.Type.IF, end))
			return new If(this);
		if (it.match(Token.Type.IDENTIFIER, end))
			return new Reference(this);
		if (it.match(Token.Type.PLUS, end))
			return new BinOp.Addition(this);
		if (it.match(Token.Type.MINUS, end)) {
			if (this.subexpressions.size() == 0)
				return new Operation.Negation(this);
			return new BinOp.Subtraction(this);			
		}if (it.match(Token.Type.STAR, end))
			return new BinOp.Multiplication(this);
		if (it.match(Token.Type.SLASH, end))
			return new BinOp.Division(this);
		if (it.match(Token.Type.BANG, end))
			return new Operation.Not(this);
		if (it.match(Token.Type.PERCENT, end))
			return new BinOp.Modulus(this);
		if (it.match(Token.Type.EQUAL, end))
			return new BinOp.Equal(this);
		if (it.match(Token.Type.NEQUAL, end))
			return new BinOp.NEqual(this);
		if (it.match(Token.Type.LESS_THAN, end))
			return new BinOp.LessThan(this);
		if (it.match(Token.Type.LESS_EQUAL, end))
			return new BinOp.LessEqual(this);
		if (it.match(Token.Type.GREATER_THAN, end))
			return new BinOp.GreaterThan(this);
		if (it.match(Token.Type.GREATER_EQUAL, end))
			return new BinOp.GreaterEqual(this);
		
		throw new ParseException("Expression beginning with ", it.token(), " could not be typified!");
	}
	
	public List<Subexpression> getSubexpressions() {
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
	public boolean isLink() {
		return false;
	}
	
}
