package com.fhaicl.aidevhub.project;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ProjectDirectoryOpener {
    @FunctionalInterface
    interface DirectoryOpener {
        void open(Path directory) throws IOException;
    }

    private final DirectoryOpener opener;

    public ProjectDirectoryOpener() {
        this(directory -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException("当前环境不支持打开文件管理器。");
            }
            Desktop.getDesktop().open(directory.toFile());
        });
    }

    ProjectDirectoryOpener(DirectoryOpener opener) {
        this.opener = Objects.requireNonNull(opener);
    }

    public void open(Project project) throws IOException {
        Objects.requireNonNull(project, "Project must not be null");
        Path directory = project.path().toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IOException("项目目录不存在或无法访问：" + directory);
        }
        opener.open(directory);
    }
}
