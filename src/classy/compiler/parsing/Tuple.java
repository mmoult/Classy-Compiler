package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.lexing.Token;

public class Tuple extends Subexpression {
	protected List<LabeledValue> args = new ArrayList<>();

	public Tuple(Value parent) {
		super(parent);
	}
	
	public void addArg(LabeledValue val) {
		args.add(val);
	}
	
	public List<LabeledValue> getArgs() {
		return args;
	}

	@Override
	public boolean isLink() {
		return false;
	}

	@Override
	public void parse(TokenIterator it, int end) {
		if (it.index == end) {
			startToken = it.tokens.get(it.index - 1);
			return; // Void argument list
		}
		
		startToken = it.token();
		if (it.match(Token.Type.VOID, end)) {
			it.next(end);
			return;
		}
		// I think that this is where we need to look for more arguments
		// Comma is not an operator the same way as + or -. It is outside
		// precedence, since in a way, it is ordered absolutely last
		
		// Parse a value between here and the next comma
		// Continue while there is a comma immediately after the next value
		boolean commaFound = true;
		while (commaFound) {
			commaFound = false;
			int comma = it.find(Token.Type.COMMA, end);
			if (comma == -1)
				comma = end;
			LabeledValue arg = new LabeledValue();
			arg.parse(it, comma);
			addArg(arg); // add it to the list
			
			// If the next token is a comma, and it is the comma found earlier,
			// then we continue the list
			if (comma < end && it.match(Token.Type.COMMA, end) && it.index == comma) {
				commaFound = true;
				it.next(end);
			}
		}
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer("(");
		boolean first = true;
		for (Value arg: args) {
			if (first)
				first = false;
			else
				buf.append(", ");
			buf.append(arg.pretty(indents));
		}
		buf.append(")");
		
		return buf.toString();
	}
	
	
	public static class LabeledValue extends Value {
		protected String label = null;
		
		public LabeledValue() {
			super();
		}
		
		public LabeledValue(Value val) {
			this.subexpressions = val.subexpressions;
			this.parent = val.parent;
		}
		public LabeledValue(Value val, String label) {
			this(val); // use the other constructor for copying
			this.label = label;
		}
		
		@Override
		public void parse(TokenIterator it, int end) {
			// Look for a label, which is an identifier, then a '='
			int startIndex = it.index;
			try {
				if (it.match(Token.Type.IDENTIFIER, end)) {
					String maybeLabel = it.token().getValue();
					it.next(end);
					if (it.match(Token.Type.ASSIGN, end)) {
						// We found a label!
						this.label = maybeLabel;
						it.next(end);
					}
				}
			}catch(ParseException ignore) {
				// There is no label if we went out of bounds
			}
			// No label was found, so revert back to starting index
			if (label == null)
				it.index = startIndex;
			
			// Regardless of whether there is a label, we want to find the value
			//  that is being labeled.
			super.parse(it, end);
		}
		
		public void setLabel(String label) {
			this.label = label;
		}
		public String getLabel() {
			return label;
		}
		
		public LabeledValue clone() {
			Value cloned = super.clone();
			LabeledValue ret = new LabeledValue(cloned, label);
			return ret;
		}
		
		@Override
		public String pretty(int indents) {
			StringBuffer buf = new StringBuffer();
			if (label != null) {
				buf.append(label);
				buf.append("=");
			}
			buf.append(super.pretty(indents));
			return buf.toString();
		}
	}

	@Override
	public Tuple clone() {
		Tuple cloned = new Tuple(parent);
		for (LabeledValue arg: args)
			cloned.args.add(arg.clone());
		return null;
	}

}
