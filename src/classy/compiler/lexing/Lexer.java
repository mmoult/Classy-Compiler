package classy.compiler.lexing;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
	protected List<Token> tokens = new ArrayList<>();
	
	public Lexer(List<String> lines) {
		lex(lines);
	}
	
	protected void lex(List<String> lines) {
		Processor selected = null;
		Processor[] available = getAllProcessors();
		// We can keep track of level (as determined by parentheses) If level > 0, then
		//  we know not to add a newline if we come to the end of a line (since it is mid-expression)
		int level = 0;
		
		for(int i=0; i<lines.size(); i++) {
			int lineNo = i + 1;
			String line = lines.get(i) + '\n';
			
			int lineLen = line.length();
			int colNo = 1;
			// Feed the line to the processor. As soon as one processor will not take more,
			//  we choose another processor and keep going. 
			while (line.length() > 1) {
				if (selected == null) {
					// Try to find a new processor.
					// Cycle through until we find a processor that will eat at least one character
					for (Processor p: available) {
						try {
							String temp = p.add(line);
							if (!temp.equals(line)) {
								colNo += (lineLen - temp.length());
								line = temp; // update the line
								lineLen = line.length();
								
								// If any characters were used, then this was a valid processor
								selected = p;
								break;
							}
						}catch(LexException le) {
							throw new LexException(le, "Lexing error on line " +
									Integer.toString(lineNo) + " and column " + Integer.toString(colNo) + "!");
						}
					}
					if (selected == null) {
						// If we made it through the entire processor list without finding anything
						// suitable, then we cannot tokenize what is next in the line
						throw new LexException("Unexpected token \"", line.substring(0, line.length()-1),
								"\" on line ", Integer.toString(lineNo), " and column ",
								Integer.toString(colNo), "!");
					}
				}else {
					String temp = selected.add(line);
					colNo += (lineLen - temp.length());
					line = temp; // update the line
					lineLen = line.length();
				}
				
				// If there is still more data to process, then extract what we got
				if (line.length() > 0) {
					Token got = new Token(selected.getValue(), selected.getType(), lineNo, colNo);
					if (got.getType() == Token.Type.OPEN_PAREN)
						level++;
					else if (got.getType() == Token.Type.CLOSE_PAREN) {
						level--;
						if (level < 0)
							throw new LexException("Mismatch parentheses error! Cannot see ",
									got, " before matching opening.");
					}
					tokens.add(got);
					selected.clear();
					selected = null;
				} // else- If the processor ate all the characters, then it needs more
			}
			
			// If the line is only \n, then we should add a corresponding new line token
			if (line.equals("\n") && level == 0)
				tokens.add(new Token("\\n", Token.Type.NEW_LINE, lineNo, colNo));
		}
		if (selected != null) {
			// If selected is not null, then it is still expecting more characters. This is problematic,
			//  since the character stream has ended.
			throw new LexException("Unexpected end found while processing ", selected.description(), "!");
		}
	}
	
	protected Processor[] getAllProcessors() {
		return new Processor[] {
			new WhitespaceProcessor(), new CommentProcessor(), new NumberProcessor(),
			new IdentifierProcessor(), new SimpleProcessor()
		};
	}
	
	public List<Token> getTokens() {
		return tokens;
	}
	
	public static abstract class Processor {
		protected String soFar = "";
		
		public abstract String add(String check);
		
		public abstract Token.Type getType();
		
		public abstract String description();
		
		public String getValue() {
			return soFar;
		}
		
		public void clear() {
			soFar = "";
		}
		
	}

}
