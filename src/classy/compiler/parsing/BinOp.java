package classy.compiler.parsing;

import java.util.List;

import classy.compiler.lexing.Token;

public abstract class BinOp extends Operation {
	protected Value lhs;

	public BinOp(Value parent, Token.Type operation) {
		super(parent, operation);
	}
	
	@Override
	public boolean isLink() {
		return lhs == null || super.isLink();
	}
	
	@Override
	public void evaluateChain(int index, List<Subexpression> subs) {
		super.evaluateChain(index, subs);
		
		// Look back at the index before, which is lhs.
		// The index after is rhs.
		// If either say that they are linking, then we know we are missing values.
		if (index > 0) {
			Subexpression lhs = subs.get(index - 1);
			if (lhs.isLink())
				throw new ParseException("Missing value after ", lhs, " and before ", this, " in expression!");
			subs.remove(index - 1);
			this.lhs = new Value(null, lhs);
			index--;
		}else
			throw new ParseException("Missing value before ", this, " in expression!");
	}
	
	public Value getLHS() {
		return lhs;
	}
	
	public static class Addition extends BinOp {
		public Addition(Value parent) {
			super(parent, Token.Type.PLUS);
		}
		
		@Override
		public Float getPrecedence() {
			return 2f;
		}
		
		@Override
		protected String prettyOperation() {
			return "+";
		}
	}
	
	public static class Subtraction extends BinOp {
		public Subtraction(Value parent) {
			super(parent, Token.Type.MINUS);
		}
		
		@Override
		public Float getPrecedence() {
			return 2f;
		}

		@Override
		protected String prettyOperation() {
			return "-";
		}
	}
	
	public static class Multiplication extends BinOp {
		public Multiplication(Value parent) {
			super(parent, Token.Type.STAR);
		}
		
		@Override
		public Float getPrecedence() {
			return 1f;
		}

		@Override
		protected String prettyOperation() {
			return "*";
		}
	}
	
	public static class Division extends BinOp {
		public Division(Value parent) {
			super(parent, Token.Type.SLASH);
		}
		
		@Override
		public Float getPrecedence() {
			return 1f;
		}

		@Override
		protected String prettyOperation() {
			return "/";
		}
	}
	
	public static class Modulus extends BinOp {
		public Modulus(Value parent) {
			super(parent, Token.Type.PERCENT);
		}
		
		@Override
		public Float getPrecedence() {
			return 1f;
		}

		@Override
		protected String prettyOperation() {
			return "%";
		}
	}
	
	public static class Equal extends BinOp {
		public Equal(Value parent) {
			super(parent, Token.Type.EQUAL);
		}
		
		@Override
		public Float getPrecedence() {
			return 5f;
		}

		@Override
		protected String prettyOperation() {
			return "==";
		}
	}
	
	public static class NEqual extends BinOp {
		public NEqual(Value parent) {
			super(parent, Token.Type.NEQUAL);
		}
		
		@Override
		public Float getPrecedence() {
			return 5f;
		}

		@Override
		protected String prettyOperation() {
			return "<>";
		}
	}
	
	public static class LessThan extends BinOp {
		public LessThan(Value parent) {
			super(parent, Token.Type.LESS_THAN);
		}
		
		@Override
		public Float getPrecedence() {
			return 4f;
		}

		@Override
		protected String prettyOperation() {
			return "<";
		}
	}
	public static class LessEqual extends BinOp {
		public LessEqual(Value parent) {
			super(parent, Token.Type.LESS_EQUAL);
		}
		
		@Override
		public Float getPrecedence() {
			return 4f;
		}

		@Override
		protected String prettyOperation() {
			return "<=";
		}
	}
	
	public static class GreaterThan extends BinOp {
		public GreaterThan(Value parent) {
			super(parent, Token.Type.GREATER_THAN);
		}
		
		@Override
		public Float getPrecedence() {
			return 4f;
		}

		@Override
		protected String prettyOperation() {
			return ">";
		}
	}
	public static class GreaterEqual extends BinOp {
		public GreaterEqual(Value parent) {
			super(parent, Token.Type.GREATER_EQUAL);
		}
		
		@Override
		public Float getPrecedence() {
			return 4f;
		}

		@Override
		protected String prettyOperation() {
			return ">=";
		}
	}
	
	public static class And extends BinOp {
		public And(Value parent) {
			super(parent, Token.Type.AMPERSAND);
		}
		
		@Override
		public Float getPrecedence() {
			return 3f;
		}

		@Override
		protected String prettyOperation() {
			return "&";
		}
	}
	public static class Or extends BinOp {
		public Or(Value parent) {
			super(parent, Token.Type.BAR);
		}
		
		@Override
		public Float getPrecedence() {
			return 3f;
		}

		@Override
		protected String prettyOperation() {
			return "|";
		}
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer("(");
		buf.append(lhs.pretty(indents + 1));
		buf.append(' ');
		buf.append(prettyOperation());
		buf.append(' ');
		buf.append(rhs.pretty(indents + 1));
		buf.append(')');
		return buf.toString();
	}
	
	@Override
	protected String description() {
		return "binary operator";
	}

}
