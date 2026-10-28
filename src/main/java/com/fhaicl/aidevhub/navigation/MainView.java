package com.fhaicl.aidevhub.navigation;

import com.fhaicl.aidevhub.project.ProjectListView;
import com.fhaicl.aidevhub.git.GitInstallationView;
import com.fhaicl.aidevhub.git.GitBranchView;
import com.fhaicl.aidevhub.git.GitLatestCommitView;
import com.fhaicl.aidevhub.git.GitChangeCountView;
import com.fhaicl.aidevhub.settings.IdePathSettings;
import com.fhaicl.aidevhub.settings.IdeSettingsView;
import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainView extends BorderPane {

    public MainView() {
        IdePathSettings ideSettings = new IdePathSettings(
                Path.of(System.getProperty("user.home"), ".aidevhub", "ide-paths.xml"));
        IdeSettingsView settingsView = new IdeSettingsView(ideSettings);
        Label currentProject = new Label("未选择项目");
        GitBranchView branchView = new GitBranchView();
        GitLatestCommitView commitView = new GitLatestCommitView();
        GitChangeCountView countView = new GitChangeCountView();
        VBox.setVgrow(countView, Priority.ALWAYS);
        GitInstallationView gitView = new GitInstallationView();
        gitView.getChildren().addAll(2, java.util.List.of(branchView, commitView, countView));
        Map<String, VBox> pages = new LinkedHashMap<>();
        pages.put("Dashboard", createPage("Dashboard", "开发工作概览。", "暂无概览数据"));
        pages.put("Projects", new ProjectListView(ideSettings, project -> {
            currentProject.setText(project == null ? "未选择项目" : "当前项目：" + project.name());
            branchView.setProject(project);
            commitView.setProject(project);
            countView.setProject(project);
        }));
        pages.put("Git", gitView);
        pages.put("Notes", createPage("Notes", "记录开发过程中的笔记。", "暂无笔记"));
        pages.put("TODO", createPage("TODO", "Track your development tasks."));
        pages.put("Agents", createPage("Agents", "集中访问 AI 开发助手。", "暂无 Agent"));
        pages.put("Environment", createPage("Environment", "View your development environment information."));
        pages.put("Settings", settingsView);

        ListView<String> navigation = new ListView<>();
        navigation.getItems().addAll(pages.keySet());
        navigation.setPrefWidth(200);

        Label currentPage = new Label();
        setTop(new ToolBar(new Label("AI Developer Hub"), new Separator(), currentPage,
                new Separator(), currentProject));
        setLeft(navigation);

        navigation.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (selected != null) {
                        setCenter(pages.get(selected));
                        currentPage.setText(selected);
                        if (selected.equals("Git")) {
                            branchView.refresh();
                            commitView.refresh();
                            countView.refresh();
                        }
                    }
                });
        navigation.getSelectionModel().selectFirst();
    }

    private static VBox createPage(String title, String summary) {
        return createPage(title, summary, title + " — Coming soon");
    }

    private static VBox createPage(String title, String summary, String placeholderText) {
        Label pageTitle = new Label(title);
        pageTitle.setFont(Font.font(24));
        Label description = new Label(summary);
        description.setWrapText(true);
        Label placeholder = new Label(placeholderText);
        placeholder.setWrapText(true);
        StackPane placeholderArea = new StackPane(placeholder);
        placeholderArea.setPadding(new Insets(24));
        placeholderArea.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 4;");
        VBox.setVgrow(placeholderArea, Priority.ALWAYS);

        VBox content = new VBox(12, pageTitle, description, placeholderArea);
        content.setAlignment(Pos.TOP_LEFT);
        content.setMinWidth(0);
        content.setPadding(new Insets(24));
        return content;
    }
}
