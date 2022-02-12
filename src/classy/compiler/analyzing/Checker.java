package classy.compiler.analyzing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import classy.compiler.parsing.ArgumentList;
import classy.compiler.parsing.ArgumentList.LabeledValue;
import classy.compiler.parsing.Assignment;
import classy.compiler.parsing.BinOp;
import classy.compiler.parsing.Block;
import classy.compiler.parsing.Expression;
import classy.compiler.parsing.If;
import classy.compiler.parsing.Literal;
import classy.compiler.parsing.Operation;
import classy.compiler.parsing.Parameter;
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
		
		List<Frame> environment = new ArrayList<>();
		environment.add(new Frame(false));
		check(program, environment);
		
		if (optimize) {
			Optimizer opt = new Optimizer();
			opt.optimize(variables, program);
		}
	}
	
	protected void check(Expression e, List<Frame> env) {
		// dispatch on the appropriate checking function
		if (e instanceof Assignment)
			check((Assignment)e, env);
		else if (e instanceof Block)
			check((Block)e, env);
		else if (e instanceof If)
			check((If)e, env);
		else if (e instanceof Operation)
			check((Operation)e, env);
		else if (e instanceof Value) { // Since Value is so common a case, do it here to avoid another function call
			Value val = (Value)e;
			// We have to perform grouping now that we know the type of all identifiers
			List<Subexpression> sub = val.getSubexpressions();
			// Evaluate function application very first
			for (int i=0; i<sub.size(); i++) {
				if (sub.get(i) instanceof Reference)
					check((Reference)sub.get(i), env);
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
			
			// After all the grouping is complete, this value should not have more than one subexpression
			if (val.getSubexpressions().size() > 1)
				throw new CheckException("Unexpected ", val.getSubexpressions().get(1), " found in value!");
			
			// Lastly, we want to perform a final check
			for (Subexpression se: val.getSubexpressions())
				check(se, env);
		}else if (e instanceof Parameter)
			check((Parameter)e, env);
		else if (e instanceof Reference)
			check((Reference)e, env);
		// Literals are not checked, since they cannot be wrong
		else if (!(e instanceof Literal))
			throw new CheckException("Unchecked expression: " + e);
	}
	
	public List<Variable> getVariables() {
		return variables;
	}
	
	protected void check(Assignment asgn, List<Frame> env) {
		// There cannot be two variables with the same name in the same scope
		// TODO we will need to modify this for different signatures...
		Frame curScope = env.get(env.size() - 1);
		String name = asgn.getVarName();
		if (curScope.defined(name) != null) {
			Expression firstInstance = curScope.defined(name).source;
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
			// add this function's name to the environment to make recursive calls possible
			curScope.allocate(name, var);
			// Then create a new function scope where the parameters will reside
			Frame fxScope = new Frame(true);
			// Even though we will need to look through a variable list later, the elements
			//  we now add are appended at the end, so they should not effect the runtime.
			// We actually need these variables saved before the function value check in
			//  case they are externalities for a nested function!
			variables.add(var);
			for (Parameter parameter: asgn.getParamList()) {
				// Check the parameter (needed if it has a default value)
				check(parameter, env);
				String param = parameter.getName();
				Variable paramVar = new Variable(param, null, null);
				variables.add(paramVar);
				fxScope.allocate(param, paramVar);
			}
			env.add(fxScope);
			// Now that the function scope is made, use it for the value
			check(asgn.getValue(), env);
			env.remove(env.size() - 1); // Remove the scope of the function call
			
			// Checking will find any externalities of our function, which are variables
			//  that are correctly referenced, but not defined in the scope of the function.
			//  In translation, the function will not have the same scope, so these need to
			//  be made explicit. We include them as additional function parameters.
			// We condense the list of references to externalities to a set of assignments
			Set<Assignment> externalities = new HashSet<>();
			class ParamVariable extends Variable {
				protected Variable oldVar;
				public ParamVariable(String name, Assignment source, Variable oldVar) {
					super(name, null, source);
					this.oldVar = oldVar;
				}
			}
			Map<Assignment, ParamVariable> replacements = new HashMap<>();
			for (Reference ext: fxScope.externalities) {
				// Recursive functions will trigger an externality on themselves. This is 
				//  to be ignored, since we don't handle recursion as an added parameter.
				if (ext.getLinkedTo() == asgn)
					continue;
				boolean added = externalities.add(ext.getLinkedTo());
				if (added) {
					// Then we must create a replacement for all function replacements
					Assignment old = ext.getLinkedTo();
					// Find the old variable to replace its references
					Variable oldVar = null;
					for (Variable checkVar: variables) {
						if (checkVar.source == old) {
							oldVar = checkVar;
							break;
						}
					}
					// Instead of using the externality directly, all references in
					// the function need to use the new variable we are creating.
					// Therefore, we have to retroactively apply it
					// TODO: The reason why we are not adding a source is because that is how
					//  we keep track of a single variable for variable mangling in translation.
					replacements.put(old, new ParamVariable(ext.getVarName(), null, oldVar));
				}
				// The replacement should already have been mapped
				ParamVariable extern = replacements.get(ext.getLinkedTo());
				// replace the old link with the new link
				extern.oldVar.getRef().remove(ext);
				extern.addRef(ext);
				variables.add(extern);
			}
			// Then, for each of the actual externalities, we add a new parameter to this function
			//  as a default value parameter
			for (ParamVariable pVar: replacements.values()) {
				// We need to create a new reference to the old assignment.
				// That is what we use as the "default value" to this implicit
				//  parameter. The idea is that if this function is in scope
				//  to be called, then so is the externality.
				Reference ref = new Reference(null);
				Value val = new Value(null, ref);
				// It is useless to set the reference source, since we only need
				//  that in checking, a step that is redundant for a synthetic
				//  reference. Instead, we must add the reference to the externality
				//  directly.
				pVar.oldVar.addRef(ref);
				asgn.getParamList().add(new Parameter(pVar.getName(), val));
			}
		}else {
			check(asgn.getValue(), env);
			Variable var = new Variable(name, asgn.getValue(), asgn);
			variables.add(var);
			curScope.allocate(name, var);
		}
	}
	
	protected void check(Reference ref, List<Frame> env) {
		if (checkedRef.contains(ref))
			return; // do not re-check a reference
		checkedRef.add(ref);
		
		String name = ref.getVarName();
		// We must find some variable that matches the name given
		Variable referenced = null;
		int i = env.size() - 1;
		for (; i>=0 && referenced==null; i--) {
			Frame search = env.get(i);
			referenced = search.defined(name);
		}
		if (referenced == null)
			throw new CheckException("Reference ", ref, " to an undeclared variable \"", name, "\"!");
		// Add this ref as an externality for all passed function scopes
		for (int j = i + 2; j < env.size(); j++) {
			if (env.get(j).isFunction())
				env.get(j).addExternality(ref);
		}
		
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
					throw new CheckException("Missing argument(s) for function call ", ref, "!");
				// Check all arguments to this reference
				// We don't want to check an argument list by itself, since it cannot occur by
				//  itself, and an error should be thrown if it is.
				if (!(subexp.get(refAt + 1) instanceof ArgumentList))
					check(subexp.get(refAt + 1), env);
				else {
					// Check all the arguments in the list
					ArgumentList ls = (ArgumentList)subexp.get(refAt + 1);
					for (Value arg: ls.getArgs())
						check(arg, env);
				}
				Subexpression argLs = subexp.remove(refAt + 1);
				// We want to get the argument list for this reference. If the argument
				//  is not an argument list (which is common for single-argument functions),
				//  then we want to wrap it as an argument list for uniformity.
				ArgumentList ls = null;
				if (argLs instanceof Value) {
					Value foundVal = (Value)argLs;
					// There should only be one subexpression in the value at this point,
					//  since it has necessarily been checked (and checking requires that
					//  there is only 1 resulting subexpression).
					// Thus, we extricate the subexpression to make the argument list.
					// TODO it turns out that an argument list does *not* result in a value,
					//  so in checking, we should validate that the value is not just an
					//  argument list
					if (foundVal.getSubexpressions().size() == 1)
						argLs = foundVal.getSubexpressions().get(0);
				}
				if (argLs instanceof ArgumentList)
					ls = (ArgumentList)argLs;
				else {
					ls = new ArgumentList(argLs.getParent());
					ls.addArg(new LabeledValue(new Value(null, argLs)));
				}
				
				// We want to type check on the number of arguments the function needs.
				// However, this gets more complicated since there may be default values
				//  specified by the function.
				
				// We want to check that all the arguments given match with parameters of
				//  the function, and that all necessary parameters of the function have
				//  been satisfied by corresponding arguments.
				List<Parameter> paramList = source.getParamList();
				// Furthermore, in translation to LLVM bytecode, we need all arguments to
				//  be in the order of the parameters. Therefore, we can easily order
				//  them here in checking.
				LabeledValue[] sortedArgs = new LabeledValue[paramList.size()];
				// Our first approach is to go through each argument sequentially and
				//  place it if it has a label (we cannot place any positional arguments
				//  until all labeled arguments have been placed since we don't know
				//  beforehand since labeled arguments do not have to be in order.
				// If a label has been provided for an argument, it *must* match one of
				//  the parameter names in the called function.
				for (LabeledValue arg: ls.getArgs()) {
					if (arg.getLabel() != null) {
						int j = 0;
						for (; j<paramList.size(); j++) {
							Parameter p = paramList.get(j);
							if (!p.getImplicit() && arg.getLabel().equals(p.getName()))
								break;
						}
						if (j == paramList.size()) // we could not find the specified parameter!
							throw new CheckException("Unrecognized parameter label \"", arg.getLabel(),
									"\" for function \"", source.getVarName(),
									"\" requested by argument list of ", ref, "!");
						if (sortedArgs[j] != null) // the parameter was already specified!
							throw new CheckException("Duplicate parameter label \"", arg.getLabel(),
									" requested by argument list of ", ref, "!");
						sortedArgs[j] = arg; // set this argument in its place
					}
				}
				// Now we must iterate over the list again to get all positional arguments
				int k = 0; // where to place the next argument in the sorted list
				int j = 0; // how far we are in the given argument list
				for (; j < ls.getArgs().size(); j++) {
					LabeledValue arg = ls.getArgs().get(j);
					if (arg.getLabel() != null)
						continue;
					// We have an argument to place...
					while (true) {
						// Check if there is already a sorted argument at t
						if (sortedArgs[k] != null)
							k++;
						// or if the parameter at t has the wrong argument (but has a default value)
						// or if the parameter at t is implicit (and thus may receive no argument).
						else if (paramList.get(k).getImplicit())
							sortedArgs[k] = new LabeledValue(paramList.get(k).getDefaultVal());
						else if (k >= sortedArgs.length)
							throw new CheckException("Mismatch number of arguments in function call ",
									ref, "! ", (ls.getArgs().size() - j)+"", " too many arguments given.");
						else {
							sortedArgs[k++] = arg;
							break; // we placed it, so we can break out							
						}
					}
				}
				// We must bring the sorted list to completion with any necessary default values
				for (; k < paramList.size(); k++) {
					if (sortedArgs[k] != null)
						continue; // if this index was already used, skip it
					Parameter p = paramList.get(k);
					if (p.getDefaultVal() == null) {
						StringBuffer missList = new StringBuffer("\"");
						missList.append(p.getName());
						missList.append("\"");
						for (; k < paramList.size(); k++) {
							if (sortedArgs[k] != null || paramList.get(k).getImplicit())
								continue;
							missList.append(", \"");
							missList.append(paramList.get(k).getName());
							missList.append("\"");
						}
						throw new CheckException("Mismatch number of arguments in function call ",
								ref, "! Call missing arguments for ", missList.toString(), ".");
					}
					// If there was a default value given, use it
					sortedArgs[k] = new LabeledValue(p.getDefaultVal());
				}
				// Finally, we update the argument list to match our new sorted list
				ls.getArgs().clear();
				for (LabeledValue arg: sortedArgs)
					ls.addArg(arg);
				
				// Set the reference's argument value
				Value args = new Value(null, ls);
				ref.setArgument(args);
			}
		}
	}
	
	protected void check(Block block, List<Frame> env) {
		env.add(new Frame(false));
		// check all children
		for (Expression e: block.getBody())
			check(e, env);
		env.remove(env.size() - 1);
	}
	
	protected void check(If ife, List<Frame> env) {
		check(ife.getCondition(), env);
		check(ife.getThen(), env);
		check(ife.getElse(), env);
	}
	
	protected void check(Operation op, List<Frame> env) {
		check(op.getRHS(), env);
		if (op instanceof BinOp)
			check(((BinOp)op).getLHS(), env);
	}
	
	protected void check(Parameter param, List<Frame> env) {
		if (param.getDefaultVal() != null)
			check(param.getDefaultVal(), env);
	}
	
}
