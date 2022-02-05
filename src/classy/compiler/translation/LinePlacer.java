package classy.compiler.translation;

import java.util.List;

public class LinePlacer {
	private List<String> outLines;
	private int indentation = 0;
	
	/* The location where new top-level data should be written.
	 * Should not be modified after set up. */
	private final int topLocation;
	/* The location where new lines will be written to. Saved as
	 * the number of lines from the bottom. */
	private int location = 0;
	
	
	public LinePlacer(List<String> startLines) {
		outLines = startLines;
		this.topLocation = startLines.size();
	}
	
	public void addLine(String... line) {
		outLines.add(outLines.size() - location, indent(line));
	}
	public void addLabel(String label) {
		indentation--;
		addLine(label, ":");
		indentation++;
	}
	
	protected String indent(String... line) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<indentation; i++)
			buf.append("  ");
		for (int i=0; i<line.length; i++)
			buf.append(line[i]);
		return buf.toString();
	}
	public void deltaIndent(int indent) {
		this.indentation += indent;
	}
	
	/**
	 * Sets this line placer to a location at the top
	 * and returns the old state. The old state should
	 * be restored after the work is completed at the
	 * top by using {@link #revertState(State)}.
	 * @return the state before placement at top scope
	 */
	public State getTop() {
		State old = new State(location, indentation);
		location = outLines.size() - topLocation;
		indentation = 0;
		return old;
	}
	/**
	 * Returns the line placer to the state specified.
	 * This can be used in conjunction with
	 * {@link #getTop()}.
	 * @param oldState the old state to return to
	 */
	public void revertState(State oldState) {
		location = oldState.location;
		indentation = oldState.indents;
	}
	
	/**
	 * Used to represent the state of a LinePlacer instance.
	 */
	public static class State {
		/** The location of where the next line should be placed */
		public final int location;
		/** The number of indents for the next line */
		public final int indents;
		
		/** Creates a new state, instantiating the given fields */
		public State(int location, int indents) {
			this.location = location;
			this.indents = indents;
		}
	}
	
	public List<String> getOutLines() {
		return outLines;
	}
}
