package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

public class FileShareApp extends Application {

    private P2P p2p;
    private ListView<FileItem> fileListView;
    private ComboBox<String> peerSelector;

    // Constants
    private static final String BACKGROUND_COLOR = "#1e1e1e";
    private static final String TITLE_BAR_COLOR = "#2c2c2c";
    private static final String TEXT_COLOR = "#e0e0e0";
    private static final String SUBTITLE_COLOR = "#a0a0a0";

    @Override
    public void start(Stage primaryStage) {
        p2p = new P2P();

        VBox root = createRoot();
        HBox titleBar = createTitleBar(primaryStage);
        VBox content = createContent();

        root.getChildren().addAll(titleBar, content);

        Scene scene = new Scene(root, 400, 600);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        setupPrimaryStage(primaryStage, scene);

        startP2PServer();
        updateFileList();
    }

    private VBox createRoot() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(0));
        root.setStyle("-fx-background-color: " + BACKGROUND_COLOR + ";");
        return root;
    }

    private VBox createContent() {
        VBox content = new VBox(20);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));

        Label titleLabel = createLabel("UPLOAD FILES", 24, TEXT_COLOR, FontWeight.BOLD);
        Label subtitleLabel = createLabel("Upload documents you want to share with your team.", 14, SUBTITLE_COLOR, FontWeight.NORMAL);

        VBox dragDropArea = createDragDropArea();
        fileListView = createFileListView();
        HBox bottomControls = createBottomControls();

        content.getChildren().addAll(titleLabel, subtitleLabel, dragDropArea, new Label("Uploaded files"), fileListView, bottomControls);
        return content;
    }

    private Label createLabel(String text, int fontSize, String color, FontWeight weight) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", weight, fontSize));
        label.setTextFill(Color.valueOf(color));
        return label;
    }

    private ListView<FileItem> createFileListView() {
        ListView<FileItem> listView = new ListView<>();
        listView.setCellFactory(param -> new FileListCell());
        listView.setPrefHeight(200);
        return listView;
    }

    private HBox createBottomControls() {
        peerSelector = new ComboBox<>();
        peerSelector.setPromptText("Select a peer");
        peerSelector.setOnAction(e -> onPeerSelected());

        Button refreshPeersButton = new Button("Refresh Peers");
        refreshPeersButton.setOnAction(e -> refreshPeers());

        HBox bottomControls = new HBox(10, peerSelector, refreshPeersButton);
        bottomControls.setAlignment(Pos.CENTER);
        return bottomControls;
    }

    private void setupPrimaryStage(Stage primaryStage, Scene scene) {
        primaryStage.setTitle("File Share");
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            closeApplication();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(this::closeApplication));
    }

    private void startP2PServer() {
        new Thread(() -> {
            try {
                p2p.startServer();
                Platform.runLater(this::refreshPeers);
            } catch (IOException ex) {
                Platform.runLater(() -> showErrorAlert("Failed to start P2P server", ex.getMessage()));
            }
        }).start();
    }


    private void showErrorAlert(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }



    private HBox createTitleBar(Stage stage) {
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(10));
        titleBar.setStyle("-fx-background-color: #2c2c2c;");

        Label titleLabel = new Label("File Share Application");
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Button minimizeBtn = new Button("—");
        Button maximizeBtn = new Button("□");
        Button closeBtn = new Button("✕");

        minimizeBtn.setOnAction(e -> stage.setIconified(true));
        maximizeBtn.setOnAction(e -> {
            stage.setMaximized(!stage.isMaximized());
        });
        closeBtn.setOnAction(e -> stage.close());

        titleBar.getChildren().addAll(titleLabel, minimizeBtn, maximizeBtn, closeBtn);

        return titleBar;
    }

    private VBox createDragDropArea() {
        VBox dragDropArea = new VBox(10);
        dragDropArea.setId("dragDropArea");
        dragDropArea.setAlignment(Pos.CENTER);
        dragDropArea.setPrefHeight(150);
        dragDropArea.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        ImageView cloudIcon = new ImageView(new Image("https://cdn3.iconfinder.com/data/icons/transfers/100/239296-cloud_upload-1024.png"));
        cloudIcon.setFitHeight(50);
        cloudIcon.setPreserveRatio(true);

        Label dragDropLabel = new Label("Drag & Drop your files here");
        dragDropLabel.setFont(Font.font("Arial", 14));

        Label orLabel = new Label("OR");
        orLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        Button browseButton = new Button("Browse Files");
        browseButton.setOnAction(e -> uploadFile());

        dragDropArea.getChildren().addAll(cloudIcon, dragDropLabel, orLabel, browseButton);

        dragDropArea.setOnDragOver(event -> {
            if (event.getGestureSource() != dragDropArea && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        dragDropArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    p2p.addSharedFile(file);
                }
                success = true;
                updateFileList();
            }
            event.setDropCompleted(success);
            event.consume();
        });

        dragDropArea.setOnDragEntered(event -> {
            if (event.getGestureSource() != dragDropArea && event.getDragboard().hasFiles()) {
                dragDropArea.setStyle("-fx-border-color: #4c90af; -fx-border-width: 2; -fx-border-style: dashed;");
            }
            event.consume();
        });

        dragDropArea.setOnDragExited(event -> {
            dragDropArea.setStyle("-fx-border-color: transparent;");
            event.consume();
        });

        return dragDropArea;
    }

    private void refreshPeers() {
        peerSelector.getItems().clear();
        for (InetAddress peer : p2p.getDiscoveredPeers()) {
            if (peer.getHostAddress().equals(p2p.getMyIp())) {
                peerSelector.getItems().add("This computer");
            } else {
                peerSelector.getItems().add(peer.getHostAddress());
            }

        }
    }

    private void onPeerSelected() {
        String selectedPeerIp = peerSelector.getValue();
        if (selectedPeerIp != null) {
            if (selectedPeerIp.equals("This computer")) {
                updateFileList();
                return;
            }
            List<File> peerFiles = p2p.getSharedPeerFiles(selectedPeerIp);
            updatePeerFileList(peerFiles);
        }
    }

    private void removeFile(File file) {
        System.out.println("Removing file: " + file.getName());
        p2p.removeSharedFile(file);
        updateFileList();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static class FileItem {
        private final File file;
        private final boolean isLocal;

        public FileItem(File file, boolean isLocal) {
            this.file = file;
            this.isLocal = isLocal;
        }

        public String getName() {
            return file.getName();
        }

        public String getExtension() {
            String name = file.getName();
            int lastIndexOf = name.lastIndexOf(".");
            if (lastIndexOf == -1) {
                return "";
            }
            return name.substring(lastIndexOf + 1);
        }


        public File getFile() {
            return file;
        }

        public boolean isLocal() {
            return isLocal;
        }

    }

    private class FileListCell extends ListCell<FileItem> {
        private final HBox content;
        private final Label fileTypeLabel;
        private final Label nameLabel;
        private final Label fileSizeLabel;
        private final Button actionButton;
        private static final int MAX_FILENAME_LENGTH = 20;

        public FileListCell() {
            super();
            fileTypeLabel = new Label();
            nameLabel = new Label();
            actionButton = new Button();
            fileSizeLabel = new Label();

            fileTypeLabel.setPrefWidth(40);
            nameLabel.setPrefWidth(100);
            fileSizeLabel.setPrefWidth(60);
            actionButton.setPrefWidth(100);

            content = new HBox(10, fileTypeLabel, nameLabel, fileSizeLabel, actionButton);
            content.setAlignment(Pos.CENTER_LEFT);
            content.setPrefWidth(300); // Adjust total width

            content.getStyleClass().add("list-cell-content");
            fileTypeLabel.getStyleClass().add("file-type-label");
            nameLabel.getStyleClass().add("file-name-label");
            fileSizeLabel.getStyleClass().add("file-size-label");
            actionButton.getStyleClass().add("action-button");
        }

        private String truncateFileName(String filename) {
            if (filename.length() <= MAX_FILENAME_LENGTH) {
                return filename;
            }
            return filename.substring(0, MAX_FILENAME_LENGTH - 3) + "...";
        }

        @Override
        protected void updateItem(FileItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                fileTypeLabel.setText(item.getExtension().toUpperCase());
                nameLabel.setText(truncateFileName(item.getName()));
                fileSizeLabel.setText(getFileSize(item.getFile()));

                if (item.isLocal()) {
                    actionButton.setText("Open folder");
                    actionButton.setOnAction(e -> openFileLocation(item.getFile()));
                } else {
                    actionButton.setText("Download");
                    actionButton.setOnAction(e -> downloadFile(item));
                }

                setGraphic(content);
            }
        }
    }



    private void openFileLocation(File file) {
        if (file == null || !file.exists()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "File not found.");
            alert.showAndWait();
            return;
        }
        try {
            File parentDirectory = file.getParentFile();
            if (parentDirectory != null && parentDirectory.exists()) {
                java.awt.Desktop.getDesktop().open(parentDirectory);
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Parent directory not found.");
                alert.showAndWait();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error opening file location: " + e.getMessage());
            alert.showAndWait();
        }
    }

    private void closeApplication() {
        try {
            if (p2p != null){
                p2p.stopServer();
                Platform.exit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFileSize(File file) {
        long bytes = file.length();
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return bytes / 1024 + " KB";
        if (bytes < 1024 * 1024 * 1024) return bytes / (1024 * 1024) + " MB";
        return bytes / (1024 * 1024 * 1024) + " GB";
    }

    private void downloadFile(FileItem item) {
        new Thread(() -> {
            try {
                String selectedPeerIp = peerSelector.getValue();
                byte[] fileContent = p2p.downloadFile(selectedPeerIp, item.getName());

                Platform.runLater(() -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setInitialFileName(item.getName());
                    File saveLocation = fileChooser.showSaveDialog(null);

                    if (saveLocation != null) {
                        try {
                            p2p.saveFile(saveLocation, fileContent);
                            updateFileList(); // Refresh the list to show the new local file
                        } catch (IOException e) {
                            e.printStackTrace();
                            // Show an error dialog
                        }
                    }
                });
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                // Show an error dialog
            }
        }).start();
    }

    private void uploadFile() {
        FileChooser fileChooser = new FileChooser();
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            if (fileListView.getItems().stream().anyMatch(item -> item.getName().equals(selectedFile.getName()))) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "File already exists in the list.");
                alert.showAndWait();
                return;
            }
            FileItem newItem = new FileItem(selectedFile, true);
            fileListView.getItems().add(newItem);

        }
    }

    private void updateFileList() {
        fileListView.getItems().clear();
        List<File> sharedFiles = p2p.getSharedFiles();
        for (File file : sharedFiles) {
            fileListView.getItems().add(new FileItem(file, true));
        }
    }

    private void updatePeerFileList(List<File> peerFiles) {
        fileListView.getItems().clear();
        for (File file : peerFiles) {
            fileListView.getItems().add(new FileItem(file, false));
        }
    }

}