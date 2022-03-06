package classy.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import classy.compiler.analyzing.Checker;
import classy.compiler.analyzing.Optimizer;
import classy.compiler.lexing.Lexer;
import classy.compiler.lexing.Token;
import classy.compiler.parsing.Parser;
import classy.compiler.parsing.Value;
import classy.compiler.translation.Translator;

public class Classy {
	public static final String SAVE = "save";
	public static final String VERBOSE = "verbose";
	public static final String NO_OPT = "O0";
	public static final String OUTPUT = "out";
	
	public static void main(String args[]) {
		String pathName = null;
		
		Map<String, String> flags = new HashMap<>();
		for(int i=0; i<args.length; i++) {
			switch(args[i]) {
			case "-help":
			case "-h":
				printHelp();
				return;
			case "-O0":		// turns off optimizations
			case "-Opt0":
				i = addFlag(NO_OPT, 0, flags, args, i);
				break;
			case "-s":		// debug- saves intermediate files
			case "-save":
				i = addFlag(SAVE, 0, flags, args, i);
				break;
			case "-v":		// verbose- prints out execution information
			case "-verbose":
				i = addFlag(VERBOSE, 0, flags, args, i);
				break;
			// Flags with one argument
			case "-o":		// names the output
				i = addFlag(OUTPUT, 1, flags, args, i);
				break;
			default:
				if (pathName == null)
					pathName = args[i];
				else
					System.err.println("Unknown flag: " + args[i]);
			}
		}
		
		List<String> lines = new ArrayList<>();
		Scanner scan = null;
		boolean verbose = flags.containsKey("verbose");
		if (pathName == null) {
			// We should get input from stdin
			if (verbose)
				System.out.println("Compiling from stdin...");
			scan = new Scanner(System.in);
		} else {
			if (verbose)
				System.out.println("Compiling \"" + pathName + "\"...");
			try {
				scan = new Scanner(new File(pathName));
			} catch (FileNotFoundException e) {
				throw new RuntimeException("Could not find file \"" + pathName + "\"!");
			}
		}
		while(scan.hasNextLine())
			lines.add(scan.nextLine());
		scan.close();
		if (lines.isEmpty())
			throw new RuntimeException("Empty input!");
		
		// remove the file extension from the path name. This will be the module name
		String moduleName;
		if (!flags.containsKey(OUTPUT)) {
			if (pathName == null)
				moduleName = "a.out";
			else if (pathName.indexOf('.') != -1) {
				moduleName = pathName.substring(0, pathName.lastIndexOf('.'));
			}else
				moduleName = pathName;			
		} else
			moduleName = flags.get(OUTPUT);
		
		new Classy(moduleName, lines, flags);
	}
	
	private static int addFlag(String flag, int args, Map<String, String> flags, String[] cmd, int at) {
		if (args == 0)
			flags.put(flag, null);
		
		if (at + args >= cmd.length) {
			if (at + args == cmd.length + 1)
				System.err.println("Missing argument to flag: " + flag);
			else
				System.out.println((at + args - cmd.length) + " missing arguments to flag: " + flag + "!");
			return cmd.length;
		} else {
			StringBuffer buf = new StringBuffer();
			boolean first = true;
			for (int i=1; i<=args; i++) {
				if (first)
					first = false;
				else
					buf.append(' ');
				buf.append(cmd[at + i]);
			}
			flags.put(flag, buf.toString());
			return at + args; // skips the arguments
		}
	}
	
	public static void printHelp() {
		Scanner scan = null;
		try {
			scan = new Scanner(new File("flags.txt"));
		} catch (FileNotFoundException e) {
			System.err.println("Could not load \"flags.txt\" help file!");
		}
		while(scan.hasNextLine())
			System.out.println(scan.nextLine());
		scan.close();
	}
	
	public Classy(String moduleName, List<String> lines, Map<String, String> flags) {
		Lexer lex = new Lexer(lines);
		List<Token> tokens = lex.getTokens();
		// strip the whitespace and comment tokens
		// convert all new lines into semicolons, removing excess
		cleanTokens(tokens);
		
		boolean verbose = flags.containsKey(VERBOSE);
		if (verbose) {
			for(Token token: tokens)
				System.out.println(token);
			System.out.println();			
		}
		
		Parser parse = new Parser(tokens);
		Value program = parse.getProgram();
		if (verbose) {
			System.out.println("Parsed:");
			System.out.println(program.pretty(0));
			System.out.println();			
		}
		
		// Just creating the checker object will run the checker and
		// try to catch any type errors that may be present.
		boolean optimize = !flags.containsKey(NO_OPT);
		Checker check = new Checker(program);
		if (verbose) {
			System.out.println("Typed to: " + check.result);
			System.out.println(program.pretty(0));
			System.out.println();
		}
		
		if (optimize) {
			new Optimizer(check, program);
			if (verbose) {
				System.out.println("Optimized:");
				System.out.println(program.pretty(0));
				System.out.println();
			}
		}
		
		Translator translate = new Translator(program, check.getVariables(), check.getTypes());
		List<String> outLines = translate.getOutLines();
		// Output the lines to fileName.ll
		FileWriter fw = null;
		File ll = new File(moduleName + ".ll");
		try {
			fw = new FileWriter(ll);
			
			for (String line: outLines)
				fw.write(line + "\n");
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			if (fw != null) {
				try {
					fw.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		
		if (fw != null) {
			// Now we are going to want to compile to an executable.
			// We use llc first, then gcc.
			//runProcess(List.of("llc", moduleName+".ll", "-o", moduleName+".s"), "Static compiling", verbose);
			//runProcess(List.of("gcc", moduleName+".s", "-o", moduleName), "Linking", verbose);
			
			// Or we can condense into one piped step
			ProcessBuilder llc = new ProcessBuilder("llc", moduleName+".ll",  "-filetype=asm",  "-o", "-");
	    	ProcessBuilder gcc = new ProcessBuilder("gcc", "-x", "assembler", "-", "-o", moduleName);
	    	String[] commands = {"Static compilation", "Assembly and Linking"};
	    	try {
	    		List<Process> whole = ProcessBuilder.startPipeline(List.of(llc, gcc));
	    		for (int i=0; i<whole.size(); i++) {
	    			Process p = whole.get(i);
	    			if (verbose)
	    				System.out.println("Running: " + commands[i]);
	    			int exitCode = p.waitFor();   
	    			if (exitCode != 0)
	    				throw new RuntimeException(commands[i] + " failed, error code: " + exitCode);
	    		}
	    	}catch (Exception e) {
	    		e.printStackTrace();
	    	}
		}
		
		boolean saveDebug = flags.containsKey("save");
		// If saveDebug is not on, then we want to delete the .ll file
		if (!saveDebug) 
			ll.delete();
	}
	
	protected void runProcess(List<String> cmd, String pName, boolean verbose) {
		StringBuffer buf = new StringBuffer();
		for(String cmdp: cmd) {
			buf.append(cmdp);
			buf.append(" ");
		}
		if (verbose)
			System.out.println("Running: " + buf.toString());
		
		ProcessBuilder processBuilder = new ProcessBuilder(cmd);
		processBuilder.redirectErrorStream(true);

		try {
			Process process = processBuilder.start();

			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StringBuilder output = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				output.append('\n');
				output.append(line);
			}
			if (verbose)
				System.out.println(output.toString());

			int exitCode = process.waitFor();
			if (exitCode != 0)
				throw new RuntimeException(pName + " failed, error code: " + exitCode);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	protected void cleanTokens(List<Token> tokens) {
		boolean lastNewLine = true;
		for(int i=0; i<tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.getType() == Token.Type.SPACE || token.getType() == Token.Type.COMMENT) {
				tokens.remove(i);
				i--;
			}else if (token.getType() == Token.Type.NEW_LINE) {
				if (lastNewLine) {
					tokens.remove(i);
					i--;
				}else {
					lastNewLine = true;
					tokens.remove(i);
					tokens.add(i, new Token(token.getValue(), Token.Type.SEMICOLON,
							token.getLineNo(), token.getColNo()));
				}
			}else
				lastNewLine = false;
		}
	}

}
