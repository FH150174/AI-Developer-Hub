package com.fhaicl.aidevhub.project;

import com.fhaicl.aidevhub.ui.Ui;
import com.fhaicl.aidevhub.settings.AppSettings;
import com.fhaicl.aidevhub.git.GitRepositoryChecker;
import com.fhaicl.aidevhub.git.GitException;
import com.fhaicl.aidevhub.git.GitBranchReader;
import com.fhaicl.aidevhub.git.GitWorkingTreeReader;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import com.fhaicl.aidevhub.settings.IdePathSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class ProjectListView extends VBox {
    private final ProjectList projects = new ProjectList();
    private final ProjectStore store;
    private final StringProperty reloadMessage = new SimpleStringProperty("");
    private final BooleanBinding reloadRequired = reloadMessage.isNotEmpty();
    private final CurrentProject currentProject = new CurrentProject();
    private final Consumer<Project> onCurrentProjectChanged;
    private final IdePathSettings ideSettings;

    public ProjectListView(IdePathSettings ideSettings, AppSettings appSettings, Path projectsFile, Consumer<Project> onCurrentProjectChanged) {
        this(ideSettings, appSettings, projectsFile, onCurrentProjectChanged, new ProjectTerminalOpener());
    }

    ProjectListView(IdePathSettings ideSettings, AppSettings appSettings, Path projectsFile,
                    Consumer<Project> onCurrentProjectChanged, ProjectTerminalOpener terminalOpener) {
        super(12);
        this.store = new ProjectStore(projectsFile);
        this.ideSettings = java.util.Objects.requireNonNull(ideSettings);
        this.onCurrentProjectChanged = java.util.Objects.requireNonNull(onCurrentProjectChanged);
        Label title = new Label("Projects");
        title.getStyleClass().add("page-title");
        Label description = new Label("“打开方式”使用已选项目；Notes 里的日志记在当前项目下。");
        description.setWrapText(true);

        TableView<Project> table = new TableView<>();
        table.getStyleClass().add("project-table");
        var selectedItem = table.getSelectionModel().selectedItemProperty();
        var noSelection = selectedItem.isNull();
        var cannotEditSelection = noSelection.or(reloadRequired);
        table.placeholderProperty().bind(Bindings.when(reloadRequired)
                .then(Ui.empty("项目列表暂不可用", "请按上方提示重新加载列表。"))
                .otherwise(Ui.empty("还没有项目", "点击“添加项目”，选择一个本地开发目录开始。")));
        Label selectedLabel = new Label();
        selectedLabel.textProperty().bind(selectedItem.map(project -> "已选：" + project.name()).orElse("已选：无"));
        selectedLabel.tooltipProperty().bind(selectedItem.map(project -> new Tooltip(project.name() + "\n" + project.path())));
        selectedLabel.setMaxWidth(200);
        Label storageStatus = new Label();
        storageStatus.textProperty().bind(reloadMessage);
        storageStatus.setWrapText(true);
        storageStatus.setMinWidth(0);
        Runnable reload = () -> {
            try {
                List<Project> saved = store.load();
                projects.replaceAll(saved);
                synchronizeCurrentProject(saved);
                table.getItems().setAll(saved);
                reloadMessage.set("");
            } catch (IOException | SecurityException exception) {
                reloadMessage.set("项目列表加载失败，已禁用编辑。请检查 " + projectsFile + " 后重新加载列表。原因："
                        + exception.getMessage());
            }
        };
        reload.run();
        Button reloadButton = new Button("重新加载列表");
        reloadButton.setMinWidth(Region.USE_PREF_SIZE);
        reloadButton.setOnAction(event -> reload.run());
        HBox recovery = new HBox(12, storageStatus, reloadButton);
        recovery.setAlignment(Pos.CENTER_LEFT);
        recovery.visibleProperty().bind(reloadRequired);
        recovery.managedProperty().bind(recovery.visibleProperty());
        MenuItem reloadItem = new MenuItem("重新加载列表");
        reloadItem.setOnAction(event -> reload.run());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Project, String> currentColumn = new TableColumn<>("状态");
        currentColumn.getStyleClass().add("project-status");
        currentColumn.setPrefWidth(72);
        currentColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().equals(currentProject.project()) ? "当前" : ""));
        currentColumn.setSortable(false);
        currentColumn.setResizable(false);
        TableColumn<Project, String> nameColumn = new TableColumn<>("项目名称");
        nameColumn.setPrefWidth(200);
        nameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().name()));
        TableColumn<Project, String> pathColumn = new TableColumn<>("项目路径");
        pathColumn.setPrefWidth(440);
        pathColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().path().toString()));
        table.getColumns().add(currentColumn);
        table.getColumns().add(nameColumn);
        table.getColumns().add(pathColumn);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button addButton = new Button("添加项目");
        addButton.getStyleClass().add("primary");
        addButton.setGraphic(Ui.icon("Projects"));
        addButton.disableProperty().bind(reloadRequired);
        addButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择本地项目目录");
            String initial = appSettings.values().projectDirectory();
            if (!initial.isEmpty() && java.nio.file.Files.isDirectory(Path.of(initial))) {
                chooser.setInitialDirectory(Path.of(initial).toFile());
            }
            File directory = chooser.showDialog(getScene().getWindow());
            if (directory == null) {
                return;
            }
            List<Project> before = projects.projects();
            if (!projects.add(directory.toPath())) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "该项目路径已在列表中。");
                Ui.dialog(alert, this);
                alert.setTitle("重复项目");
                alert.setHeaderText(null);
                alert.showAndWait();
                return;
            }
            if (saveChanges(before)) {
                table.getItems().setAll(projects.projects());
            }
        });

        MenuItem removeItem = new MenuItem("从列表移除");
        removeItem.disableProperty().bind(cannotEditSelection);
        removeItem.setOnAction(event -> {
            Project selected = selectedItem.get();
            List<Project> before = projects.projects();
            if (selected != null && projects.remove(selected) && saveChanges(before)) {
                table.getItems().remove(selected);
            }
        });

        MenuItem renameItem = new MenuItem("修改名称");
        renameItem.disableProperty().bind(cannotEditSelection);
        renameItem.setOnAction(event -> {
            Project selected = selectedItem.get();
            if (selected == null) {
                return;
            }
            TextInputDialog dialog = new TextInputDialog(selected.name());
            Ui.dialog(dialog, this);
            dialog.setTitle("修改项目名称");
            dialog.setHeaderText(null);
            dialog.setContentText("项目名称：");
            dialog.showAndWait().ifPresent(name -> {
                if (name.strip().isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "项目名称不能为空或仅包含空白字符。");
                    Ui.dialog(alert, this);
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
        selectButton.disableProperty().bind(cannotEditSelection);
        selectButton.setOnAction(event -> {
            Project selected = selectedItem.get();
            if (selected != null) {
                currentProject.select(selected);
                table.refresh();
                onCurrentProjectChanged.accept(currentProject.project());
            }
        });

        ProjectDirectoryOpener directoryOpener = new ProjectDirectoryOpener();
        MenuItem directoryItem = new MenuItem("打开项目目录");
        directoryItem.setOnAction(event -> {
            Project selected = selectedItem.get();
            if (selected == null) {
                return;
            }
            try {
                directoryOpener.open(selected);
            } catch (IOException | SecurityException | UnsupportedOperationException
                     | IllegalArgumentException exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法打开项目目录。原因：" + exception.getMessage());
                Ui.dialog(alert, this);
                alert.setTitle("打开失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });

        MenuItem terminalItem = new MenuItem("用终端打开");
        terminalItem.setOnAction(event -> {
            Project selected = selectedItem.get();
            if (selected == null) {
                return;
            }
            try {
                terminalOpener.open(selected, appSettings.values().terminal());
            } catch (IOException | SecurityException exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法打开终端。原因：" + exception.getMessage());
                Ui.dialog(alert, this);
                alert.setTitle("打开失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });

        ProjectIdeaOpener ideaOpener = new ProjectIdeaOpener();
        MenuItem ideaItem = new MenuItem("用 IDEA 打开");
        ideaItem.setOnAction(event -> {
            Project selected = selectedItem.get();
            if (selected == null) {
                return;
            }
            try {
                ideaOpener.open(selected, ideSettings.requirePath(IdePathSettings.Ide.IDEA));
            } catch (IOException | SecurityException exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法用 IDEA 打开项目。原因：" + exception.getMessage());
                Ui.dialog(alert, this);
                alert.setTitle("打开失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });

        ProjectPathChecker pathChecker = new ProjectPathChecker();
        MenuItem checkPathItem = new MenuItem("检查路径");
        checkPathItem.disableProperty().bind(noSelection);
        checkPathItem.setOnAction(event -> {
            Project selected = selectedItem.get();
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
            Ui.dialog(alert, this);
            alert.setTitle("路径检查");
            alert.setHeaderText(null);
            alert.showAndWait();
        });

        MenuItem checkGitItem = new MenuItem("检测 Git 仓库");
        checkGitItem.disableProperty().bind(noSelection);
        checkGitItem.setOnAction(event -> {
            Project selected = selectedItem.get();
            if (selected == null) {
                return;
            }
            checkGitItem.disableProperty().unbind();
            checkGitItem.setDisable(true);
            Task<GitRepositoryChecker.Status> task =
                    new Task<>() {
                        @Override
                        protected GitRepositoryChecker.Status call() throws IOException {
                            return new GitRepositoryChecker().check(selected);
                        }
                    };
            task.setOnSucceeded(done -> {
                checkGitItem.disableProperty().bind(noSelection);
                String message = switch (task.getValue()) {
                    case REPOSITORY -> "项目目录属于 Git 仓库。";
                    case NOT_REPOSITORY -> "项目目录不是 Git 仓库。";
                    case MISSING -> "项目路径不存在，可能已被移动或删除。";
                    case NOT_DIRECTORY -> "项目路径存在，但不是目录。";
                };
                Alert alert = new Alert(Alert.AlertType.INFORMATION, message + "\n路径：" + selected.path());
                Ui.dialog(alert, this);
                alert.setTitle("Git 仓库检测");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            task.setOnFailed(done -> {
                checkGitItem.disableProperty().bind(noSelection);
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法检测 Git 仓库。原因：" + GitException.userMessage(task.getException()));
                Ui.dialog(alert, this);
                alert.setTitle("Git 仓库检测失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            Thread thread = new Thread(task, "git-repository-check");
            thread.setDaemon(true);
            thread.start();
        });

        MenuItem branchItem = new MenuItem("获取当前分支");
        branchItem.disableProperty().bind(noSelection);
        branchItem.setOnAction(event -> {
            Project selected = selectedItem.get();
            if (selected == null) {
                return;
            }
            branchItem.disableProperty().unbind();
            branchItem.setDisable(true);
            Task<GitBranchReader.Result> task =
                    new Task<>() {
                        @Override
                        protected GitBranchReader.Result call() throws IOException {
                            return new GitBranchReader().read(selected);
                        }
                    };
            task.setOnSucceeded(done -> {
                branchItem.disableProperty().bind(noSelection);
                String message = switch (task.getValue().status()) {
                    case BRANCH -> "当前分支：" + task.getValue().branchName();
                    case DETACHED_HEAD -> "当前处于 detached HEAD（分离头指针）状态，没有当前分支。";
                    case NOT_REPOSITORY -> "项目目录不是 Git 仓库。";
                    case MISSING -> "项目路径不存在，可能已被移动或删除。";
                    case NOT_DIRECTORY -> "项目路径存在，但不是目录。";
                };
                Alert alert = new Alert(Alert.AlertType.INFORMATION, message + "\n路径：" + selected.path());
                Ui.dialog(alert, this);
                alert.setTitle("当前分支");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            task.setOnFailed(done -> {
                branchItem.disableProperty().bind(noSelection);
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法获取当前分支。原因：" + GitException.userMessage(task.getException()));
                Ui.dialog(alert, this);
                alert.setTitle("获取当前分支失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            Thread thread = new Thread(task, "git-branch-read");
            thread.setDaemon(true);
            thread.start();
        });

        MenuItem workingTreeItem = new MenuItem("获取 Working Tree 状态");
        workingTreeItem.disableProperty().bind(noSelection);
        workingTreeItem.setOnAction(event -> {
            Project selected = selectedItem.get();
            if (selected == null) {
                return;
            }
            workingTreeItem.disableProperty().unbind();
            workingTreeItem.setDisable(true);
            Task<GitWorkingTreeReader.Result> task =
                    new Task<>() {
                        @Override
                        protected GitWorkingTreeReader.Result call() throws IOException {
                            return new GitWorkingTreeReader().readDetails(selected);
                        }
                    };
            task.setOnSucceeded(done -> {
                workingTreeItem.disableProperty().bind(noSelection);
                String message = switch (task.getValue().status()) {
                    case CLEAN -> "工作区干净，没有待提交的更改。";
                    case DIRTY -> "工作区存在更改（包含已暂存、未暂存或未跟踪文件）。";
                    case NO_WORKING_TREE -> "这是裸仓库，没有 Working Tree。";
                    case NOT_REPOSITORY -> "项目目录不是 Git 仓库。";
                    case MISSING -> "项目路径不存在，可能已被移动或删除。";
                    case NOT_DIRECTORY -> "项目路径存在，但不是目录。";
                };
                Alert alert = new Alert(Alert.AlertType.INFORMATION, message + "\n路径：" + selected.path());
                Ui.dialog(alert, this);
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
                workingTreeItem.disableProperty().bind(noSelection);
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "无法获取 Working Tree 状态。原因：" + GitException.userMessage(task.getException()));
                Ui.dialog(alert, this);
                alert.setTitle("获取 Working Tree 状态失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            Thread thread = new Thread(task, "git-working-tree-read");
            thread.setDaemon(true);
            thread.start();
        });

        MenuButton openMenu = new MenuButton("打开方式", null, directoryItem, terminalItem,
                new SeparatorMenuItem(), ideaItem, createEditorItem(table, ProjectEditorOpener.Editor.VS_CODE),
                createEditorItem(table, ProjectEditorOpener.Editor.CURSOR));
        openMenu.disableProperty().bind(noSelection);
        MenuButton moreMenu = new MenuButton("更多", null, renameItem, checkPathItem, checkGitItem,
                branchItem, workingTreeItem, new SeparatorMenuItem(), reloadItem, removeItem);
        FlowPane actions = new FlowPane(12, 8, addButton, selectButton, openMenu, moreMenu, selectedLabel);
        actions.getStyleClass().add("project-actions");
        getChildren().addAll(title, description, actions, recovery, table);
        setAlignment(Pos.TOP_LEFT);
        setMinWidth(0);
        setPadding(new Insets(24));
    }

    private MenuItem createEditorItem(TableView<Project> table, ProjectEditorOpener.Editor editor) {
        ProjectEditorOpener opener = new ProjectEditorOpener(editor);
        MenuItem item = new MenuItem("用 " + editor.displayName() + " 打开");
        item.setOnAction(event -> {
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
                Ui.dialog(alert, this);
                alert.setTitle("打开失败");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });
        return item;
    }

    public int projectCount() {
        return reloadRequired.get() ? -1 : projects.projects().size();
    }

    private boolean saveChanges(List<Project> before) {
        try {
            store.save(before, projects.projects());
            Ui.toast(this, "项目列表已保存。");
            synchronizeCurrentProject(projects.projects());
            return true;
        } catch (ProjectStore.SaveConflictException conflict) {
            projects.replaceAll(before);
            reloadMessage.set("本次修改未保存，已暂停编辑。" + conflict.getMessage());
            return false;
        } catch (IOException | SecurityException exception) {
            projects.replaceAll(before);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "项目列表保存失败，本次修改未生效。原因：" + exception.getMessage());
            Ui.dialog(alert, this);
            alert.setTitle("保存失败");
            alert.setHeaderText(null);
            alert.showAndWait();
            return false;
        }
    }

    private void synchronizeCurrentProject(List<Project> saved) {
        Project previous = currentProject.project();
        currentProject.synchronize(saved);
        if (!java.util.Objects.equals(previous, currentProject.project())) {
            onCurrentProjectChanged.accept(currentProject.project());
        }
    }
}
