package com.fhaicl.aidevhub.support;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** A test-only command that fills both streams then deliberately waits past the execution deadline. */
public final class SlowCommand {
    private SlowCommand() { }
    public static void main(String[] arguments) throws InterruptedException {
        System.out.print("x".repeat(131072));
        System.err.print("y".repeat(131072));
        System.out.flush();
        System.err.flush();
        Thread.sleep(30000);
    }

    public static List<String> command() {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                SlowCommand.class.getName());
    }

    public static ProcessHandle child(AtomicBoolean destroyed) {
        return (ProcessHandle) java.lang.reflect.Proxy.newProxyInstance(ProcessHandle.class.getClassLoader(),
                new Class<?>[]{ProcessHandle.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("destroyForcibly")) {
                        destroyed.set(true);
                        return true;
                    }
                    throw new AssertionError("Unexpected ProcessHandle call: " + method.getName());
                });
    }
}
