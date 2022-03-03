package classy.compiler.analyzing;

public abstract class Undetermined extends Type {
	protected Variable from;
	
	public Undetermined(Variable from) {
		super("Undetermined");
		this.from = from;
	}
	
	public abstract Type coerce(Type expected);
	
	public static class Return extends Undetermined {
		public Return(Variable variable) {
			super(variable);
		}
		
		@Override
		public Type coerce(Type expected) {
			from.getType().output = expected; // determine the output
			return expected;
		}
	}
	
	public static class Param extends Undetermined {
		public Param(Variable variable) {
			super(variable);
		}
		
		@Override
		public Type coerce(Type expected) {
			from.setType(expected); // determine the type
			return expected;
		}
	}
	

}
