package com.fhaicl.aidevhub.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ProjectEditorOpenerTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(ProjectEditorOpener.Editor.class)
    void passesSpecialCharacterPathsAsSeparateArguments(ProjectEditorOpener.Editor editor) throws IOException {
        Path installation = Files.createDirectory(directory.resolve("编辑器 tools & apps"));
        Path executable = Files.createFile(installation.resolve(editor.executableName()));
        Path project = Files.createDirectory(directory.resolve("中文 project & %PATH%"));
        AtomicReference<ProcessBuilder> started = new AtomicReference<>();

        new ProjectEditorOpener(editor, started::set)
                .open(new Project("project", project.resolve(".")), executable);

        assertEquals(List.of(executable.toAbsolutePath().toString(), project.toAbsolutePath().toString()),
                started.get().command());
        assertEquals(project.toAbsolutePath().toFile(), started.get().directory());
        assertFalse(started.get().environment().containsKey("ELECTRON_RUN_AS_NODE"));
    }

    @ParameterizedTest
    @EnumSource(ProjectEditorOpener.Editor.class)
    void rejectsMissingProjectAndFileWithoutStarting(ProjectEditorOpener.Editor editor) throws IOException {
        Path executable = Files.createFile(directory.resolve(editor.executableName()));
        ProjectEditorOpener opener = new ProjectEditorOpener(editor, builder -> fail("Must not start"));

        assertThrows(IOException.class,
                () -> opener.open(new Project("missing", directory.resolve("missing")), executable));
        assertThrows(IOException.class,
                () -> opener.open(new Project("file", executable), executable));
    }

    @ParameterizedTest
    @EnumSource(ProjectEditorOpener.Editor.class)
    void rejectsMissingExecutableDirectoryAndOtherEditorsExecutable(ProjectEditorOpener.Editor editor)
            throws IOException {
        String otherName = editor == ProjectEditorOpener.Editor.VS_CODE ? "Cursor.exe" : "Code.exe";
        Path otherExecutable = Files.createFile(directory.resolve(otherName));
        Project project = new Project("project", directory);
        ProjectEditorOpener opener = new ProjectEditorOpener(editor, builder -> fail("Must not start"));

        assertThrows(IOException.class, () -> opener.open(project, directory.resolve(editor.executableName())));
        assertThrows(IOException.class, () -> opener.open(project, directory));
        assertThrows(IOException.class, () -> opener.open(project, otherExecutable));
    }

    @ParameterizedTest
    @EnumSource(ProjectEditorOpener.Editor.class)
    void preservesStartupFailure(ProjectEditorOpener.Editor editor) throws IOException {
        Path executable = Files.createFile(directory.resolve(editor.executableName()));
        IOException failure = new IOException("Cannot start editor");
        ProjectEditorOpener opener = new ProjectEditorOpener(editor, builder -> { throw failure; });

        assertSame(failure, assertThrows(IOException.class,
                () -> opener.open(new Project("project", directory), executable)));
    }

    @ParameterizedTest
    @EnumSource(ProjectEditorOpener.Editor.class)
    void rejectsNullArguments(ProjectEditorOpener.Editor editor) {
        ProjectEditorOpener opener = new ProjectEditorOpener(editor, builder -> fail("Must not start"));
        assertThrows(NullPointerException.class, () -> opener.open(null, directory));
        assertThrows(NullPointerException.class, () -> opener.open(new Project("project", directory), null));
    }
}
