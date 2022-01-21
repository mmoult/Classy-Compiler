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
		
		String accum = "";
		for(int i=0; i<lines.size(); i++) {
			int lineNo = i + 1;
			String line = lines.get(i);
			
			for(int j=0; j<line.length(); j++) {
				int colNo = j + 1;
				char c = line.charAt(j);
				accum += c;
				
				// try to let the selected processor handle it
				if (selected != null) {
					if (selected.add(accum))
						// Great!, that means that we can clear the accumulation
						accum = ""; // we only want to process the new input each time
					else {
						// the processor is no longer valid. That means we want to dispose it
						// But first, we are going to save the token made
						try {
							tokens.add(new Token(selected.getValue(), selected.getType(), lineNo, colNo));
							selected.clear();
							selected = null;
						}catch(LexException le) {
							throw new LexException(le, "Lexing error on line " +
									Integer.toString(lineNo) + " and column " + Integer.toString(colNo) + "!");
						}
					}
				}if (selected == null) {
					// We need to get a new processor
					for (Processor p: available) {
						try {
							if (p.add(accum)) {
								selected = p;
								break;
							}
						}catch(LexException le) {
							// Do nothing actually. We were just testing to see if it would work
						}
					}
					if (selected != null)
						accum = "";
					// If we do not find a processor, then we continue and hope for
					// acceptance later. 
				}
			}
			
			// Perform line termination operations
			if (selected != null) {
				// We need to find whether the handler is terminated at end of line
				if (selected.lineTerminated()) {
					tokens.add(new Token(selected.getValue(), selected.getType(), lineNo, line.length()-1));
					selected.clear();
					selected = null;
					
					tokens.add(new Token("\\n", Token.Type.NEW_LINE, lineNo, line.length()));
				}
			}else if (!accum.isEmpty()) {
				throw new LexException("Unrecognized symbols \"", accum, "\" on line ", Integer.toString(lineNo), "!");
			}
		}
	}
	
	protected Processor[] getAllProcessors() {
		return new Processor[] {
			new SingletonProcessor(), new CommentProcessor(), new IdentifierProcessor(),
			new WhitespaceProcessor(), new NumberProcessor(),
		};
	}
	
	public List<Token> getTokens() {
		return tokens;
	}
	
	public static abstract class Processor {
		protected String soFar = "";
		
		public boolean add(String check) {
			if(check.isEmpty())
				return false;
			boolean valid = isValid(check);
			if (valid)
				soFar += check;
			return valid;
		}
		
		protected abstract boolean isValid(String check);
		
		public abstract boolean lineTerminated();
		
		public abstract Token.Type getType();
		
		public String getValue() {
			return soFar;
		}
		
		public void clear() {
			soFar = "";
		}
	}

}
