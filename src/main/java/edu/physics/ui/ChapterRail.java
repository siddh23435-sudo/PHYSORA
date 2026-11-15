package edu.physics.ui;

import edu.physics.problem.PhysicsCapabilityRegistry;
import edu.physics.problem.PhysicsCapabilityRegistry.ChapterCapability;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact, collapsible navigation rail representing the eight supported JEE mechanics chapters.
 * Authoritative capability states are consumed directly from {@link PhysicsCapabilityRegistry}.
 */
final class ChapterRail extends BorderPane {
    private static final double EXPANDED_WIDTH = 232.0;
    private static final double COLLAPSED_WIDTH = 64.0;

    private final BooleanProperty collapsed = new SimpleBooleanProperty(false);
    private final ObjectProperty<ChapterCapability> selectedChapter =
            new SimpleObjectProperty<>(PhysicsCapabilityRegistry.curriculumChapters().get(0));

    private final List<HBox> expandedRows = new ArrayList<>();
    private final List<Button> collapsedRows = new ArrayList<>();
    private final VBox topSection = new VBox();
    private final VBox chapterListBox = new VBox(4);
    private final ScrollPane scrollPane = new ScrollPane(chapterListBox);
    private final Label footnote = new Label();
    private final Button collapseToggle = new Button("◀");

    ChapterRail() {
        setId("chapterRail");
        getStyleClass().add("chapter-rail");
        setPrefWidth(EXPANDED_WIDTH);
        setMinWidth(COLLAPSED_WIDTH);
        setStyle("-fx-background-color: #132b45;");

        collapseToggle.setId("railCollapseToggle");
        collapseToggle.getStyleClass().add("rail-toggle-button");
        collapseToggle.setOnAction(event -> setCollapsed(!isCollapsed()));

        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("chapter-scroll");

        footnote.setId("railFootnote");
        footnote.getStyleClass().add("rail-footnote");

        setTop(topSection);
        setCenter(scrollPane);
        setBottom(footnote);

        collapsed.addListener((obs, wasCollapsed, isNowCollapsed) -> refreshRailLayout(isNowCollapsed));
        selectedChapter.addListener((obs, oldChapter, newChapter) -> updateSelectedRowHighlight());

        refreshRailLayout(false);
    }

    private void refreshRailLayout(boolean isCollapsed) {
        topSection.getChildren().clear();
        chapterListBox.getChildren().clear();
        expandedRows.clear();
        collapsedRows.clear();

        List<ChapterCapability> chapters = PhysicsCapabilityRegistry.curriculumChapters();

        if (isCollapsed) {
            setPrefWidth(COLLAPSED_WIDTH);
            setPadding(new Insets(16, 6, 12, 6));

            // Collapsed top: "P" mark + expand toggle
            collapseToggle.setText("▶");
            Label compactMark = new Label("P");
            compactMark.setTextFill(Color.web("#d8f45a"));
            compactMark.setFont(Font.font("Georgia", FontWeight.BOLD, 22));

            VBox compactHeader = new VBox(10, collapseToggle, compactMark, rule(36));
            compactHeader.setAlignment(Pos.CENTER);
            compactHeader.setPadding(new Insets(0, 0, 12, 0));
            topSection.getChildren().add(compactHeader);

            // Collapsed rows: compact square buttons with chapter number
            chapterListBox.setAlignment(Pos.TOP_CENTER);
            for (ChapterCapability cap : chapters) {
                Button btn = new Button(cap.formattedNumber());
                btn.setId("chapterRow_" + cap.number());
                btn.getStyleClass().addAll("chapter-collapsed-btn",
                        cap.isAvailable() ? "chapter-available" : "chapter-planned-btn");
                btn.setTooltip(new Tooltip(cap.formattedNumber() + " " + cap.title() + " (" + cap.status() + ")"));
                btn.setOnAction(e -> selectChapter(cap.number()));
                collapsedRows.add(btn);
                chapterListBox.getChildren().add(btn);
            }

            footnote.setVisible(false);
            footnote.setManaged(false);
        } else {
            setPrefWidth(EXPANDED_WIDTH);
            setPadding(new Insets(20, 14, 16, 14));

            // Expanded top: Brand identity + collapse toggle + curriculum kicker
            collapseToggle.setText("◀");
            Label mark = new Label("P");
            mark.setTextFill(Color.web("#d8f45a"));
            mark.setFont(Font.font("Georgia", FontWeight.BOLD, 26));

            Label name = new Label("PHYSORA");
            name.setTextFill(Color.web("#f5f0e4"));
            name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));

            Label tagline = new Label("PHYSICS, IN MOTION");
            tagline.setTextFill(Color.web("#aebbc7"));
            tagline.setStyle("-fx-font-size: 8.5px; -fx-letter-spacing: 1.2px;");

            VBox brandText = new VBox(1, name, tagline);
            HBox brandRow = new HBox(10, mark, brandText, spacer(), collapseToggle);
            brandRow.setAlignment(Pos.CENTER_LEFT);

            Label workspace = new Label("MECHANICS WORKBENCH");
            workspace.setTextFill(Color.web("#d8f45a"));
            workspace.setStyle("-fx-font-size: 9.5px; -fx-font-weight: bold; -fx-letter-spacing: 1.2px;");

            Label curriculum = new Label("CURRICULUM   /   8 CHAPTERS");
            curriculum.getStyleClass().add("rail-kicker");

            VBox header = new VBox(12, brandRow, rule(196), workspace, curriculum);
            header.setPadding(new Insets(0, 0, 10, 0));
            topSection.getChildren().add(header);

            // Expanded rows: number, title, status pill
            chapterListBox.setAlignment(Pos.TOP_LEFT);
            for (ChapterCapability cap : chapters) {
                HBox row = createExpandedRow(cap);
                expandedRows.add(row);
                chapterListBox.getChildren().add(row);
            }

            footnote.setText("01 / LIVE BENCH\nKinematics is interactive.\n7 chapters planned.");
            footnote.setPadding(new Insets(10, 0, 0, 0));
            footnote.setVisible(true);
            footnote.setManaged(true);
        }

        updateSelectedRowHighlight();
    }

    private HBox createExpandedRow(ChapterCapability cap) {
        Label numberLabel = new Label(cap.formattedNumber());
        Label nameLabel = new Label(cap.title());
        Label statusLabel = new Label(cap.isAvailable() ? "LIVE" : "PLANNED");

        HBox row = new HBox(8, numberLabel, nameLabel, statusLabel);
        row.setId("chapterRow_" + cap.number());
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(7, 8, 7, 8));
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        numberLabel.getStyleClass().add("chapter-number");
        nameLabel.getStyleClass().add("chapter-name");
        statusLabel.getStyleClass().add(cap.isAvailable() ? "chapter-live" : "chapter-planned");

        row.setOnMouseClicked(event -> selectChapter(cap.number()));
        return row;
    }

    private void updateSelectedRowHighlight() {
        int selectedNum = selectedChapter.get().number();
        List<ChapterCapability> chapters = PhysicsCapabilityRegistry.curriculumChapters();

        if (isCollapsed()) {
            for (int i = 0; i < collapsedRows.size(); i++) {
                Button btn = collapsedRows.get(i);
                int chapterNum = chapters.get(i).number();
                btn.getStyleClass().remove("chapter-current-btn");
                if (chapterNum == selectedNum) {
                    btn.getStyleClass().add("chapter-current-btn");
                }
            }
        } else {
            for (int i = 0; i < expandedRows.size(); i++) {
                HBox row = expandedRows.get(i);
                int chapterNum = chapters.get(i).number();
                row.getStyleClass().removeAll("chapter-current", "chapter-upcoming");
                if (chapterNum == selectedNum) {
                    row.getStyleClass().add("chapter-current");
                } else {
                    row.getStyleClass().add("chapter-upcoming");
                }
            }
        }
    }

    void selectChapter(int number) {
        PhysicsCapabilityRegistry.findChapter(number).ifPresent(selectedChapter::set);
    }

    ChapterCapability selectedChapter() {
        return selectedChapter.get();
    }

    ObjectProperty<ChapterCapability> selectedChapterProperty() {
        return selectedChapter;
    }

    boolean isCollapsed() {
        return collapsed.get();
    }

    void setCollapsed(boolean value) {
        collapsed.set(value);
    }

    BooleanProperty collapsedProperty() {
        return collapsed;
    }

    private static Node rule(double width) {
        javafx.scene.shape.Rectangle line = new javafx.scene.shape.Rectangle(width, 1, Color.web("#38516b"));
        return line;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
