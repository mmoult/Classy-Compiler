package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import classy.compiler.Classy;
import classy.compiler.analyzing.CheckException;

class IntegrationTest {
	
	private void expectFromProgram(List<String> lines, int result) {
		Map<String, String> flags = new HashMap<>();
		new Classy("a.exe", lines, flags);
		ProcessResult res = runProcess(List.of("a.exe"));
		// If everything went well, then the result should be 6, with an exit code of 0
		assertEquals(0, res.exitCode);
		assertEquals("\n"+result, res.output);
		File file = new File("a.exe");
		file.delete(); // dispose of the file we made
	}
	
	@Test
	void testLiteral() {
		List<String> lines = List.of(
			"-2"
		);
		expectFromProgram(lines, -2);
	}
	
	@Test
	void testNestedFunction() {
		List<String> lines = List.of(
			"let foo(x, y, z) = {",
			"	let bar(x, y, z) = {",
			"		x + y + z",
			"	}",
			"	bar(x, y, z)",
			"}",
			"foo (1, 2, 3)"
		);
		expectFromProgram(lines, 6);
	}
	
	@Test
	void testVarBlock() {
		List<String> lines = List.of(
			"let foo = 10",
			"foo"
		);
		expectFromProgram(lines, 10);
	}
	
	@Test
	void testFunction() {
		List<String> lines = List.of(
			"let negate(num) = -num",
			"negate(-1)"
		);
		expectFromProgram(lines, 1);
	}
	
	@Test
	void testOperators() {
		List<String> lines = List.of(
			"3 - 6 / 2 <> 1"
		);
		expectFromProgram(lines, 1);
	}
	
	@Test
	void testParentheses() {
		List<String> lines = List.of(
			"2 * (3 - 5) >= 0"
		);
		expectFromProgram(lines, 0);
	}
	
	@Test
	void testUndefinedVar() {
		List<String> lines = List.of(
			"let fx(a) = {",
			"	a + 3",
			"}",
			"fx(1) - a"
		);
		assertThrows(CheckException.class, () -> {expectFromProgram(lines, 1);});
	}
	
	@Test
	void testVoidParamFunc() {
		List<String> lines = List.of(
			"let voidcaller() = {",
			"	7",
			"}",
			"voidcaller void"
		);
		expectFromProgram(lines, 7);
	}
	
	protected ProcessResult runProcess(List<String> cmd) {
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
			System.out.println(output.toString());

			int exitCode = process.waitFor();
			return new ProcessResult(exitCode, output.toString());
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return new ProcessResult(-1, null);
	}
	
	protected static class ProcessResult {
		public final int exitCode;
		public final String output;
		
		public ProcessResult(int exitCode, String output) {
			this.exitCode = exitCode;
			this.output = output;
		}
	}
}
