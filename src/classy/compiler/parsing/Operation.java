package classy.compiler.parsing;

import java.util.List;

import classy.compiler.lexing.Token;

public abstract class Operation extends Subexpression {
	protected Token.Type operation;
	protected Value rhs;

	public Operation(Value parent, Token.Type operation) {
		super(parent);
		this.operation = operation;
	}
	
	@Override
	public void parse(TokenIterator it, int end) {
		// During the parse through, we just look for the operation. It is only later
		// on the second pass through that we can look for the arguments. We cannot
		// parse the arguments immediately since we have to obey order of operations.
		if (!it.match(operation, end))
			throw new ParseException("Unexpected token ", it.token(), " founnd where ",
					" \"", operation, "\" expected!");
		startToken = it.token();
		it.next(end);
	}

	@Override
	public boolean isLink() {
		return rhs == null;
	}
	
	public Value getRHS() {
		return rhs;
	}
	
	@Override
	public void evaluateChain(int index, List<Subexpression> subs) {
		if (index < subs.size() - 1) {
			Subexpression rhs = subs.get(index + 1);
			if (rhs.isLink())
				throw new ParseException("Missing value after ", this, " and before ", rhs, " in expression!");
			subs.remove(index + 1);
			this.rhs = new Value(null, rhs);
		}else
			throw new ParseException("Missing value after ", this, " in expression!");
	}
	
	public static class Not extends Operation {
		public Not(Value parent) {
			super(parent, Token.Type.BANG);
		}
		
		@Override
		public Float getPrecedence() {
			return 1f;
		}

		@Override
		protected String prettyOperation() {
			return "!";
		}
	}
	
	public static class Negation extends Operation {
		public Negation(Value parent) {
			super(parent, Token.Type.MINUS);
		}
		
		@Override
		public Float getPrecedence() {
			return 1f;
		}

		@Override
		protected String prettyOperation() {
			return "-";
		}
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer();
		if (!isLink())
			buf.append("(");
		buf.append(prettyOperation());
		if (rhs != null) {
			buf.append(' ');
			buf.append(rhs.pretty(indents + 1));			
		}
		if (!isLink())
			buf.append(')');
		return buf.toString();
	}
	
	protected abstract String prettyOperation();
	
	public Operation clone() {
		Operation outer = this;
		Operation cloned = new Operation(parent, this.operation) {
			@Override
			protected String prettyOperation() {
				return outer.prettyOperation();
			}
		};
		cloned.rhs = this.rhs.clone();
		
		return cloned;
	}

}
