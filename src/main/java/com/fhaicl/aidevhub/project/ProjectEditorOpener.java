package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ProjectEditorOpener {
    public enum Editor {
        VS_CODE("VS Code", "Code.exe"),
        CURSOR("Cursor", "Cursor.exe");

        private final String displayName;
        private final String executableName;

        Editor(String displayName, String executableName) {
            this.displayName = displayName;
            this.executableName = executableName;
        }

        public String displayName() {
            return displayName;
        }

        public String executableName() {
            return executableName;
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        void start(ProcessBuilder builder) throws IOException;
    }

    private final Editor editor;
    private final ProcessStarter starter;

    public ProjectEditorOpener(Editor editor) {
        this(editor, builder -> builder.start());
    }

    ProjectEditorOpener(Editor editor, ProcessStarter starter) {
        this.editor = Objects.requireNonNull(editor);
        this.starter = Objects.requireNonNull(starter);
    }

    public void open(Project project, Path executable) throws IOException {
        Objects.requireNonNull(project, "Project must not be null");
        Objects.requireNonNull(executable, "Editor executable must not be null");
        Path directory = project.path().toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IOException("项目目录不存在或无法访问：" + directory);
        }
        Path program = executable.toAbsolutePath().normalize();
        if (!Files.isRegularFile(program) || program.getFileName() == null
                || !program.getFileName().toString().equalsIgnoreCase(editor.executableName())) {
            throw new IOException("请选择 " + editor.displayName() + " 安装目录中的 " + editor.executableName() + "。");
        }
        ProcessBuilder builder = new ProcessBuilder(program.toString(), directory.toString());
        builder.directory(directory.toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        // Electron executables can inherit this flag when the hub is launched from an editor terminal.
        builder.environment().remove("ELECTRON_RUN_AS_NODE");
        starter.start(builder);
    }
}
