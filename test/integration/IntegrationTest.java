package integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import classy.compiler.Classy;

class IntegrationTest {
	
	@Test
	void testNestedFunction() {
		Map<String, String> flags = new HashMap<>();
		
		List<String> lines = List.of(
			"let foo(x, y, z) = {",
			"	let bar(x, y, z) = {",
			"		x + y + z",
			"	}",
			"bar(x, y, z)",
			"}",
			"foo (1, 2, 3)"
			);
		new Classy("a.exe", lines, flags);
		assertEquals(0, runProcess(List.of("a.exe")));
		File file = new File("a.exe");
		file.delete(); // dispose of the file we made
	}
	
	protected int runProcess(List<String> cmd) {
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

			return process.waitFor();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return -1;
	}
}
