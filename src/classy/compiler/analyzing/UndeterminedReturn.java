package classy.compiler.analyzing;

public class UndeterminedReturn extends Type {
	protected Variable from;

	public UndeterminedReturn(Variable from) {
		super("Undetermined");
		this.from = from;
	}

}
