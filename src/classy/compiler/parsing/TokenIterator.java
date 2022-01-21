package classy.compiler.parsing;

import java.util.List;

import classy.compiler.lexing.Token;

public class TokenIterator {
	protected List<Token> tokens;
	protected int index;
	
	public TokenIterator(List<Token> tokens) {
		this.tokens = tokens;
		index = 0;
	}
	public TokenIterator(List<Token> tokens, int index) {
		this.tokens = tokens;
		this.index = index;
	}
	
	/**
	 * Attempts to match the first token in "got" at "index" to the token type
	 * expected. Excess semicolons and new lines will be disregarded.
	 * 
	 * @param tokens   the tokens to search through
	 * @param index    the index in "got" to begin the search
	 * @param end      the exclusive upper bound of index
	 * @param expected the token type that needs to be found. Semicolons and new
	 *                 lines are treated interchangeably in the comparison.
	 * @return whether the expected token was found next
	 */
	public boolean match(Token.Type expected, int end) {
		while (index < end) {
			Token.Type curr = token().getType();
			
			// If this type is what we were expecting, match on it
			if (curr == expected)
				return true;
			else if (!Token.isNonSyntaxType(curr))
				// If it is not non-syntax, meaning it is relevant, there is no match
				return false;
			
			index++;
			if (index >= end) {
				String endType;
				if (index < tokens.size())
					endType = token().toString();
				else
					endType = "token list";
				throw new ParseException("Unexpected end of ", endType, " found when matching for ",
						expected, "!");
			}
		}
		throw new ParseException("Unexpected end found when matching for ", expected, "!");
	}
	
	/**
	 * Finds the first occurrence of the expected token type in the same scope.
	 * Begins searching in "tokens" from index "start".
	 * 
	 * @param expected the token type to be found. An expected semicolon is the
	 * same as a call to {@link #findBreak(List, int)}.
	 * @return the index if found, -1 if not.
	 */
	public int find(Token.Type expected, int end) {
		int scope = 0;

		for (int i= index; i < end; i++) {
			Token token = tokens.get(i);
			Token.Type type = token.getType();
			
			if (scope == 0 && type == expected)
				return i;

			else if (type == Token.Type.CLOSE_BRACE || type == Token.Type.CLOSE_PAREN) {
				if (scope > 0)
					scope--;
				else {
					// end of scope forces a break
					if (expected == Token.Type.SEMICOLON)
						return i;
					return -1;
				}
			}

			else if (type == Token.Type.OPEN_BRACE || type == Token.Type.OPEN_PAREN) 
				scope++;
		}

		// if we exit out of the loop, the expected token was not found!
		return -1;
	}
	
	/**
	 * Finds the first natural break in the token list beginning with the index
	 * provided as start.
	 * <p>
	 * It <i>cannot</i> be assumed that the token at the returned index is a
	 * semicolon. A break can also be found when the scope ends, and in such a
	 * case, the token at the returned index is a closing parenthesis, bracket,
	 * or brace.
	 * 
	 * @param end	the upper limit (exclusive) of the tokens to be analyzed
	 * @return the index of the first break found, or -1 if none
	 */
	public int findBreak(int end) {
		return find(Token.Type.SEMICOLON, end);
	}
	
	/**
	 * Gets the current index of the iterator.
	 * @return {@link #index}
	 */
	public int getIndex() {
		return index;
	}
	
	/**
	 * Sets the current index of the iterator.
	 * @param index overwrites {@link #index}
	 */
	public void setIndex(int index) {
		if (index < 0)
			throw new ParseException("Index cannot be set before the start of the list! Requested index ",
					Integer.toString(index), ".");
		else if (index > tokens.size())
			throw new ParseException("Index cannot be set after the end of the list! Requested index ",
					Integer.toString(index), ".");
		this.index = index;
	}
	
	public Token token() {
		return tokens.get(index);
	}
	
	public void next(int end) {
		index++;
		if (index > end) {
			String endType;
			if (index < tokens.size())
				endType = "token " + token().toString();
			else
				endType = "of token list";
			throw new ParseException("Unexpected end ", endType, " found when pattern matching!");
		}
	}

}
