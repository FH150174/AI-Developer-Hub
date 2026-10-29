package com.fhaicl.aidevhub.project;

import com.fhaicl.aidevhub.git.GitRepositoryChecker;
import com.fhaicl.aidevhub.git.GitException;
import com.fhaicl.aidevhub.git.GitBranchReader;
import com.fhaicl.aidevhub.git.GitWorkingTreeReader;
import javafx.concurrent.Task;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import com.fhaicl.aidevhub.settings.IdePathSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public final class ProjectListView extends VBox {
    private final ProjectList projects = new ProjectList();
    private final ProjectStore store = new ProjectStore(
            Path.of(System.getProperty("user.home"), ".aidevhub", "projects.xml"));
    private final BooleanProperty loadFailed = new SimpleBooleanProperty(false);
    private final CurrentProject currentProject = new CurrentProject();
    private final Consumer<Project> onCurrentProjectChanged;
    private final IdePathSettings ideSettings;

    public ProjectListView(IdePathSettings ideSettings, Consumer<Project> onCurrentProjectChanged) {
        super(12);
        this.ideSettings = java.util.Objects.requireNonNull(ideSettings);
        this.onCurrentProjectChanged = java.util.Objects.requireNonNull(onCurrentProjectChanged);
        Label title = new Label("Projects");
        title.setFont(Font.font(24));
        Label description = new Label("集中管理本地开发项目。");
        description.setWrapText(true);

        TableView<Project> table = new TableView<>();
        table.setPlaceholder(new Label("暂无项目"));
        Label storageStatus = new Label();
        storageStatus.setWrapText(true);
        try {
            projects.replaceAll(store.load());
            table.getItems().setAll(projects.projects());
        } catch (IOException | SecurityException exception) {
            loadFailed.set(true);
            storageStatus.setText("项目列表加载失败，已禁用编辑以保护原文件。请检查用户目录下的 .aidevhub/projects.xml 后重启。原因："
                    + exception.getMessage());
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Project, String> nameColumn = new TableColumn<>("项目名称");
        nameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().name()));
        TableColumn<Project, String> pathColumn = new TableColumn<>("项目路径");
        pathColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().path().toString()));
        table.getColumns().add(nameColumn);
        table.getColumns().add(pathColumn);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button addButton = new Button("添加项目");
        addButton.disableProperty().bind(loadFailed);
        addButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择本地项目目录");
            File directory = chooser.showDialog(getScene().getWindow());
            if (directory == null) {
                return;
            }
            List<Project> before = projects.projects();
            if (!projects.add(directory.toPath())) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "该项目路径已在列表中。");
                alert.initOwner(getScene().getWindow());
                alert.setTitle("重复项目");
                alert.setHeaderText(null);
                alert.showAndWait();
                return;
            }
            if (saveChanges(before)) {
                table.getItems().setAll(projects.projects());
            }
        });

        Button removeButton = new Button("删除项目");
        removeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(loadFailed));
        removeButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            List<Project> before = projects.projects();
            if (selected != null && projects.remove(selected) && saveChanges(before)) {
                table.getItems().remove(selected);
            }
        });

        Button renameButton = new Button("修改名称");
        renameButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(loadFailed));
        renameButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            TextInputDialog dialog = new TextInputDialog(selected.name());
            dialog.initOwner(getScene().getWindow());
            dialog.setTitle("修改项目名称");
            dialog.setHeaderText(null);
            dialog.setContentText("项目名称：");
            dialog.showAndWait().ifPresent(name -> {
                if (name.strip().isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "项目名称不能为空或仅包含空白字符。");
                    alert.initOwner(getScene().getWindow());
                    alert.setTitle("名称无效");
                    alert.setHeaderText(null);
                    alert.showAndWait();
                    return;
                }
                List<Project> before = projects.projects();
                Project renamed = projects.rename(selected, name);
                if (!saveChanges(before)) {
                    return;
                }
                table.getItems().setAll(projects.projects());
                table.getSelectionModel().select(renamed);
            });
        });

        Button selectButton = new Button("设为当前项目");
        selectButton.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull().or(loadFailed));
        selectButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                currentProject.select(selected);
                onCurrentProjectChanged.accept(currentProject.project());
            }
        });

        ProjectDirectoryOpener directoryOpener = new ProjectDirectoryOpener();
        Button openButton = new Button("打开项目目录");
        openButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        openButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            try {
                directoryOpener.open(selected);
            } catch (IOException | SecurityException | UnsupportedOperationException
                     | IllegalArgumentException exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法打开项目目录。原因：" + exception.getMessage());
                alert.initOwner(getScene().getWindow());
                alert.setTitle("打开失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });

        ProjectTerminalOpener terminalOpener = new ProjectTerminalOpener();
        Button terminalButton = new Button("用终端打开");
        terminalButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        terminalButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            try {
                terminalOpener.open(selected);
            } catch (IOException | SecurityException exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法打开终端。原因：" + exception.getMessage());
                alert.initOwner(getScene().getWindow());
                alert.setTitle("打开失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });

        ProjectIdeaOpener ideaOpener = new ProjectIdeaOpener();
        Button ideaButton = new Button("用 IDEA 打开");
        ideaButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        ideaButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            try {
                ideaOpener.open(selected, ideSettings.requirePath(IdePathSettings.Ide.IDEA));
            } catch (IOException | SecurityException exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法用 IDEA 打开项目。原因：" + exception.getMessage());
                alert.initOwner(getScene().getWindow());
                alert.setTitle("打开失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });

        ProjectPathChecker pathChecker = new ProjectPathChecker();
        Button checkPathButton = new Button("检查路径");
        checkPathButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        checkPathButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            Alert alert;
            try {
                ProjectPathChecker.Status status = pathChecker.check(selected);
                String message = switch (status) {
                    case DIRECTORY -> "项目目录存在。";
                    case MISSING -> "项目路径不存在，可能已被移动或删除。";
                    case NOT_DIRECTORY -> "项目路径存在，但不是目录。";
                };
                alert = new Alert(status == ProjectPathChecker.Status.DIRECTORY
                        ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                        message + "\n路径：" + selected.path());
            } catch (IOException | SecurityException exception) {
                alert = new Alert(Alert.AlertType.ERROR,
                        "无法检查项目路径，请检查访问权限或磁盘状态。\n路径：" + selected.path()
                                + "\n原因：" + exception.getMessage());
            }
            alert.initOwner(getScene().getWindow());
            alert.setTitle("路径检查");
            alert.setHeaderText(null);
            alert.showAndWait();
        });

        Button checkGitButton = new Button("检测 Git 仓库");
        checkGitButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        checkGitButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            checkGitButton.disableProperty().unbind();
            checkGitButton.setDisable(true);
            Task<GitRepositoryChecker.Status> task =
                    new Task<>() {
                        @Override
                        protected GitRepositoryChecker.Status call() throws IOException {
                            return new GitRepositoryChecker().check(selected);
                        }
                    };
            task.setOnSucceeded(done -> {
                checkGitButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
                String message = switch (task.getValue()) {
                    case REPOSITORY -> "项目目录属于 Git 仓库。";
                    case NOT_REPOSITORY -> "项目目录不是 Git 仓库。";
                    case MISSING -> "项目路径不存在，可能已被移动或删除。";
                    case NOT_DIRECTORY -> "项目路径存在，但不是目录。";
                };
                Alert alert = new Alert(Alert.AlertType.INFORMATION, message + "\n路径：" + selected.path());
                alert.initOwner(getScene().getWindow());
                alert.setTitle("Git 仓库检测");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            task.setOnFailed(done -> {
                checkGitButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法检测 Git 仓库。原因：" + GitException.userMessage(task.getException()));
                alert.initOwner(getScene().getWindow());
                alert.setTitle("Git 仓库检测失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            Thread thread = new Thread(task, "git-repository-check");
            thread.setDaemon(true);
            thread.start();
        });

        Button branchButton = new Button("获取当前分支");
        branchButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        branchButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            branchButton.disableProperty().unbind();
            branchButton.setDisable(true);
            Task<GitBranchReader.Result> task =
                    new Task<>() {
                        @Override
                        protected GitBranchReader.Result call() throws IOException {
                            return new GitBranchReader().read(selected);
                        }
                    };
            task.setOnSucceeded(done -> {
                branchButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
                String message = switch (task.getValue().status()) {
                    case BRANCH -> "当前分支：" + task.getValue().branchName();
                    case DETACHED_HEAD -> "当前处于 detached HEAD（分离头指针）状态，没有当前分支。";
                    case NOT_REPOSITORY -> "项目目录不是 Git 仓库。";
                    case MISSING -> "项目路径不存在，可能已被移动或删除。";
                    case NOT_DIRECTORY -> "项目路径存在，但不是目录。";
                };
                Alert alert = new Alert(Alert.AlertType.INFORMATION, message + "\n路径：" + selected.path());
                alert.initOwner(getScene().getWindow());
                alert.setTitle("当前分支");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            task.setOnFailed(done -> {
                branchButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法获取当前分支。原因：" + GitException.userMessage(task.getException()));
                alert.initOwner(getScene().getWindow());
                alert.setTitle("获取当前分支失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            Thread thread = new Thread(task, "git-branch-read");
            thread.setDaemon(true);
            thread.start();
        });

        Button workingTreeButton = new Button("获取 Working Tree 状态");
        workingTreeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        workingTreeButton.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            workingTreeButton.disableProperty().unbind();
            workingTreeButton.setDisable(true);
            Task<GitWorkingTreeReader.Result> task =
                    new Task<>() {
                        @Override
                        protected GitWorkingTreeReader.Result call() throws IOException {
                            return new GitWorkingTreeReader().readDetails(selected);
                        }
                    };
            task.setOnSucceeded(done -> {
                workingTreeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
                String message = switch (task.getValue().status()) {
                    case CLEAN -> "工作区干净，没有待提交的更改。";
                    case DIRTY -> "工作区存在更改（包含已暂存、未暂存或未跟踪文件）。";
                    case NO_WORKING_TREE -> "这是裸仓库，没有 Working Tree。";
                    case NOT_REPOSITORY -> "项目目录不是 Git 仓库。";
                    case MISSING -> "项目路径不存在，可能已被移动或删除。";
                    case NOT_DIRECTORY -> "项目路径存在，但不是目录。";
                };
                Alert alert = new Alert(Alert.AlertType.INFORMATION, message + "\n路径：" + selected.path());
                alert.initOwner(getScene().getWindow());
                alert.setTitle("Working Tree 状态");
                if (!task.getValue().files().isEmpty()) {
                    TableView<GitWorkingTreeReader.FileChange> changes = new TableView<>();
                    TableColumn<GitWorkingTreeReader.FileChange, String> category = new TableColumn<>("类型");
                    category.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().category()));
                    TableColumn<GitWorkingTreeReader.FileChange, String> code = new TableColumn<>("暂存区 / 工作区");
                    code.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().code()));
                    TableColumn<GitWorkingTreeReader.FileChange, String> path = new TableColumn<>("仓库相对路径");
                    path.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().path()));
                    TableColumn<GitWorkingTreeReader.FileChange, String> original = new TableColumn<>("原路径");
                    original.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().originalPath()));
                    changes.getColumns().add(category);
                    changes.getColumns().add(code);
                    changes.getColumns().add(path);
                    changes.getColumns().add(original);
                    changes.getItems().setAll(task.getValue().files());
                    changes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
                    changes.setPrefSize(800, 360);
                    alert.getDialogPane().setContent(new VBox(12,
                            new Label(message + "\n路径：" + selected.path()),
                            new Label("状态码：第一位为暂存区，第二位为工作区；? 表示未跟踪。"), changes));
                    alert.setResizable(true);
                }
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            task.setOnFailed(done -> {
                workingTreeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法获取 Working Tree 状态。原因：" + GitException.userMessage(task.getException()));
                alert.initOwner(getScene().getWindow());
                alert.setTitle("获取 Working Tree 状态失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            Thread thread = new Thread(task, "git-working-tree-read");
            thread.setDaemon(true);
            thread.start();
        });

        getChildren().addAll(title, description, storageStatus,
                new HBox(12, addButton, renameButton, removeButton, selectButton, checkPathButton),
                new HBox(12, openButton, terminalButton, ideaButton, checkGitButton),
                new HBox(12, createEditorButton(table, ProjectEditorOpener.Editor.VS_CODE),
                        createEditorButton(table, ProjectEditorOpener.Editor.CURSOR), branchButton),
                new HBox(12, workingTreeButton), table);
        setAlignment(Pos.TOP_LEFT);
        setMinWidth(0);
        setPadding(new Insets(24));
    }

    private Button createEditorButton(TableView<Project> table, ProjectEditorOpener.Editor editor) {
        ProjectEditorOpener opener = new ProjectEditorOpener(editor);
        Button button = new Button("用 " + editor.displayName() + " 打开");
        button.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        button.setOnAction(event -> {
            Project selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            IdePathSettings.Ide ide = editor == ProjectEditorOpener.Editor.VS_CODE
                    ? IdePathSettings.Ide.VS_CODE : IdePathSettings.Ide.CURSOR;
            try {
                opener.open(selected, ideSettings.requirePath(ide));
            } catch (IOException | SecurityException exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法用 " + editor.displayName() + " 打开项目。原因：" + exception.getMessage());
                alert.initOwner(getScene().getWindow());
                alert.setTitle("打开失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });
        return button;
    }

    public int projectCount() {
        return loadFailed.get() ? -1 : projects.projects().size();
    }

    private boolean saveChanges(List<Project> before) {
        try {
            store.save(projects.projects());
            currentProject.synchronize(projects.projects());
            onCurrentProjectChanged.accept(currentProject.project());
            return true;
        } catch (IOException | SecurityException exception) {
            projects.replaceAll(before);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "项目列表保存失败，本次修改未生效。原因：" + exception.getMessage());
            alert.initOwner(getScene().getWindow());
            alert.setTitle("保存失败");
            alert.setHeaderText(null);
            alert.showAndWait();
            return false;
        }
    }
}
