package classy.compiler.analyzing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import classy.compiler.lexing.Token;
import classy.compiler.parsing.Assignment;
import classy.compiler.parsing.BinOp;
import classy.compiler.parsing.Block;
import classy.compiler.parsing.Expression;
import classy.compiler.parsing.If;
import classy.compiler.parsing.Literal;
import classy.compiler.parsing.NameBinding;
import classy.compiler.parsing.Operation;
import classy.compiler.parsing.Parameter;
import classy.compiler.parsing.Reference;
import classy.compiler.parsing.Subexpression;
import classy.compiler.parsing.Tuple;
import classy.compiler.parsing.Tuple.LabeledValue;
import classy.compiler.parsing.TypeDefinition;
import classy.compiler.parsing.Value;

public class Checker {
	// Variables and types define all the variables and types used in the program.
	// Conceptually they are sets, but we never need to verify that no instance occurs
	//  more than once, so we save time by making them lists.
	protected List<Variable> variables = new ArrayList<>();
	protected List<Type> types = new ArrayList<>();
	// A set of references that have already been checked. We can return the
	//  previously calculated type instead of checking them again.
	protected Set<Reference> checkedRef = new HashSet<>();
	
	public Type result;
	
	
	public Checker() {}
	
	public Checker(Value program) {
		result = check(program);
	}
	
	public Type check(Value program) {
		// We need to check variable references and reuse:
		//  There cannot be two variables with the same name in the same scope.
		//  If there is only one usage of a variable, it can be replaced with the value.
		// We also need to type check the entire program
		
		List<Frame> environment = new ArrayList<>();
		Frame first = new Frame(null);
		// Set all the default types
		types.add(Type.Any);
		types.add(Type.Int);
		types.add(Type.Bool);
		for (Type t: types)
			first.makeType(t);
		environment.add(first);
		return check(program, environment);
	}
	
	public Type check(Expression e, List<Frame> env) {
		// dispatch on the appropriate checking function
		if (e instanceof Assignment)
			return check((Assignment)e, env);
		else if (e instanceof TypeDefinition)
			return check((TypeDefinition)e, env);
		else if (e instanceof Block)
			return check((Block)e, env);
		else if (e instanceof If)
			return check((If)e, env);
		else if (e instanceof Operation)
			return check((Operation)e, env);
		else if (e instanceof Value) { // Since Value is so common a case, do it here to avoid another function call
			Value val = (Value)e;
			// We have to perform grouping now that we know the type of all identifiers
			List<Subexpression> sub = val.getSubexpressions();
			// Evaluate function application and reference location very first
			int lastSize = sub.size();
			for (int i=0; i<sub.size(); i++) {
				if (sub.get(i) instanceof Reference)
					check((Reference)sub.get(i), env);
				if (lastSize != sub.size()) {
					// The list can change as references chain other parts
					i--; // redo this index
					lastSize = sub.size();
				}
			}
			
			// Then evaluate operator application, which has a lower precedence
			if (sub.size() > 1) {
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
			return check(val.getSubexpressions().get(0), env);
		}else if (e instanceof Reference)
			return check((Reference)e, env);
		else if (e instanceof Literal)
			return check((Literal)e, env);
		else
			throw new CheckException("Unchecked expression: " + e);
	}
	
	public List<Variable> getVariables() {
		return variables;
	}
	public List<Type> getTypes() {
		return types;
	}
	
	protected Type resolveAnnotation(Type annot, Type valued, List<Frame> env, NameBinding bind) {
		if (annot instanceof Type.Stub) {
			// Stubs receive a leading - to the type name so that the stub may not
			//  be confused with a real type value.
			String realName = annot.name.substring(1);
			// try to resolve the stub name
			Type referenced = null;
			for (int i = env.size() - 1; i>=0 && referenced==null; i--) {
				referenced = env.get(i).typeDefined(realName);
			}
			if (referenced == null)
				throw new CheckException("Type annotation for ", bind,
						" cannot be resolved! Undefined type \"", realName, "\".");
			return referenced;
		}
		if (valued == null || valued.isa(annot))
			return annot;
		else
			throw new CheckException("Type of assignment's value, ", valued,
					" cannot be cast to match the type of the given annotation: ",
					annot, "!");
	}
	
	protected Type check(Assignment asgn, List<Frame> env) {
		Type type = null;
		// There cannot be two variables with the same name in the same scope
		// TODO we will need to modify this for different signatures...
		// There can be two functions with the same name as long as they are variants
		//  of the same parent type. 
		Frame curScope = env.get(env.size() - 1);
		String name = asgn.getVarName();
		if (curScope.varDefined(name) != null) {
			NameBinding firstInstance = curScope.varDefined(name).source;
			throw new CheckException("Multiple definitions of same variable name \"",
					name, " found in the same scope! First instance: ", firstInstance,
					" and second instance: ", asgn);
		}
		Variable var = new Variable(name, asgn.getValue(), asgn);
		
		// If the parameter list is null, then this is simply a variable. Otherwise, this is
		// a function definition. 
		if (asgn.getParamList() == null) {
			// Try to get the type of this assignment
			// If there is an annotation connected, the type of value <: annotation
			type = check(asgn.getValue(), env);
			if (asgn.getAnnotation() != null)
				type = resolveAnnotation(asgn.getAnnotation(), type, env, asgn);
			var.setType(type);
			variables.add(var);
			curScope.allocate(var);
		}else {
			// Function definitions are special since the value can have references to itself.
			// Also, it needs to add the parameters in a scope that contains only the value.
			
			// add this function's name to the environment to make recursive calls possible
			curScope.allocate(var);
			// Then create a new function scope where the parameters will reside
			Frame fxScope = new Frame(asgn.getVarName());
			// Even though we will need to look through a variable list later, the elements
			//  we now add are appended at the end, so they should not effect the runtime.
			// We actually need these variables saved before the function value check in
			//  case they are externalities for a nested function!
			variables.add(var);
			List<ParameterType> inputTypes = new ArrayList<>(asgn.getParamList().size());
			for (Parameter parameter: asgn.getParamList()) {
				// Check the parameter (needed if it has a default value)
				Value defaultValue = parameter.getDefaultVal();
				Type ptype = null;
				if (defaultValue != null)
					ptype = check(parameter.getDefaultVal(), env);
				// if the parameter has a type annotation, it must work with the type of given
				if (parameter.getAnnotation() != null)
					ptype = resolveAnnotation(parameter.getAnnotation(), ptype, env, parameter);
				String param = parameter.getName();
				Variable paramVar = new Variable(param, null, parameter);
				if (ptype == null)
					ptype = new Undetermined.Param(paramVar); // set it as undetermined so far
				paramVar.setType(ptype);
				// For constructing the type of this variable, we must use a ParameterType because
				//  it has an attached name. This is important since the checker will use the type,
				//  not the source of the variable.
				ParameterType paramType = new ParameterType(param, ptype);
				// We must also pass along the default value to the parameter type
				paramType.setDefaultValue(parameter.getDefaultVal());
				inputTypes.add(paramType);
				variables.add(paramVar);
				fxScope.allocate(paramVar);
			}
			// We need to create a temporary array of the inputs just so that the value
			//  of the function can check (since recursive calls may be made).
			// We will need to update the type later with necessary extra parameters
			//  so that it can be used by translation.
			
			ParameterType[] inputsReplacement = inputTypes.toArray(new ParameterType[inputTypes.size()]);
			
			// We may not have the type of the function yet, but we can assign the type
			//  of the parameters. Set the type of this variable in case a recursive call
			//  is made by the value
			
			// The return may be determined if there is a type annotation for the function.
			// Otherwise, we want to give an undetermined returned, which provides a link
			//  in case the value sets a return type
			Type returnType;
			if (asgn.getAnnotation() != null)
				returnType = resolveAnnotation(asgn.getAnnotation(), null, env, asgn);
			else
				returnType = new Undetermined.Return(var);
			var.setType(new Type(returnType, inputsReplacement));
			
			env.add(fxScope);
			// Now that the function scope is made, use it for the value
			type = check(asgn.getValue(), env);
			
			env.remove(env.size() - 1); // Remove the scope of the function call
			// Now we can set the type of the return (assuming it has not been set already)
			if (var.getType().output instanceof Undetermined.Return) {
				var.getType().output = type;
			}else {
				// If it has already been set, we need to verify that what it was set to is consistent
				if (!type.isa(var.getType().output))
					throw new CheckException("Type error for assignment of function ", asgn,
							"! Cannot return both ", type, " and ", var.getType().output, ".");
			}
			
			// Checking must find any externalities of our function, which are variables
			//  that are correctly referenced, but not defined in the scope of the function.
			//  In translation, the function will not have the same scope, so these need to
			//  be made explicit. We include them as additional function parameters.
			// We condense the list of externality references to a set of assignments
			Set<Variable> externalities = new HashSet<>();
			class ParamVariable extends Variable {
				protected Variable oldVar;
				public ParamVariable(String name, NameBinding source, Variable oldVar) {
					super(name, null, source);
					this.oldVar = oldVar;
				}
			}
			Map<Variable, ParamVariable> replacements = new HashMap<>();
			for (Reference ext: fxScope.externalities) {
				// Recursive functions will trigger an externality on themselves. This is 
				//  to be ignored, since we don't handle recursion as an added parameter.
				if (ext.getLinkedTo() == var)
					continue;
				boolean added = externalities.add(ext.getLinkedTo());
				if (added) { // If added is true, then this variable was new to the set
					// Then we must create a replacement for all function replacements
					Variable oldVar = ext.getLinkedTo();
					// Instead of using the externality directly, all references in
					// the function need to use the new variable we are creating.
					// Therefore, we have to retroactively apply it
					ParamVariable newVar = new ParamVariable("&"+ext.getVarName(), null, oldVar);
					newVar.type = oldVar.type;
					variables.add(newVar);
					replacements.put(oldVar, newVar);
				}
				// The replacement should already have been mapped
				ParamVariable extern = replacements.get(ext.getLinkedTo());
				// replace the old link with the new link
				extern.oldVar.getRef().remove(ext);
				extern.addRef(ext);
				ext.setLinkedTo(extern);
				ext.setVarName(extern.name);
			}
			// Then, for each of the actual externalities, we add a new parameter to this function
			//  as a default value parameter
			// As we go through the replacement variables, we add the remaining needed entries.
			for (ParamVariable pVar: replacements.values()) {
				// We need to create a new reference to the old assignment.
				// That is what we use as the "default value" to this implicit
				//  parameter. The idea is that if this function is in scope
				//  to be called, then so is the externality.
				Reference ref = new Reference(null);
				ref.setVarName(pVar.oldVar.name);
				ref.setLinkedTo(pVar.oldVar); // tell the reference what it points to (in case it is cloned)
				// For optimization reasons, we do not want to tell oldVar that the default
				//  value is one of its references (though we connect them from reference).
				// This is because whenever the default value is used, the reference is created. If it is
				//  never used, then the variable referenced may be safely deleted.
				Value value = new Value(null, ref);
				
				Parameter fParam = new Parameter(pVar.getName(), value);
				fParam.setSourced(pVar);
				asgn.getParamList().add(fParam);
				ParameterType ptype = new ParameterType(pVar.getName(), pVar.type);
				ptype.defaultValue = value;
				ptype.implicit = true;
				inputTypes.add(ptype);
			}
			// If the size of inputTypes changed (so there are some externalities to account for), then
			//  we need to update the function type.
			if (inputsReplacement.length != inputTypes.size()) {
				inputsReplacement = inputTypes.toArray(new ParameterType[inputTypes.size()]);
				var.getType().inputs = inputsReplacement;
			}
		}
		
		return null;
	}
	
	protected Type check(TypeDefinition def, List<Frame> env) {
		// Verify that there is not already a type with this same name
		Frame curScope = env.get(env.size() - 1);
		String name = def.getTypeName();
		if (curScope.typeDefined(name) != null) {
			TypeDefinition firstInstance = curScope.typeDefined(name).source;
			throw new CheckException("Multiple type definitions with the same type name \"",
					name, " found in the same scope! First instance: ", firstInstance,
					" and second instance: ", def);
		}
		
		// We want to create a new type from this definition
		Type created = new Type(def.getTypeName());
		types.add(created);
		def.setSourced(created);
		curScope.makeType(created);
		// We also want to add all fields from this type definition
		List<Parameter> fieldList = def.getFieldList();
		ParameterType fields[] = new ParameterType[fieldList.size()];
		for (int i=0; i < fieldList.size(); i++) {
			Parameter p = fieldList.get(i);
			Variable field = new Variable(p.getName(), p.getDefaultVal(), p);
			// Try to set the type of the field
			Type type = new Undetermined.Param(field);
			if (p.getDefaultVal() != null)
				type = check(p.getDefaultVal(), env);
			if (p.getAnnotation() != null)
				type = resolveAnnotation(p.getAnnotation(), type, env, p);
			field.setType(type);
			fields[i] = new ParameterType(p.getName(), type);
			
			created.fields.put(p.getName(), field);
		}
		// TODO create the constructor
		Variable construct = new Variable("..new" + def.getTypeName(), null, null);
		construct.type = new Type(created, fields); // give the params for the constructor type 
		created.methods.put(construct.name, construct);
		
		return null;
	}
	
	protected Type check(Reference ref, List<Frame> env) {
		if (checkedRef.contains(ref))
			return ref.getType(); // do not re-check a reference
		checkedRef.add(ref);
		
		// We need to find if this is a member or an independent reference.
		//  If this was parsed with a dot, then we already know it should be a member.
		//  We can know this should be a member if the previous subexpression in this
		//   value takes members (not a function).
		boolean member = false;
		List<Subexpression> subexp = ref.getParent().getSubexpressions();
		int refAt = subexp.indexOf(ref);
		if (refAt == -1) 
			throw new RuntimeException("Could not find " + ref.toString() + " in parent expression!");
		if (refAt > 0) {
			Subexpression sub = subexp.get(refAt - 1);
			if (sub instanceof Reference) {
				// There is a case where the reference before is evaluating function arguments, in
				//  which case the type will be null (but this cannot be a member, so it is fine).
				Reference before = (Reference)sub;
				member = before.getType() != null && !before.getType().isFunction();
			}else if (sub instanceof Operation) {
				// This may be a member of the result of the operation.
				// But not if the operation's rhs is undefined. (In that case, this
				//  is that operation's rhs. This may be checked before since function
				//  application takes precedence over operators.)
				Operation op = (Operation)sub;
				member = op.getRHS() != null;
			}else
				member = true; // there is something to take the type of
		}
		
		if (!member && ref.isMember()) // If parsing told us this should be a member, but isn't, throw the error
			throw new CheckException("Member found without any location! Some object-resulting expression must precede the member ",
					ref, ".");

		// Now things diverge greatly based on whether this is a member reference or not:
		if (member) {
			// If everything typed correctly, there should be exactly one subexpression before this
			if (refAt - 1 >= 0) {
				Subexpression sub = subexp.remove(refAt - 1);
				Type parentType = check(sub, env);
				if (parentType instanceof Undetermined)
					throw new CheckException("The type of ", sub, ", with the member ", ref, " could not be determined!");
				// Now we must find some reference within
				Variable linkedTo = parentType.fields.get(ref.getVarName()); // first try a field
				if (linkedTo == null)
					linkedTo = parentType.methods.get(ref.getVarName()); // next try a method
				if (linkedTo == null) // could not find a valid member in the type
					throw new CheckException("\"", ref.getVarName(), "\" from ", ref, " not a member of ", parentType, "!");
				ref.setLinkedTo(linkedTo);
				ref.setMember(true);
				ref.getMemberData().location = new Value(null, sub);
				ref.getMemberData().memberOf = parentType;
				
				Type linkType = linkedTo.getType();
				if (linkType.isFunction())
					handleFunctionRef(ref, linkType, env, refAt);
				else
					ref.setType(linkType);
			}// else should never happen since the value checks that there is something before
			
			return ref.getType();
		}
		
		// If we got here, then this is not a member
		String name = ref.getVarName();
		// We must find some variable that matches the name given
		Variable referenced = null;
		Type typed = null;
		int i = env.size() - 1;
		for (; i>=0 && referenced==null && typed==null; i--) {
			Frame search = env.get(i);
			if (name.equals("self") && search.functionName != null)
				name = search.functionName; // define "self"
			referenced = search.varDefined(name);
			typed = search.typeDefined(name);
		}
		if (typed != null) {
			// if we found the type, then we have a constructor call here
			referenced = typed.methods.get("..new" + typed.name);
		}else {			
			if (referenced == null) {			
				// If the name is "self", then the user tried to use self in a non-function definition
				if (name.equals("self"))
					throw new CheckException("\"self\" keyword may only be used in a function definition! Found ",
							ref, ".");
				// otherwise we just have a regular undeclared variable issue
				throw new CheckException("Reference ", ref, " to an undeclared variable \"", name, "\"!");
			}
			// Add this ref as an externality for all passed function scopes
			for (int j = i + 2; j < env.size(); j++) {
				if (env.get(j).isFunction())
					env.get(j).addExternality(ref);
			}
		}		
		referenced.addRef(ref);
		ref.setLinkedTo(referenced);
		
		// We can know the type by the type of the variable linked to
		Type type = referenced.getType();
		if (type == null)
			throw new CheckException("Missing type in ", referenced, "!");
		/*
		 * This is the tricky part. We want to find the type of this reference, which is dependent
		 * upon the type of the reference, and may also use the types given in the argument list.
		 * Alternatively, if the referenced is undetermined, we will see if maybe it is a function.
		 */
		if (type.isFunction()) {
			handleFunctionRef(ref, type, env, refAt);
		}else if (referenced.getType() instanceof Undetermined) {
			// could be a function or could be a regular variable
			ref.setType(type);
		}else {
			// If it is not a function, this is the easy part. We can simply assign the type
			//  of the reference to the same as the variable being referenced
			ref.setType(type);
		}
		
		return ref.getType();
	}
	protected void handleFunctionRef(Reference ref, Type type, List<Frame> env, int refAt) {
		// Just because this reference is a function type does *not* mean that it is being
		//  used as a function call. We need to take a look at the next subexpression to see
		//  the context of how this reference is being used.
		
		// We need to connect the next token to the ref
		List<Subexpression> subexp = ref.getParent().getSubexpressions();
		if (refAt == -1) 
			throw new CheckException("Reference ", ref, " cannot be found in parents subexpressions!");
		if (refAt + 1 >= subexp.size()) {
			ref.setType(type);
			return; // not using a call since there are no arguments.
		}
		// If the next subexpression is not argument-allowable, then we know this isn't a call
		
		// Check all arguments to this reference
		// We don't want to check a tuple by itself, since it cannot occur by
		//  itself, and an error should be thrown if it is.
		// We cannot disconnect the subexpression before it is checked since it may
		//  itself have connecting arguments.
		Subexpression argLs = subexp.get(refAt + 1);
		if (!(argLs instanceof Tuple)) {
			if (argLs instanceof BinOp) // operations are not possible on function types 
				throw new CheckException("Operation ", argLs,
						" may not be called on function-typed reference ", ref, "!");
			
			// If all the checks did not flag this, then it must be a call.
			check(argLs, env);
		}else {
			// Check all the arguments in the list
			Tuple ls = (Tuple)argLs;
			for (Value arg: ls.getArgs())
				check(arg, env);
		}
		
		subexp.remove(refAt + 1);
		// We want to get the argument list for this reference. If the argument
		//  is not an argument list (which is common for single-argument functions),
		//  then we want to wrap it as an argument list for uniformity.
		Tuple ls = null;
		if (argLs instanceof Value) {
			Value foundVal = (Value)argLs;
			// There should only be one subexpression in the value at this point,
			//  since it has necessarily been checked (and checking requires that
			//  there is only 1 resulting subexpression).
			// Thus, we extricate the subexpression to make the argument list.
			// It turns out that an argument list does *not* result in a value,
			//  so in checking, we should validate that the value is not just an
			//  argument list
			if (foundVal.getSubexpressions().size() == 1)
				argLs = foundVal.getSubexpressions().get(0);
		}
		if (argLs instanceof Tuple)
			ls = (Tuple)argLs;
		else {
			ls = new Tuple(argLs.getParent());
			ls.addArg(new LabeledValue(new Value(null, argLs)));
		}
		
		// We want to type check on the number of arguments the function needs.
		// However, this gets more complicated since there may be default values
		//  specified by the function.
		
		// We want to check that all the arguments given match with parameters of
		//  the function, and that all necessary parameters of the function have
		//  been satisfied by corresponding arguments.
		ParameterType[] paramList = type.inputs;
		// Furthermore, in translation to LLVM bytecode, we need all arguments to
		//  be in the order of the parameters. Therefore, we can easily order
		//  them here in checking.
		LabeledValue[] sortedArgs = new LabeledValue[paramList.length];
		// Our first approach is to go through each argument sequentially and
		//  place it if it has a label (we cannot place any positional arguments
		//  until all labeled arguments have been placed since we don't know
		//  beforehand since labeled arguments do not have to be in order.
		// If a label has been provided for an argument, it *must* match one of
		//  the parameter names in the called function.
		for (LabeledValue arg: ls.getArgs()) {
			if (arg.getLabel() != null) {
				int j = 0;
				for (; j<paramList.length; j++) {
					// TODO: should check type here
					if (!paramList[j].implicit && arg.getLabel().equals(paramList[j].name))
						break;
				}
				if (j == paramList.length) // we could not find the specified parameter!
					throw new CheckException("Unrecognized parameter label \"", arg.getLabel(),
							"\" for function call \"", ref.getVarName(),
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
			if (arg.getLabel() != null) // this arg already placed since it is positional
				continue;
			// We have an argument to place...
			while (true) {
				if (k >= sortedArgs.length)
					throw new CheckException("Mismatch number of arguments in function call ",
							ref, "! ", (ls.getArgs().size() - j)+"", " too many arguments given.");
				
				// Check if there is already a positional argument at t
				if (sortedArgs[k] != null)
					k++;
				// or if the parameter at t has the wrong argument type (but has a default value)
				
				// or if the parameter at t is implicit (and thus may receive no argument).
				else if (paramList[k].implicit) {
					// We need to copy from the default value to the argument list
					// Unfortunately, we need to perform a deep clone of the default value.
					//  If we merely copy the reference, then multiple uses of a reference
					//  will not be correctly realized for optimization.
					Value cloned = paramList[k].defaultValue.clone();
					// We will have references add themselves appropriately on a clone, and
					//  thus we don't need to check again.
					sortedArgs[k] = new LabeledValue(cloned);
					
				}else {
					sortedArgs[k++] = arg;
					break; // we placed it, so we can break out							
				}
			}
		}
		// We must bring the sorted list to completion with any necessary default values
		for (; k < paramList.length; k++) {
			if (sortedArgs[k] != null)
				continue; // if this index was already used, skip it
			if (paramList[k].defaultValue == null) {
				StringBuffer missList = new StringBuffer("\"");
				missList.append(paramList[k].name);
				missList.append("\"");
				for (k += 1; k < paramList.length; k++) {
					if (sortedArgs[k] != null || paramList[k].implicit)
						continue;
					missList.append(", \"");
					missList.append(paramList[j].name);
					missList.append("\"");
				}
				throw new CheckException("Mismatch number of arguments in function call ",
						ref, "! Call missing arguments for ", missList.toString(), ".");
			}
			// If there was a default value given, use it
			sortedArgs[k] = new LabeledValue(paramList[k].defaultValue.clone());
		}
		// Finally, we update the argument list to match our new sorted list
		ls.getArgs().clear();
		for (LabeledValue arg: sortedArgs)
			ls.addArg(arg);
		
		// Set the reference's argument value
		Value args = new Value(null, ls);
		ref.setArgument(args);
		
		// TODO: Here we assume that we are just doing a call, and thus the type of this
		//  reference is merely the output type of the function
		ref.setType(type.output);
	}
	
	protected Type check(Block block, List<Frame> env) {
		env.add(new Frame(null));
		Type type = null;
		// check all children
		for (Expression e: block.getBody())
			type = check(e, env);
		env.remove(env.size() - 1);
		return type; // the value (must be last expression) has the type
	}
	
	protected Type check(If ife, List<Frame> env) {
		Type condType = check(ife.getCondition(), env);
		expectType(Type.Bool, condType);
		Type thenType = check(ife.getThen(), env);
		Type elseType = check(ife.getElse(), env);
		// The less specific of the two is the type returned
		Type intersected = thenType.intersect(elseType);
		if (intersected == null)
			throw new CheckException("Types of then and else in conditional ", ife,
					" must have a common ancestor! Type ",
					thenType, " and type ", elseType, " found instead.");
		return intersected;
	}
	
	protected Type check(Operation op, List<Frame> env) {
		Type rhsType = check(op.getRHS(), env);
		if (op instanceof Operation.Not || op instanceof BinOp.And || op instanceof BinOp.Or)
			rhsType = expectType(Type.Bool, rhsType);
		else
			rhsType = expectType(Type.Int, rhsType);
		if (rhsType == null)
			throw new CheckException("The right expression (", op.getRHS(), ") of ",
					op.getExpressionName(), " must be a number! ", check(op.getRHS()), " found instead.");
		if (op instanceof BinOp) {
			Type lhsType = check(((BinOp)op).getLHS(), env);
			// see if the operation is AND or OR, which both require boolean arguments
			if (op instanceof BinOp.And || op instanceof BinOp.Or) {
				lhsType = expectType(Type.Bool, lhsType);
				if (lhsType == null)
					throw new CheckException("The left expression (", ((BinOp) op).getLHS(),
							") of ", op.getExpressionName(), " must be a bool! ",
							check(((BinOp)op).getLHS(), env), " found instead.");
			}else {
				// otherwise the operation takes int arguments
				lhsType = expectType(Type.Int, lhsType);
				if (lhsType == null)
					throw new CheckException("The left expression (", ((BinOp) op).getLHS(),
							") of ", op.getExpressionName(), " must be a number! ",
							check(((BinOp)op).getLHS(), env), " found instead.");
				
				// Some return bool and some return int
				if (op instanceof BinOp.Equal ||
					op instanceof BinOp.NEqual ||
					op instanceof BinOp.LessThan ||
					op instanceof BinOp.LessEqual ||
					op instanceof BinOp.GreaterThan ||
					op instanceof BinOp.GreaterEqual)
					return Type.Bool;
			}
			//rhsType = rhsType.intersect(lhsType);
		}
		return rhsType;
	}
	
	protected Type check(Literal lit, List<Frame> env) {
		if (lit.getToken().getType() == Token.Type.NUMBER)
			return Type.Int;
		return Type.Bool; // the other type is true or false
	}
	
	protected Type expectType(Type expected, Type got) {
		if (got.isa(expected))
			return expected;
		if (got instanceof Undetermined) 
			return ((Undetermined)got).coerce(expected);
		
		return null;
	}
	
}
