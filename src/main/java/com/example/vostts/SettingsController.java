package com.example.vostts;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import javax.sound.sampled.Mixer;

/** Controller for the settings window. */
public class SettingsController {
    @FXML private TextField wrapField;
    @FXML private TextField timeoutField;
    @FXML private ComboBox<Mixer.Info> deviceCombo;
    @FXML private Button closeButton;

    private VosTtsController parent;

    public void setParent(VosTtsController controller) {
        this.parent = controller;
        wrapField.setText(Integer.toString(controller.getWrapChars()));
        timeoutField.setText(Integer.toString(controller.getTimeoutSeconds()));
        deviceCombo.getItems().setAll(VosTtsController.listInputDevices());
        Mixer.Info sel = controller.getSelectedDevice();
        if (sel != null) {
            deviceCombo.getSelectionModel().select(sel);
        } else if (!deviceCombo.getItems().isEmpty()) {
            deviceCombo.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void onSave() {
        try {
            int wrap = Integer.parseInt(wrapField.getText().trim());
            parent.setWrapChars(wrap);
        } catch (NumberFormatException ignored) {}
        try {
            int t = Integer.parseInt(timeoutField.getText().trim());
            parent.setTimeoutSeconds(t);
        } catch (NumberFormatException ignored) {}
        Mixer.Info sel = deviceCombo.getSelectionModel().getSelectedItem();
        parent.setSelectedDevice(sel);
        onClose();
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}
