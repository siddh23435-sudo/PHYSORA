package edu.physics.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/** Persistent chapter index. Only the implemented projectile chapter is active. */
final class ChapterRail extends BorderPane {
    private static final List<String> CHAPTERS = List.of(
            "Kinematics", "Relative motion", "Laws of motion", "Work & energy",
            "Momentum & collisions", "Circular motion", "Rotation", "Solids",
            "Fluids", "Oscillations", "Waves & sound", "Electrostatics",
            "Capacitance", "Current electricity", "Magnetism", "Induction",
            "Alternating current", "Ray optics", "Wave optics");

    ChapterRail() {
        setPrefWidth(242);
        setMinWidth(220);
        setPadding(new Insets(24, 16, 18, 18));
        setStyle("-fx-background-color: #132b45;");

        VBox identity = new VBox(2);
        Label mark = new Label("K");
        mark.setTextFill(Color.web("#d8f45a"));
        mark.setFont(Font.font("Georgia", FontWeight.BOLD, 31));
        Label name = new Label("KINETIK");
        name.setTextFill(Color.web("#f5f0e4"));
        name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        Label tagline = new Label("PHYSICS, IN MOTION");
        tagline.setTextFill(Color.web("#aebbc7"));
        tagline.setStyle("-fx-font-size: 9px; -fx-letter-spacing: 1.4px;");
        identity.getChildren().addAll(mark, name, tagline);

        Label workspace = new Label("NORMAL BENCH");
        workspace.setTextFill(Color.web("#d8f45a"));
        workspace.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-letter-spacing: 1.3px;");
        Label curriculum = new Label("CURRICULUM   /   19 CHAPTERS");
        curriculum.getStyleClass().add("rail-kicker");
        VBox top = new VBox(24, identity, rule(), workspace, curriculum);
        top.setPadding(new Insets(0, 0, 16, 0));

        VBox chapterList = new VBox(3);
        for (int i = 0; i < CHAPTERS.size(); i++) {
            chapterList.getChildren().add(chapterRow(i + 1, CHAPTERS.get(i)));
        }
        ScrollPane scroll = new ScrollPane(chapterList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("chapter-scroll");
        setTop(top);
        setCenter(scroll);

        Label chapterNote = new Label("01  /  IN PROGRESS\nProjectile motion is the first\nactive model in the bench.");
        chapterNote.getStyleClass().add("rail-footnote");
        chapterNote.setPadding(new Insets(13, 0, 0, 0));
        setBottom(chapterNote);
    }

    private HBox chapterRow(int number, String name) {
        Label numberLabel = new Label(String.format("%02d", number));
        Label nameLabel = new Label(name);
        Label status = new Label(number == 1 ? "LIVE" : "PLANNED");
        HBox row = new HBox(8, numberLabel, nameLabel, status);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 7, 9, 7));
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        numberLabel.getStyleClass().add("chapter-number");
        nameLabel.getStyleClass().add("chapter-name");
        status.getStyleClass().add(number == 1 ? "chapter-live" : "chapter-planned");
        if (number == 1) {
            row.getStyleClass().add("chapter-current");
        } else {
            row.getStyleClass().add("chapter-upcoming");
        }
        return row;
    }

    private static javafx.scene.Node rule() {
        javafx.scene.shape.Rectangle line = new javafx.scene.shape.Rectangle(194, 1, Color.web("#38516b"));
        return line;
    }
}
