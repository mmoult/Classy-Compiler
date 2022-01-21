package classy.compiler.analyzing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import classy.compiler.CompileException;
import classy.compiler.parsing.Assignment;
import classy.compiler.parsing.Block;
import classy.compiler.parsing.Expression;
import classy.compiler.parsing.NestingExpression;
import classy.compiler.parsing.Reference;
import classy.compiler.parsing.Value;

public class Checker {
	protected List<Variable> variables = new ArrayList<>();
	
	public Checker(Value program, boolean optimize) {
		check(program, optimize);
	}
	
	protected void check(Expression e, List<Map<String, Variable>> env) {
		if (e instanceof Assignment) {
			Assignment asgn = (Assignment)e;
			// There cannot be two variables with the same name in the same scope
			Map<String, Variable> curScope = env.get(env.size() - 1);
			String name = asgn.getVarName();
			if (curScope.containsKey(name)) {
				Expression firstInstance = curScope.get(name).source;
				throw new CheckException("Multiple definitions of same variable name \"",
						name, " found in the same scope! First instance: ", firstInstance,
						" and second instance: ", asgn);
			}
			Variable var = new Variable(name, asgn.getValue(), asgn);
			variables.add(var);
			curScope.put(name, var);
		}if (e instanceof Reference) {
			// We must find some variable that matches the name given
			Reference ref = (Reference)e;
			String name = ref.getVarName();
			
			Variable referenced = null;
			for(int i=env.size()-1; i>=0; i--) {
				Map<String, Variable> search = env.get(i);
				if (search.containsKey(name))
					referenced = search.get(name);
			}
			if (referenced == null)
				throw new CheckException("Reference ", ref, " to an undeclared variable \"", name, "\"!");
			
			referenced.addRef(ref);
		}
		
		// now we want to process the children of this expression
		// Since the subexpressions can change for block reduction, we don't want the actual
		//  list or else we may run into concurrent access. 
		List<Expression> nested = e.toCheck();
		List<Expression> toCheck = new ArrayList<>(nested.size());
		toCheck.addAll(nested);
		// we should handle these sequentially
		
		// If this expression is a block, then we start a new scope
		boolean scoped = e instanceof Block;
		if (scoped) {
			env.add(new TreeMap<>());
			// remember to remove the scope after the children were processed
		}
		for (Expression child: toCheck) {
			check(child, env);
		}
		if (scoped) {
			env.remove(env.size() - 1);
			// If all in the body passed the check, then we can try to reduce
			((Block)e).reduce();
		}
	}
	
	public void check(Value program, boolean optimize) {
		// We need to check variable references and reuse:
		//  There cannot be two variables with the same name in the same scope.
		//  If there is only one usage of a variable, it can be replaced with the value.
		
		List<Map<String, Variable>> environment = new ArrayList<>();
		environment.add(new TreeMap<String, Variable>());
		check(program, environment);
		
		if (!optimize)
			return;
		
		List<Variable> removeList = new ArrayList<>();
		for (Variable var: variables) {
			if (var.references.isEmpty())
				removeList.add(var);
			else if (var.references.size() == 1) {
				// Replace the reference with the value
				Expression ref = var.references.get(0);
				List<Expression> subList = ref.getParent().getNested();
				int replaceAt = subList.indexOf(ref);
				subList.remove(replaceAt);
				subList.add(replaceAt, var.value);
				
				// get rid of the assignment for an unused variable
				removeList.add(var);
			}
		}
		for (Variable var: removeList) {
			// We need to delete it at its source first
			NestingExpression parent = var.source.getParent();
			parent.getNested().remove(var.source);
			if (parent instanceof Block)
				((Block)parent).reduce();
			// then we can remove it from the variables list to complete the change
			variables.remove(var);
		}
	}
	
	public List<Variable> getVariables() {
		return variables;
	}
	
	public static class CheckException extends CompileException {
		private static final long serialVersionUID = 1L;

		public CheckException(Object... msg) {
			super(msg);
		}
		
		public CheckException(Throwable cause) {
			super(cause);
		}
		
		public CheckException(Throwable cause, Object... msg) {
			super(cause, msg);
		}
		
	}

}
