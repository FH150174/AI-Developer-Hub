package com.fhaicl.aidevhub.navigation;

import com.fhaicl.aidevhub.ui.Ui;
import com.fhaicl.aidevhub.settings.AppSettings;
import com.fhaicl.aidevhub.settings.AppSettingsView;
import com.fhaicl.aidevhub.project.ProjectListView;
import com.fhaicl.aidevhub.agent.AgentsView;
import com.fhaicl.aidevhub.agent.AgentPathSettings;
import com.fhaicl.aidevhub.agent.AgentSettingsView;
import com.fhaicl.aidevhub.environment.EnvironmentView;
import com.fhaicl.aidevhub.dashboard.DashboardView;
import com.fhaicl.aidevhub.todo.ProjectTodoService;
import com.fhaicl.aidevhub.todo.ProjectTodoView;
import com.fhaicl.aidevhub.todo.ProjectTodoStore;
import com.fhaicl.aidevhub.notes.ProjectLogsView;
import com.fhaicl.aidevhub.notes.ProjectLogStore;
import com.fhaicl.aidevhub.git.GitInstallationView;
import com.fhaicl.aidevhub.git.GitBranchView;
import com.fhaicl.aidevhub.git.GitLatestCommitView;
import com.fhaicl.aidevhub.git.GitChangeCountView;
import com.fhaicl.aidevhub.settings.IdePathSettings;
import com.fhaicl.aidevhub.settings.IdeSettingsView;
import java.nio.file.Path;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainView extends BorderPane {

    public MainView(Path dataDirectory) {
        AppSettings appSettings = new AppSettings(
                dataDirectory.resolve("settings.xml"));
        String loadError = "";
        try {
            appSettings.load();
        } catch (java.io.IOException | SecurityException exception) {
            loadError = "应用设置加载失败，已禁用保存。请检查 " + dataDirectory.resolve("settings.xml") + " 后重启：" + exception.getMessage();
        }
        Runnable applyTheme = () -> Ui.theme(this, appSettings.values().theme());
        applyTheme.run();
        IdePathSettings ideSettings = new IdePathSettings(
                dataDirectory.resolve("ide-paths.xml"));
        IdeSettingsView settingsView = new IdeSettingsView(ideSettings);
        settingsView.getChildren().add(2, new AppSettingsView(appSettings, applyTheme, loadError));
        AgentPathSettings agentSettings = new AgentPathSettings(
                dataDirectory.resolve("agent-paths.xml"));
        settingsView.getChildren().add(new AgentSettingsView(agentSettings));
        ProjectLogStore logStore = new ProjectLogStore(
                dataDirectory.resolve("notes"));
        ProjectLogsView notesView = new ProjectLogsView(logStore);
        ProjectTodoStore todoStore = new ProjectTodoStore(
                dataDirectory.resolve("todos"));
        ProjectTodoView todoView = new ProjectTodoView(todoStore);
        DashboardView dashboardView = new DashboardView(new ProjectTodoService(todoStore), logStore, ideSettings, appSettings);
        Label currentProject = new Label("未选择项目");
        GitBranchView branchView = new GitBranchView();
        GitLatestCommitView commitView = new GitLatestCommitView();
        GitChangeCountView countView = new GitChangeCountView();
        VBox.setVgrow(countView, Priority.ALWAYS);
        GitInstallationView gitView = new GitInstallationView();
        Runnable refreshGit = () -> {
            branchView.refresh();
            commitView.refresh();
            countView.refresh();
        };
        Button refreshButton = new Button("刷新 Git 状态");
        refreshButton.setDisable(true);
        refreshButton.setOnAction(event -> refreshGit.run());
        gitView.getChildren().addAll(2, java.util.List.of(refreshButton, branchView, commitView, countView));
        AgentsView agentsView = new AgentsView(agentSettings);
        Map<String, VBox> pages = new LinkedHashMap<>();
        pages.put("Dashboard", dashboardView);
        ProjectListView projectsView = new ProjectListView(ideSettings, appSettings, dataDirectory.resolve("projects.xml"), project -> {
            refreshButton.setDisable(project == null);
            currentProject.setText(project == null ? "未选择项目" : "当前项目：" + project.name());
            branchView.setProject(project);
            commitView.setProject(project);
            countView.setProject(project);
            notesView.setProject(project);
            todoView.setProject(project);
            dashboardView.setProject(project);
            agentsView.setProject(project);
        });
        pages.put("Projects", projectsView);
        pages.put("Git", gitView);
        pages.put("Notes", notesView);
        pages.put("TODO", todoView);
        pages.put("Agents", agentsView);
        EnvironmentView environmentView = new EnvironmentView(ideSettings);
        pages.put("Environment", environmentView);
        pages.put("Settings", settingsView);

        javafx.scene.control.ScrollPane gitScroll = new javafx.scene.control.ScrollPane(gitView);
        gitScroll.setFitToWidth(true);
        // Keep the status table and installation check at their natural heights; scroll the whole page.
        gitView.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        javafx.scene.control.ScrollPane dashboardScroll = new javafx.scene.control.ScrollPane(dashboardView);
        dashboardScroll.setFitToWidth(true);
        javafx.scene.control.ScrollPane environmentScroll = new javafx.scene.control.ScrollPane(environmentView);
        environmentScroll.setFitToWidth(true);
        javafx.scene.control.ScrollPane agentsScroll = new javafx.scene.control.ScrollPane(agentsView);
        agentsScroll.setFitToWidth(true);
        javafx.scene.control.ScrollPane settingsScroll = new javafx.scene.control.ScrollPane(settingsView);
        settingsScroll.setFitToWidth(true);
        pages.values().forEach(page -> page.getStyleClass().add("page"));
        com.fhaicl.aidevhub.ui.ToastHost contentHost = new com.fhaicl.aidevhub.ui.ToastHost();
        getProperties().put(com.fhaicl.aidevhub.ui.ToastHost.class, contentHost);
        setCenter(contentHost);
        ListView<String> navigation = new ListView<>();
        navigation.getStyleClass().add("navigation");
        navigation.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setText(empty ? null : name);
                setGraphic(empty || name == null ? null : Ui.icon(name));
            }
        });
        navigation.getItems().addAll(pages.keySet());
        navigation.setPrefWidth(200);
        navigation.setMinWidth(180);

        Label currentPage = new Label();
        Label brand = new Label("AI Developer Hub");
        brand.getStyleClass().add("brand");
        setTop(new ToolBar(brand, new Separator(), currentPage,
                new Separator(), currentProject));
        setLeft(navigation);

        navigation.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (selected != null) {
                        contentHost.content(selected.equals("Dashboard") ? dashboardScroll
                                : selected.equals("Git") ? gitScroll
                                : selected.equals("Environment") ? environmentScroll
                                : selected.equals("Agents") ? agentsScroll
                                : selected.equals("Settings") ? settingsScroll : pages.get(selected));
                        currentPage.setText(selected);
                        if (selected.equals("Dashboard")) {
                            dashboardView.setProjectCount(projectsView.projectCount());
                            dashboardView.refresh();
                        } else if (selected.equals("Git")) {
                            refreshGit.run();
                        } else if (selected.equals("Notes")) {
                            notesView.refresh();
                        } else if (selected.equals("Environment")) {
                            environmentView.refresh();
                        } else if (selected.equals("TODO")) {
                            todoView.refresh();
                        }
                    }
                });
        navigation.getSelectionModel().select(appSettings.values().startPage().name());
    }

}
