package com.fhaicl.aidevhub.agent;

import com.fhaicl.aidevhub.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ProjectContextService {
    public String context(Project project) throws IOException {
        Objects.requireNonNull(project);
        Path root = project.path().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("项目目录不存在或无法访问。");
        StringBuilder text = new StringBuilder("项目名称：").append(project.name())
                .append("\n项目路径（Windows）：").append(root)
                .append("\n请先阅读下列现有项目文档，遵守项目规则，再等待用户给出具体任务。\n")
                .append("不要仅凭启动上下文修改文件或执行 Git 写操作。\n现有文档（相对项目目录）：\n");
        boolean found = false;
        for (String relative : List.of("AGENTS.md", "CLAUDE.md", "README.md",
                "docs/REQUIREMENTS.md", "docs/ARCHITECTURE.md", "docs/DEVELOPMENT.md", "docs/ROADMAP.md")) {
            if (Files.isRegularFile(root.resolve(relative))) {
                text.append("- ").append(relative).append('\n');
                found = true;
            }
        }
        if (!found) text.append("（未找到约定文档，不应假设文档存在。）\n");
        return text.toString();
    }
}
