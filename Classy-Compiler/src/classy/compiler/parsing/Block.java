package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.lexing.Token;

public class Block extends Expression implements NestingExpression {
	protected boolean impliedBounds = false;
	protected List<Expression> body = new ArrayList<>();
	
	public Block(NestingExpression parent) {
		super(parent);
	}
	
	public Block(NestingExpression parent, boolean impliedBounds) {
		super(parent);
		this.impliedBounds = impliedBounds;
	}

	@Override
	public void parse(TokenIterator it, int end) {
		// If the bounds are not implied (default), then we start with {, and
		// we will go until the first } in our level. 
		
		// If bounds are implied, then we do not need a starting open, and we
		// similarly go until the first } in our level and quit immediately
		// before it.
		int start;
		if (!impliedBounds) {
			boolean found = it.match(Token.Type.OPEN_BRACE, end);
			if (!found)
				throw new ParseException("Block without implied bounds must begin with '{'!");
			start = it.index;
			it.next(end);
		}else
			start = it.index;
		
		int stop = it.find(Token.Type.CLOSE_BRACE, end);
		if (stop == -1) {
			if (!impliedBounds)
				throw new ParseException("Missing close brace in block beginning with ",
						it.tokens.get(start), "!");
			stop = end;
		}
		body = Parser.parse(it, stop, this);
		// The next token should be the end. If the block did not have implied bounds, we should
		// find the close brace next
		if (!impliedBounds) {
			boolean endFound = it.match(Token.Type.CLOSE_BRACE, end);
			if (!endFound || it.index != stop)
				throw new ParseException("Unexpected token encountered at end of block beginning with ",
						it.tokens.get(start), "!");
			it.next(end); // get past the close brace
		}
		
		// Now that parsing for the block is done, we should be able to perform some analysis.
		// For example, there must be one and only one value in the block
		Value firstValue = null;
		for (Expression e: body) {
			if (e instanceof Value) {
				if (firstValue == null)
					firstValue = (Value)e;
				else
					throw new ParseException("Unexpected second value in block beginning with ",
							it.tokens.get(start), "! ", firstValue, " found first, then ", e,
							" encountered.");
			}
		}
	}
	
	public boolean reduce() {
		// If the body is only consisting of one element, and this is nested in
		// a value, then we can transfer the contents up to the parent and
		// dissolve this.
		if (body.size() == 1 && parent instanceof Value) {
			Value par = (Value)parent;
			List<Expression> sub = par.getSubexpressions();
			int found = sub.indexOf(this);
			if (found != -1) {
				sub.remove(found);
				sub.add(body.get(0));
				body.get(0).parent = par;
				return true;
			}
		}
		return false;
	}
	
	public List<Expression> getBody() {
		return body;
	}
	
	@Override
	public List<Expression> toCheck() {
		List<Expression> upper = super.toCheck();
		upper.addAll(body);
		return upper;
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer("{\n");
		for (Expression e: body) {
			buf.append(getIndents(indents + 1));
			buf.append(e.pretty(indents + 1));
			buf.append("\n");
		}
		buf.append(getIndents(indents));
		buf.append("}");
		return buf.toString();
	}

	@Override
	public List<Expression> getNested() {
		return body;
	}

}
