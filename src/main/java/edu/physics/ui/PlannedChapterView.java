package edu.physics.ui;

import edu.physics.problem.PhysicsCapabilityRegistry.ChapterCapability;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.Map;

/**
 * Informative view displayed when a student navigates to a planned curriculum chapter.
 * Prevents launching empty or misleading simulations while preserving the active Kinematics model in memory.
 */
final class PlannedChapterView extends BorderPane {
    private static final Map<Integer, List<String>> TOPIC_ROADMAPS = Map.of(
            2, List.of("Inertial Reference Frames", "1D & 2D Relative Velocity", "River-Boat Crossing Problems", "Rain-Umbrella Drift Vectors"),
            3, List.of("Newton's Three Laws of Motion", "Free-Body Diagrams (FBD)", "Static and Kinetic Friction", "Coupled Pulley-Mass Systems"),
            4, List.of("Work-Energy Theorem", "Conservative vs Non-Conservative Forces", "Potential Energy Curves", "Mechanical Power & Efficiency"),
            5, List.of("Impulse-Momentum Theorem", "Conservation of Linear Momentum", "1D Elastic & Inelastic Collisions", "2D Oblique Impact Dynamics"),
            6, List.of("Uniform Circular Motion & Kinematics", "Centripetal & Centrifugal Formulations", "Roadway & Railway Banking", "Vertical Circular Motion Loops"),
            7, List.of("Rotational Kinematics & Angular Velocity", "Torque and Moment of Inertia", "Parallel & Perpendicular Axis Theorems", "Rolling Without Slipping Dynamics"),
            8, List.of("Simple Harmonic Motion Kinematics", "Mass-Spring & Pendulum Equations", "Energy Oscillations (KE vs PE)", "Damped & Driven Oscillation Foundations")
    );

    private final Label chapterHeader = new Label();
    private final Label chapterTitle = new Label();
    private final Label chapterDesc = new Label();
    private final FlowPane topicsPane = new FlowPane(10, 10);
    private final Button returnButton = new Button("⚗  RETURN TO KINEMATICS EXPERIMENT");
    private final VBox standardContent;
    private final RelativeMotionWorkbenchView relativeMotionWorkbench;
    private Runnable onReturnToKinematics;

    PlannedChapterView() {
        setId("plannedChapterView");
        getStyleClass().add("planned-chapter-view");
        setStyle("-fx-background-color: #f5f1e5;");
        setPadding(new Insets(16, 24, 16, 24));

        returnButton.setId("returnToKinematicsButton");
        returnButton.getStyleClass().add("transport-primary");
        returnButton.setStyle("-fx-font-size: 11px; -fx-padding: 10 18 10 18;");
        returnButton.setOnAction(e -> {
            if (onReturnToKinematics != null) {
                onReturnToKinematics.run();
            }
        });

        this.standardContent = buildContent();
        this.relativeMotionWorkbench = new RelativeMotionWorkbenchView(new RelativeMotionSimulationController());
        this.relativeMotionWorkbench.setOnReturnToKinematics(() -> {
            if (onReturnToKinematics != null) {
                onReturnToKinematics.run();
            }
        });

        setCenter(standardContent);
    }

    RelativeMotionWorkbenchView relativeMotionWorkbench() {
        return relativeMotionWorkbench;
    }

    Button returnButton() {
        return returnButton;
    }

    private VBox buildContent() {
        chapterHeader.getStyleClass().add("eyebrow");
        chapterHeader.setTextFill(PhysoraTheme.MUTED);

        chapterTitle.setFont(Font.font("Georgia", FontWeight.BOLD, 28));
        chapterTitle.setTextFill(PhysoraTheme.INK);

        Label statusPill = new Label("PLANNED CHAPTER — CURRICULUM ROADMAP");
        statusPill.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; " +
                "-fx-font-family: Consolas; -fx-font-size: 10px; -fx-font-weight: bold; " +
                "-fx-padding: 4 10 4 10; -fx-border-color: #fde68a; -fx-border-radius: 3px; -fx-background-radius: 3px;");

        HBox titleRow = new HBox(12, chapterTitle, statusPill);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        chapterDesc.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155; -fx-line-spacing: 3px;");
        chapterDesc.setWrapText(true);

        Label syllabusTitle = new Label("CURRICULUM TOPICS & JEE SYLLABUS SPECIFICATION");
        syllabusTitle.getStyleClass().add("eyebrow");

        topicsPane.setPrefWrapLength(700);

        Label notice = new Label(
                "Interactive multi-body simulation and dedicated numerical solvers for this chapter are scheduled " +
                        "for the upcoming milestone. The Kinematics Laboratory (Chapter 01) is currently live with " +
                        "deterministic 2D simulation, guided logic derivations, and AI problem assistance.");
        notice.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-background-color: #e9e2d0; " +
                "-fx-padding: 16; -fx-border-color: #cbc4b4; -fx-border-radius: 2px; -fx-background-radius: 2px;");
        notice.setWrapText(true);

        VBox card = new VBox(16,
                chapterHeader,
                titleRow,
                chapterDesc,
                rule(),
                syllabusTitle,
                topicsPane,
                rule(),
                notice,
                returnButton
        );
        card.setStyle("-fx-background-color: #f5f1e5; -fx-padding: 16 0 24 0;");
        card.setMaxWidth(800);
        return card;
    }

    void setChapter(ChapterCapability chapter) {
        if (chapter.number() == 2) {
            setCenter(relativeMotionWorkbench);
            return;
        }

        setCenter(standardContent);
        chapterHeader.setText(String.format("CHAPTER %s   /   JEE MECHANICS", chapter.formattedNumber()));
        chapterTitle.setText(chapter.title().toUpperCase());
        chapterDesc.setText(chapter.description());

        topicsPane.getChildren().clear();
        List<String> topics = TOPIC_ROADMAPS.getOrDefault(chapter.number(), List.of("Standard JEE Mechanics Curriculum"));
        for (String topic : topics) {
            Label topicLabel = new Label("• " + topic);
            topicLabel.setStyle("-fx-background-color: #e9e2d0; -fx-text-fill: #142e4b; " +
                    "-fx-font-size: 11px; -fx-font-family: 'Segoe UI', sans-serif; " +
                    "-fx-padding: 6 12 6 12; -fx-border-color: #cbc4b4; -fx-border-radius: 2px; -fx-background-radius: 2px;");
            topicsPane.getChildren().add(topicLabel);
        }
    }

    void setOnReturnToKinematics(Runnable callback) {
        this.onReturnToKinematics = callback;
    }

    private static Node rule() {
        Region rule = new Region();
        rule.setPrefHeight(1);
        rule.setStyle("-fx-background-color: #cbc4b4;");
        return rule;
    }
}

