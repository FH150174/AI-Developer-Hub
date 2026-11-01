package com.fhaicl.aidevhub.git;

import com.fhaicl.aidevhub.project.Project;
import com.fhaicl.aidevhub.project.ProjectStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class GitWorkflowIntegrationTest {
    @TempDir Path directory;

    @Test void restoredProjectSupportsRepositoryBranchCommitAndWorkingTreeQueriesWithoutMutatingMetadata() throws Exception {
        Path repository = Files.createDirectories(directory.resolve("中文 repository with spaces"));
        assertEquals(0, new GitCommandExecutor().execute(repository, "init", "--initial-branch=feature/中文").exitCode());
        Path nested = Files.createDirectories(repository.resolve("子目录 with spaces"));
        Path data = directory.resolve("data/projects.xml");
        new ProjectStore(data).save(List.of(new Project("stored project", nested)));
        Project project = new ProjectStore(data).load().getFirst();
        Map<Path, String> before = metadata(repository.resolve(".git"));
        assertEquals(GitRepositoryChecker.Status.REPOSITORY, new GitRepositoryChecker().check(project));
        assertEquals(new GitBranchReader.Result(GitBranchReader.Status.BRANCH, "feature/中文"), new GitBranchReader().read(project));
        assertEquals(GitLatestCommitReader.Status.NO_COMMITS, new GitLatestCommitReader().read(project).status());
        assertEquals(GitWorkingTreeReader.Status.CLEAN, new GitWorkingTreeReader().read(project));
        Path changed = Files.writeString(repository.resolve("中文 change with spaces.txt"), "内容");
        var changes = new GitWorkingTreeReader().readDetails(project);
        assertEquals(GitWorkingTreeReader.Status.DIRTY, changes.status());
        assertEquals(List.of(new GitWorkingTreeReader.FileChange("中文 change with spaces.txt", "", "??")), changes.files());
        Files.delete(changed);
        assertEquals(GitWorkingTreeReader.Status.CLEAN, new GitWorkingTreeReader().read(project));
        assertEquals(before, metadata(repository.resolve(".git")));
        assertFalse(Files.exists(repository.resolve(".git/index.lock")));
    }

    @Test void brokenRepositoryDoesNotPreventReadingAnotherProjectOrNonRepository() throws Exception {
        Path broken = Files.createDirectory(directory.resolve("broken"));
        Path healthy = Files.createDirectory(directory.resolve("healthy"));
        for (Path path : List.of(broken, healthy)) {
            assertEquals(0, new GitCommandExecutor().execute(path, "init", "--initial-branch=main").exitCode());
        }
        Files.writeString(broken.resolve(".git/config"), "[broken");
        Project failed = new Project("broken", broken);
        assertThrows(IOException.class, () -> new GitRepositoryChecker().check(failed));
        assertThrows(IOException.class, () -> new GitBranchReader().read(failed));
        assertThrows(IOException.class, () -> new GitWorkingTreeReader().read(failed));
        assertEquals("[broken", Files.readString(broken.resolve(".git/config")));
        assertEquals("main", new GitBranchReader().read(new Project("healthy", healthy)).branchName());
        Path ordinary = Files.createDirectory(directory.resolve("ordinary"));
        assertEquals(GitRepositoryChecker.Status.NOT_REPOSITORY,
                new GitRepositoryChecker().check(new Project("ordinary", ordinary)));
    }

    private Map<Path, String> metadata(Path git) throws IOException {
        Map<Path, String> snapshot = new TreeMap<>();
        try (var files = Files.walk(git)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                snapshot.put(git.relativize(file), Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
            }
        }
        return snapshot;
    }
}
