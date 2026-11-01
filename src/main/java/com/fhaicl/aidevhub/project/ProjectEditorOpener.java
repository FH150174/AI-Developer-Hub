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
        if (!Files.isRegularFile(program)) {
            throw new IOException("未找到 " + editor.displayName()
                    + " 启动程序，可能已卸载或路径已变更。请安装该 IDE，或在 Settings 中重新设置路径。");
        }
        if (program.getFileName() == null
                || !program.getFileName().toString().equalsIgnoreCase(editor.executableName())) {
            throw new IOException("请选择 " + editor.displayName() + " 安装目录中的 " + editor.executableName() + "。");
        }
        ProcessBuilder builder = new ProcessBuilder(program.toString(), directory.toString());
        builder.directory(directory.toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        // Electron executables can inherit this flag when the hub is launched from an editor terminal.
        builder.environment().remove("ELECTRON_RUN_AS_NODE");
        try {
            starter.start(builder);
        } catch (IOException exception) {
            throw new IOException("无法启动 " + editor.displayName()
                    + "，请检查程序是否可运行及访问权限，并在 Settings 中确认路径。", exception);
        }
    }
}
