package edu.physics.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Standardized, high-contrast numeric parameter control for PHYSORA physics experiments.
 *
 * <p>Replaces horizontal sliders with directly editable numeric text fields as part of the
 * PHYSORA numeric input standardization. Formatted as a compact aligned row:
 * <pre>
 *   [Parameter Name]       [ [TextField] Unit ]
 *   [Validation Error Message] (collapsed when valid)
 * </pre>
 * </p>
 */
public final class NumericParameterRow extends VBox {

    private final Label nameLabel;
    private final TextField valueField;
    private final Label unitLabel;
    private final Label errorLabel;

    private final double min;
    private final double max;
    private final String decimalFormat;
    private final Consumer<Double> onValueCommitted;

    private double currentValue;
    private boolean updatingInternally = false;

    public NumericParameterRow(String name, String unit, String inputId,
                               double initialValue, double min, double max,
                               String decimalFormat, Consumer<Double> onValueCommitted) {
        super(2);
        this.min = min;
        this.max = max;
        this.decimalFormat = Objects.requireNonNullElse(decimalFormat, "%.2f");
        this.onValueCommitted = Objects.requireNonNull(onValueCommitted, "onValueCommitted must not be null");
        this.currentValue = initialValue;
        getStyleClass().add("numeric-param-row");

        nameLabel = new Label(name);
        nameLabel.getStyleClass().add("metric-name");
        nameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        nameLabel.setTextFill(Color.web("#1e293b")); // Crisp dark slate for high contrast
        nameLabel.setStyle("-fx-text-fill: #1e293b; -fx-font-weight: bold;");
        nameLabel.setMinWidth(115);

        valueField = new TextField(formatValue(initialValue));
        valueField.setId(inputId);
        valueField.setFont(Font.font("Consolas", FontWeight.BOLD, 11.5));
        valueField.setAlignment(Pos.CENTER_RIGHT);
        valueField.setPrefWidth(72);
        valueField.setMinWidth(62);
        valueField.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbc4b4; -fx-border-radius: 4px; " +
                "-fx-background-radius: 4px; -fx-padding: 3 6 3 6; -fx-text-fill: #0f172a;");

        unitLabel = new Label(unit);
        unitLabel.getStyleClass().add("metric-unit");
        unitLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        unitLabel.setTextFill(Color.web("#475569")); // Muted slate
        unitLabel.setStyle("-fx-text-fill: #475569; -fx-font-weight: bold;");
        unitLabel.setMinWidth(36);

        errorLabel = new Label();
        errorLabel.setId(inputId + "Error");
        errorLabel.getStyleClass().add("validation-message");
        errorLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        errorLabel.setTextFill(Color.web("#b91c1c")); // Accessible red
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        HBox inputWithUnit = new HBox(6, valueField, unitLabel);
        inputWithUnit.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox mainRow = new HBox(8, nameLabel, spacer, inputWithUnit);
        mainRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(mainRow, errorLabel);

        initEventHandlers();
    }

    private void initEventHandlers() {
        // Commit on Action (Enter)
        valueField.setOnAction(e -> commitInput());

        // Escape reverts to last valid value
        valueField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                valueField.setText(formatValue(currentValue));
                clearError();
            }
        });

        // Commit on Focus Lost
        valueField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) {
                commitInput();
            }
        });

        // Text change listener
        valueField.textProperty().addListener((obs, oldText, text) -> {
            if (updatingInternally) {
                return;
            }
            if (!valueField.isFocused()) {
                // Programmatic update (e.g. from headless tests or script)
                try {
                    if (text == null || text.isBlank()) throw new NumberFormatException();
                    double val = Double.parseDouble(text.trim());
                    if (!Double.isFinite(val)) throw new NumberFormatException();
                    if (val < min || val > max) throw new NumberFormatException();

                    this.currentValue = val;
                    clearError();
                    onValueCommitted.accept(val);
                } catch (NumberFormatException ex) {
                    showError("Enter a finite number.");
                }
            } else {
                // Interactive typing: clear error if syntax is now valid, but do NOT commit while actively typing
                try {
                    if (text != null && !text.isBlank()) {
                        double v = Double.parseDouble(text.trim());
                        if (Double.isFinite(v) && v >= min && v <= max) {
                            clearErrorStyleOnly();
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Do not flash validation errors while user is actively typing
                }
            }
        });
    }

    /**
     * Parses the current text, validates bounds, and updates the model if valid.
     */
    public boolean commitInput() {
        String raw = valueField.getText();
        if (raw == null || raw.isBlank()) {
            showError("Enter a finite number.");
            return false;
        }

        try {
            double parsed = Double.parseDouble(raw.trim());
            if (!Double.isFinite(parsed)) {
                showError("Enter a finite number.");
                return false;
            }
            if (parsed < min || parsed > max) {
                showError(String.format(Locale.ROOT, "Range: %s to %s", formatValue(min), formatValue(max)));
                return false;
            }

            clearError();
            this.currentValue = parsed;
            valueField.setText(formatValue(parsed));
            onValueCommitted.accept(parsed);
            return true;
        } catch (NumberFormatException ex) {
            showError("Enter a finite number.");
            return false;
        }
    }

    /**
     * Programmatically sets the value and updates the text field if not currently focused.
     */
    public void setValue(double value) {
        this.currentValue = value;
        clearError();
        if (!valueField.isFocused()) {
            String formatted = formatValue(value);
            if (!valueField.getText().equals(formatted)) {
                updatingInternally = true;
                try {
                    valueField.setText(formatted);
                } finally {
                    updatingInternally = false;
                }
            }
        }
    }

    public double getValue() {
        return currentValue;
    }

    public TextField valueField() {
        return valueField;
    }

    public Label nameLabel() {
        return nameLabel;
    }

    public Label errorLabel() {
        return errorLabel;
    }

    private void showError(String message) {
        if (!valueField.getStyleClass().contains("input-invalid")) {
            valueField.getStyleClass().add("input-invalid");
        }
        valueField.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #b91c1c; -fx-border-width: 1.5px; " +
                "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 3 6 3 6; -fx-text-fill: #991b1b;");
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        valueField.getStyleClass().remove("input-invalid");
        valueField.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbc4b4; -fx-border-width: 1px; " +
                "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 3 6 3 6; -fx-text-fill: #0f172a;");
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void clearErrorStyleOnly() {
        valueField.getStyleClass().remove("input-invalid");
        valueField.setStyle("-fx-background-color: #ffffff; -fx-border-color: #2453d4; -fx-border-width: 1px; " +
                "-fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 3 6 3 6; -fx-text-fill: #0f172a;");
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private String formatValue(double val) {
        return String.format(Locale.ROOT, decimalFormat, val);
    }
}
