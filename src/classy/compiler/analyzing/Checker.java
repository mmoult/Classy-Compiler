package classy.compiler.analyzing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import classy.compiler.parsing.ArgumentList;
import classy.compiler.parsing.Assignment;
import classy.compiler.parsing.BinOp;
import classy.compiler.parsing.Block;
import classy.compiler.parsing.Expression;
import classy.compiler.parsing.If;
import classy.compiler.parsing.Operation;
import classy.compiler.parsing.Reference;
import classy.compiler.parsing.Subexpression;
import classy.compiler.parsing.Value;

public class Checker {
	protected List<Variable> variables = new ArrayList<>();
	protected Set<Reference> checkedRef = new HashSet<>();
	
	public Checker(Value program, boolean optimize) {
		check(program, optimize);
	}
	
	public void check(Value program, boolean optimize) {
		// We need to check variable references and reuse:
		//  There cannot be two variables with the same name in the same scope.
		//  If there is only one usage of a variable, it can be replaced with the value.
		
		List<Map<String, Variable>> environment = new ArrayList<>();
		environment.add(new TreeMap<String, Variable>());
		check(program, environment, optimize);
		
		if (optimize) {
			Optimizer opt = new Optimizer();
			opt.optimize(variables, program);
		}
	}
	
	protected void check(Expression e, List<Map<String, Variable>> env, boolean optimize) {
		// dispatch on the appropriate checking function
		if (e instanceof Assignment)
			check((Assignment)e, env, optimize);
		if (e instanceof Block)
			check((Block)e, env, optimize);
		if (e instanceof If)
			check((If)e, env, optimize);
		if (e instanceof Operation)
			check((Operation)e, env, optimize);
		if (e instanceof Value)
			check((Value)e, env, optimize);
		if (e instanceof ArgumentList)
			check((ArgumentList)e, env, optimize);
	}
	
	public List<Variable> getVariables() {
		return variables;
	}
	
	protected void check(Assignment asgn, List<Map<String, Variable>> env, boolean optimize) {
		// There cannot be two variables with the same name in the same scope
		// TODO we will need to modify this for different signatures...
		Map<String, Variable> curScope = env.get(env.size() - 1);
		String name = asgn.getVarName();
		if (curScope.containsKey(name)) {
			Expression firstInstance = curScope.get(name).source;
			throw new CheckException("Multiple definitions of same variable name \"",
					name, " found in the same scope! First instance: ", firstInstance,
					" and second instance: ", asgn);
		}
		
		// If the parameter list is null, then this is simply a variable. Otherwise, this is
		// a function definition. 
		// Function definitions are special since the value can have references to itself.
		// Also, it needs to add the parameters in a scope that contains only the value
		if (asgn.getParamList() != null) {
			Variable var = new Variable(name, asgn.getValue(), asgn);
			variables.add(var);
			curScope.put(name, var);
			env.add(new TreeMap<>());
			Map<String, Variable> fxScope = env.get(env.size() - 1);
			// We cannot optimize away parameters, and thus they have null parent
			for (String param: asgn.getParamList()) {
				Variable paramVar = new Variable(param, null, null);
				variables.add(paramVar);
				fxScope.put(param, paramVar);
			}
			// Now that the function scope is made, use it for the value
			check(asgn.getValue(), env, optimize);
			env.remove(env.size() - 1);
		}else {
			check(asgn.getValue(), env, optimize);
			
			Variable var = new Variable(name, asgn.getValue(), asgn);
			variables.add(var);
			curScope.put(name, var);
		}
	}
	
	protected void check(Reference ref, List<Map<String, Variable>> env, boolean optimize) {
		if (checkedRef.contains(ref))
			return; // do not re-check a reference
		checkedRef.add(ref);
		
		String name = ref.getVarName();
		// We must find some variable that matches the name given
		Variable referenced = null;
		for(int i=env.size()-1; i>=0; i--) {
			Map<String, Variable> search = env.get(i);
			if (search.containsKey(name)) {
				referenced = search.get(name);
				break;
			}
		}
		if (referenced == null)
			throw new CheckException("Reference ", ref, " to an undeclared variable \"", name, "\"!");
		
		referenced.addRef(ref);
		ref.setLinkedTo(referenced.getSource());
		
		// We will know if there are necessary arguments based on whether the assignment was a function
		if (referenced.getSource() != null) {
			Assignment source = referenced.getSource();
			if (source.getParamList() != null) {
				// We need to connect the next token to the ref
				List<Subexpression> subexp = ref.getParent().getSubexpressions();
				int refAt = subexp.indexOf(ref);
				if (refAt == -1 || refAt + 1 >= subexp.size())
					throw new CheckException("Missing argument list for function call \"", ref, "\"!");
				Subexpression argLs = subexp.remove(refAt + 1);
				
				// We want to type check on the number of arguments the function needs
				if (source.getParamList().size() != 1) {
					// If there is more than 1 parameter, then we need an argument list
					// Often it can be nested in another value, however, so we may have
					//  to extract it out
					if (argLs instanceof Value) {
						Value foundVal = (Value)argLs;
						// If there is only one subexpression, we can safely extricate.
						// However, if there are more, then that is an expression, which
						//  does not match our expected argument list.
						if (foundVal.getSubexpressions().size() == 1)
							argLs = foundVal.getSubexpressions().get(0);
					}
					if (argLs instanceof ArgumentList) {
						ArgumentList list = (ArgumentList)argLs;
						int expected = source.getParamList().size();
						int found = list.getArgs().size();
						if (expected != found)
							throw new CheckException("Mismatch number of arguments given for function \"",
									ref.getVarName(), "\"! ", Integer.toString(found),
									" arguments found whereas ", Integer.toString(expected), " expected.");
					}else
						throw new CheckException("Mismatch number of arguments given for function \"",
								ref.getVarName(), "\"! 1 argument found whereas ",
								Integer.toString(source.getParamList().size()), " expected.");
				}else if (argLs instanceof ArgumentList) {
					// Check that the number of arguments matches!
					int found = ((ArgumentList)argLs).getArgs().size();
					if (found != 1)
						throw new CheckException("Mismatch number of arguments given for function \"",
								ref.getVarName(), "\"! ", Integer.toString(found),
								" arguments found whereas 1 expected.");
				}
				
				// Wrap the subexpression in a value, unless it already is a value
				Value args;
				if (argLs instanceof Value) {
					args = (Value)argLs;
					args.setParent(null);
				}else
					args = new Value(null, argLs);
				// We need to check any and all arguments
				check(args, env, optimize);
				ref.setArgument(args);
			}
		}
	}
	
	protected void check(Block block, List<Map<String, Variable>> env, boolean optimize) {
		env.add(new TreeMap<>());
		// check all children
		for (Expression e: block.getBody())
			check(e, env, optimize);
		env.remove(env.size() - 1);
	}
	
	protected void check(If ife, List<Map<String, Variable>> env, boolean optimize) {
		check(ife.getCondition(), env, optimize);
		check(ife.getThen(), env, optimize);
		check(ife.getElse(), env, optimize);
	}
	
	protected void check(Value val, List<Map<String, Variable>> env, boolean optimize) {
		// We have to perform grouping now that we know the type of all identifiers
		List<Subexpression> sub = val.getSubexpressions();
		// Evaluate function application very first
		for (int i=0; i<sub.size(); i++) {
			if (sub.get(i) instanceof Reference)
				check((Reference)sub.get(i), env, optimize);
		}
		
		// Then evaluate operator application, which has a lower precedence
		if (val.getSubexpressions().size() > 1) {
			// We could not do this during parsing because of order of operations:
			//  eg "2 + 4 / 1" -> "2 + (4 / 1)", even though the plus is seen first.
			// Also, we could not know whether each identifier was a function until
			//  checking, which is why we do it now.
			TreeSet<Float> precs = new TreeSet<>();
			for (int i = 0; i < sub.size(); i++) {
				Float prec = sub.get(i).getPrecedence();
				if (prec != null)
					precs.add(prec);
			}
			for (Float prec: precs) {
				boolean tryAgain;
				do {
					tryAgain = false;
					for (int i = 0; i < sub.size(); i++) {
						Subexpression sexp = sub.get(i);
						if (!sexp.isLink())
							continue;
						Float subPrec = sexp.getPrecedence();
						if (subPrec == null)
							continue;
						if (sexp.getPrecedence().equals(prec)) {
							sexp.evaluateChain(i, sub);
							tryAgain = true;
							break; // restart this precedence after the list has been modified
						}
					}
				}while(tryAgain);
			}
		}
		
		// Lastly, we want to perform checks for all of the grouped subexpressions
		for (Subexpression e: val.getSubexpressions())
			check(e, env, optimize);
	}
	
	protected void check(Operation op, List<Map<String, Variable>> env, boolean optimize) {
		check(op.getRHS(), env, optimize);
		if (op instanceof BinOp)
			check(((BinOp)op).getLHS(), env, optimize);
	}
	
	protected void check(ArgumentList list, List<Map<String, Variable>> env, boolean optimize) {
		for (Value arg: list.getArgs())
			check(arg, env, optimize);
	}
	
}
