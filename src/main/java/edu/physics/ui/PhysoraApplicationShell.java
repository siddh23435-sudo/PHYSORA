package edu.physics.ui;

import javafx.scene.layout.BorderPane;

final class PhysoraApplicationShell extends BorderPane {
    private final ProjectileSimulationController controller = new ProjectileSimulationController();
    private final NormalBenchView normalBenchView = new NormalBenchView(controller);
    private final ChapterRail chapterRail = new ChapterRail();
    private final PlannedChapterView plannedChapterView = new PlannedChapterView();

    PhysoraApplicationShell() {
        getStyleClass().add("physora-shell");
        setLeft(chapterRail);
        setCenter(normalBenchView);

        chapterRail.selectedChapterProperty().addListener((obs, oldChapter, newChapter) -> {
            if (newChapter != null && newChapter.number() == 1) {
                setCenter(normalBenchView);
            } else if (newChapter != null) {
                plannedChapterView.setChapter(newChapter);
                setCenter(plannedChapterView);
            }
        });

        plannedChapterView.setOnReturnToKinematics(() -> chapterRail.selectChapter(1));
    }

    void selectChapter(int number) {
        chapterRail.selectChapter(number);
    }

    void dispose() {
        normalBenchView.dispose();
        controller.dispose();
        plannedChapterView.dispose();
    }

    ChapterRail chapterRail() { return chapterRail; }
    NormalBenchView normalBenchView() { return normalBenchView; }
    PlannedChapterView plannedChapterView() { return plannedChapterView; }
    ProjectileSimulationController controller() { return controller; }
}
