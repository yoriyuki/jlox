package io.github.yoriyuki.jlox;
import java.io.*;
import java.util.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;


public class LoxTestRunner {
    static class TestExpectation {
        List<String> expectedLines = new ArrayList<>();
        String runtimeError = null;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java LoxTestRunner <file> or <dir> ...");
            System.exit(64);
        }

        for (String arg : args) {
            Path top = Paths.get(arg);
            try (Stream<Path> paths = Files.walk(top)) {
                paths.filter(Files::isRegularFile) // ファイルだけに絞る
                        .filter(path -> path.getFileName().toString().endsWith(".lox"))
                        .forEach(path -> {
                            try {
                                runTest(path);// ここにファイル処理を書く
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            System.out.println("見つけたファイル: " + path.toString());
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static TestExpectation extractExpectedOutput(File testFile) throws IOException {
        TestExpectation result = new TestExpectation();

        try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("// expect:")) {
                    result.expectedLines.add(line.split("// expect:")[1].trim());
                } else if (line.contains("// expect runtime error:")) {
                    result.runtimeError = line.split("// expect runtime error:")[1]
                            .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
                } else if (line.contains("Error at")) {
                    result.expectedLines.add(line.split("//")[1].trim());
                }
            }
        }

        return result;
    }

    static void runTest(Path path) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-cp", "out/production/jlox",
                "com.craftinginterpreters.lox.Lox",
                path.toString()
        );
//        builder.redirectInput(testFile);
        builder.redirectErrorStream(true);

        Process process = builder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        List<String> output = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            output.add(line.trim());
        }

        int exitCode = 0;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Process interrupted: " + path, e);
        }

        TestExpectation expectation = extractExpectedOutput(path.toFile());

        if (expectation.runtimeError != null && exitCode != 0) {
            // 実行時エラーが期待されている場合は、それが含まれていたかチェック
            boolean found = output.stream().anyMatch(l -> l.contains(expectation.runtimeError));
            if (!found) {
                System.out.println("FAIL (missing runtime error): " + path);
                System.out.println("Expected error: " + expectation.runtimeError);
                System.out.println("Got: " + output);
            }
            return;
        }

        int min = Math.min(expectation.expectedLines.size(), output.size());
        boolean passed = true;

        for (int i = 0; i < min; i++) {
            if (!output.get(i).contains(expectation.expectedLines.get(i))) {
                passed = false;
                System.out.println("Mismatch at line " + i);
                System.out.println("Expected: " + expectation.expectedLines.get(i));
                System.out.println("Got:      " + output.get(i));
            }
        }

// 余剰チェック
        if (expectation.expectedLines.size() != output.size()) {
            passed = false;
            System.out.println("Expected lines: " + expectation.expectedLines.size());
            System.out.println("Got lines:      " + output.size());
        }

// 最終的な判定
        if (passed) {
            System.out.println("PASS: " + path.toString());
        } else {
            System.out.println("FAIL: " + path.toString());
        }
    }
}