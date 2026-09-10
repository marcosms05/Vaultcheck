package io.vaultcheck.desktop;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import io.vaultcheck.VaultCheckApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Desktop pilot: read-only folder inventory and synthetic demonstrations. */
public final class DesktopApplication extends Application {
        private final java.util.function.Function<Stage, java.io.File> folderChooser;
    public DesktopApplication() {
        this(owner -> {
            var chooser = new javafx.stage.DirectoryChooser(); chooser.setTitle("Elegir carpeta local · Solo lectura");
            return chooser.showDialog(owner);
        });
    }
    DesktopApplication(java.util.function.Function<Stage, java.io.File> folderChooser) {
        this.folderChooser = java.util.Objects.requireNonNull(folderChooser);
    }
    private ConfigurableApplicationContext context;
    private CooperativeTask<?> active;
    private final javafx.beans.property.BooleanProperty working = new javafx.beans.property.SimpleBooleanProperty();
    private java.nio.file.Path selectedFolder;
    private boolean closeAfterWork;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void init() {
        context = new SpringApplicationBuilder(VaultCheckApplication.class).web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }
    @Override public void start(Stage stage) {
        stage.setTitle("VaultCheck · Vista previa");
        stage.getIcons().add(new Image(DesktopApplication.class.getResource("/brand/app-icon.png").toExternalForm()));
        var root = new BorderPane();
        var menu = new MenuBar();
        var file = new Menu("Archivo");
        var close = new MenuItem("Cerrar"); close.setOnAction(e -> stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST))); file.getItems().add(close);
        var help = new Menu("Ayuda");
        var about = new MenuItem("Acerca de VaultCheck");
        about.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "VaultCheck · Vista previa de desarrollo\n\nVerificación local de archivos contra referencias firmadas e identidades cifradas. La aprobación de identidades es explícita y dura esta selección.\n\nUn inventario sin referencia no comprueba integridad. Un recorrido no es una instantánea ni un análisis de malware.", ButtonType.OK).showAndWait());
        help.getItems().add(about); menu.getMenus().addAll(file, help); root.setTop(menu);
        var brand = new HBox(10, logo(), label("VaultCheck", "brand"));
        var navigation = new VBox(18, brand, label("ESPACIO LOCAL", "muted"));
        navigation.setPadding(new Insets(24, 16, 24, 16)); navigation.setPrefWidth(235);
        navigation.getStyleClass().add("sidebar");
        var verify = new Button("Verificar carpeta"); verify.setMaxWidth(Double.MAX_VALUE); verify.getStyleClass().add("selected-nav");
        var createNavigation = new Button("Crear referencia"); createNavigation.setMaxWidth(Double.MAX_VALUE); navigation.getChildren().addAll(verify, createNavigation);
        var space = new Region(); VBox.setVgrow(space, Priority.ALWAYS);
        navigation.getChildren().addAll(space, label("Procesamiento local · Sin conexión", "muted")); root.setLeft(navigation);
        var content = new VBox(12); content.setPadding(new Insets(24, 28, 24, 28));
                var folderField = field("Elige una carpeta local para inventariarla en modo lectura");
        folderField.setId("selected-folder");
        var choose = new Button("Elegir carpeta"); choose.setId("choose-folder");
        var folderLine = new HBox(12, folderField, choose); HBox.setHgrow(folderField, Priority.ALWAYS);
        var pageTitle = label("Verificar carpeta", "heading");
        var mode = label("SIN REFERENCIA · AUTENTICIDAD NO COMPROBADA", "demo-banner");
        content.getChildren().addAll(pageTitle,
                label("Compara tus archivos con una referencia firmada.", "muted"),
                mode,
                label("Carpeta", ""), folderLine,
                label("Referencia", ""), field("Sin referencia seleccionada · El inventario no verifica integridad"));
        var show = new Button("Mostrar resultados de ejemplo"); show.getStyleClass().add("primary");
        var clear = new Button("Limpiar vista");
                var run = new Button("Ejecutar prueba real"); run.getStyleClass().add("primary");
        show.getStyleClass().remove("primary");
        var cancel = new Button("Cancelar"); cancel.setDisable(true);
        var progress = new ProgressIndicator(); progress.setPrefSize(24, 24); progress.setVisible(false); progress.setManaged(false);
        var scanFolder = new Button("Inventariar carpeta"); scanFolder.setId("scan-folder"); scanFolder.setDisable(true);
                var actions = new FlowPane(12, 8, scanFolder, clear, cancel, progress);
        var demoActions = new FlowPane(12, 8, run, show);
        demoActions.setVisible(false); demoActions.managedProperty().bind(demoActions.visibleProperty());
        run.getStyleClass().remove("primary");
        var viewMenu = new Menu("Ver");
        var sidebarToggle = new CheckMenuItem("Mostrar barra lateral"); sidebarToggle.setSelected(true);
        sidebarToggle.setAccelerator(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.B,
                javafx.scene.input.KeyCombination.CONTROL_DOWN));
        sidebarToggle.selectedProperty().addListener((observable, before, visible) -> root.setLeft(visible ? navigation : null));
        viewMenu.getItems().add(sidebarToggle);
        var demoToggle = new CheckMenuItem("Herramientas de demostración");
        demoActions.visibleProperty().bind(demoToggle.selectedProperty());
        viewMenu.getItems().add(demoToggle); menu.getMenus().add(1, viewMenu);
        content.getChildren().add(actions);
        var summary = label("Selecciona una carpeta y una referencia para empezar. También puedes obtener un inventario sin comparación.", "muted");
        summary.setWrapText(true); content.getChildren().add(summary);
        var search = new TextField(); search.setPromptText("Buscar archivo…"); search.setAccessibleText("Buscar archivo");
        var source = FXCollections.<Row>observableArrayList();
        var filtered = new FilteredList<>(source);
        var filterGroup = new ToggleGroup();
        var all = new ToggleButton("Todos · 0"); all.setId("filter-all");
        var differences = new ToggleButton("Diferencias · 0"); differences.setId("filter-differences");
        var unavailable = new ToggleButton("No verificables · 0"); unavailable.setId("filter-unavailable");
        var issues = new ToggleButton("Incidencias · 0"); issues.setId("filter-issues");
        for (var toggle : java.util.List.of(all, differences, unavailable, issues)) { toggle.setToggleGroup(filterGroup); toggle.getStyleClass().add("result-filter"); }
        all.setSelected(true);
        var shown = label("0 filas visibles", "muted"); shown.setId("visible-count");
        Runnable applyFilter = () -> {
            var selected = filterGroup.getSelectedToggle();
            String query = search.getText().toLowerCase(java.util.Locale.ROOT);
            filtered.setPredicate(row -> row.path().toLowerCase(java.util.Locale.ROOT).contains(query)
                    && (selected == all || selected == null || selected == differences && row.isDifference()
                    || selected == unavailable && row.isUnavailable() || selected == issues && row.issue()));
            shown.setText(filtered.size() + " de " + source.size() + " filas visibles");
        };
        search.textProperty().addListener((o, before, value) -> applyFilter.run());
        filterGroup.selectedToggleProperty().addListener((o, before, selected) -> {
            if (selected == null) all.setSelected(true); else applyFilter.run();
        });
        source.addListener((javafx.collections.ListChangeListener<Row>) change -> {
            all.setText("Todos · " + source.size());
            differences.setText("Diferencias · " + source.stream().filter(Row::isDifference).count());
            unavailable.setText("No verificables · " + source.stream().filter(Row::isUnavailable).count());
            issues.setText("Incidencias · " + source.stream().filter(Row::issue).count());
            if (source.isEmpty()) all.setSelected(true);
            applyFilter.run();
        });
        search.setPrefWidth(220);
        var filters = new FlowPane(8, 8, all, differences, unavailable, issues, search);
        var table = new TableView<Row>(filtered);
        var empty = label("Aún no hay resultados", "muted");
        empty.textProperty().bind(javafx.beans.binding.Bindings.when(javafx.beans.binding.Bindings.isEmpty(source)).then("Aún no hay resultados").otherwise("Ninguna fila coincide con los filtros. Consulta la cobertura del resultado."));
        empty.setWrapText(true); table.setPlaceholder(empty);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        var path = new TableColumn<Row, String>("Archivo"); path.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().path())); path.setPrefWidth(440);
        var status = new TableColumn<Row, String>("Estado"); status.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().status())); status.setPrefWidth(150);
        status.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean emptyCell) {
                super.updateItem(value, emptyCell);
                setText(null); setGraphic(null);
                if (!emptyCell && value != null) {
                    var dot = new javafx.scene.shape.Circle(3);
                    dot.setFill(javafx.scene.paint.Color.web(switch (value) {
                        case "Coincidente" -> "#22c55e";
                        case "Modificado", "Añadido", "Ausente" -> "#f59e0b";
                        default -> "#a3a3a3";
                    }));
                    var state = new HBox(8, dot, new Label(value));
                    state.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    setGraphic(state); setAccessibleText(value);
                } else setAccessibleText(null);
            }
        });
        table.getColumns().add(path); table.getColumns().add(status);
        var detail = label("Selecciona una fila para revisar sus detalles.", ""); detail.setWrapText(true);
        var inspector = new VBox(16, label("Detalle del archivo", "section-title"), detail);
        inspector.setPadding(new Insets(20)); inspector.setPrefWidth(300); inspector.setMinWidth(260); inspector.getStyleClass().add("inspector");
        var closeInspector = new Button("Cerrar"); closeInspector.setAccessibleText("Cerrar detalle del archivo");
        var inspectorTitle = new HBox(12, label("Detalle del archivo", "section-title"), closeInspector);
        inspectorTitle.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        inspector.getChildren().set(0, inspectorTitle);
        var split = new SplitPane(table);
        split.setMinHeight(240);
        split.setPrefHeight(360);
        content.widthProperty().addListener((observable, before, width) -> {
            boolean compact = width.doubleValue() < 850;
            split.setOrientation(compact ? javafx.geometry.Orientation.VERTICAL : javafx.geometry.Orientation.HORIZONTAL);
            if (split.getItems().size() == 2) split.setDividerPositions(compact ? .55 : .70);
        });
        table.getSelectionModel().selectedItemProperty().addListener((o, old, row) -> {
            if (row == null) { split.getItems().remove(inspector); detail.setText(""); }
            else {
                detail.setText(row.path() + "\n\n" + row.status() + "\n\n" + row.evidence());
                if (!split.getItems().contains(inspector)) { split.getItems().add(inspector); split.setDividerPositions(content.getWidth() < 850 ? .55 : .70); }
            }
        });
        closeInspector.setOnAction(event -> { table.getSelectionModel().clearSelection(); table.requestFocus(); });
        VBox.setVgrow(split, Priority.ALWAYS); content.getChildren().addAll(demoActions, new Separator(), label("Archivos", "section-title"), filters, split, shown);
        show.setOnAction(e -> { mode.setText("EJEMPLO VISUAL · DATOS FICTICIOS · SIN VERIFICACIÓN");
            source.setAll(new Row("notas.txt", "Coincidente"), new Row("proyectos/memoria.pdf", "Modificado"),
                    new Row("proyectos/esquema.svg", "Añadido"), new Row("archivo/borrador.txt", "Ausente"), new Row("inventario.csv", "Coincidente"));
            summary.setText("Ejemplo: 5 archivos · 3 diferencias. Autenticidad y cobertura reales: no comprobadas.");
        });
        clear.setOnAction(e -> { source.clear(); search.clear(); summary.setText("Sin operación realizada."); });
                Runnable finish = () -> {
            active = null; working.set(false); choose.setDisable(false); scanFolder.setDisable(selectedFolder == null); run.setDisable(false); show.setDisable(false); clear.setDisable(false);
            cancel.setDisable(true); progress.setVisible(false); progress.setManaged(false);
            if (closeAfterWork) stage.close();
        };
        var referencePanel = new ReferencePanel(stage, () -> selectedFolder, task -> {
            if (active != null) throw new IllegalStateException("Concurrent operation");
            active = task; working.set(true); source.clear(); search.clear();
            choose.setDisable(true); scanFolder.setDisable(true); run.setDisable(true); show.setDisable(true); clear.setDisable(true);
            cancel.setDisable(false); progress.setVisible(true); progress.setManaged(true);
            mode.setText("REFERENCIA SELECCIONADA · REVISIÓN / VERIFICACIÓN EN CURSO");
            worker.execute(task);
        }, finish, result -> {
            source.clear();
            for (var difference : result.differences()) source.add(new Row(difference.path().value(), statusText(difference.status()),
                    "Comparación con referencia firmada. Identidad aprobada para esta selección.\nSHA-256 de identidad: " + result.signerFingerprint()));
            for (var issue : result.issues()) source.add(new Row(issue.relativePath(), "Incidencia", "Recorrido: " + issue.reason(), true));
            mode.setText("FIRMA VÁLIDA · IDENTIDAD APROBADA PARA ESTA SELECCIÓN");
            summary.setText("Cobertura: " + result.coverage() + " · Omisiones de referencia: " + result.referenceOmissions()
                    + " · Incidencias: " + result.issues().size() + " · No es una instantánea ni un análisis de malware.");
        }, text -> { source.clear(); mode.setText("REFERENCIA SELECCIONADA · SIN NUEVA COMPARACIÓN COMPLETADA"); summary.setText(text); });
        referencePanel.disableProperty().bind(working);
        var referenceSection = new TitledPane("Referencia firmada e identidad", referencePanel); referenceSection.setAnimated(false);
        referenceSection.setExpanded(true);
        content.getChildren().set(6, referenceSection);
        source.addListener((javafx.collections.ListChangeListener<Row>) change -> { if (!source.isEmpty()) referenceSection.setExpanded(false); });
        var createPanel = new CreateReferencePanel(stage, () -> selectedFolder, task -> {
            if (active != null) throw new IllegalStateException("Concurrent operation");
            active = task; working.set(true); source.clear(); search.clear();
            choose.setDisable(true); scanFolder.setDisable(true); run.setDisable(true); show.setDisable(true); clear.setDisable(true);
            cancel.setDisable(false); progress.setVisible(true); progress.setManaged(true);
            mode.setText("CREACIÓN DE REFERENCIA · OPERACIÓN EN CURSO");
            summary.setText("Consulta el estado de creación en el panel.");
            worker.execute(task);
        }, () -> { mode.setText("CREACIÓN DE REFERENCIA · CONSULTA EL RESULTADO DEL PANEL"); finish.run(); });
        createPanel.disableProperty().bind(working);
        var createSection = new TitledPane("Crear referencia firmada", createPanel); createSection.setExpanded(false);
        content.getChildren().add(7, createSection);
        createNavigation.setOnAction(event -> { referenceSection.setExpanded(false); createSection.setExpanded(true); });
        var identityPanel = new IdentityPanel(stage, task -> {
            if (active != null) throw new IllegalStateException("Concurrent operation");
            active = task; working.set(true);
            choose.setDisable(true); scanFolder.setDisable(true); run.setDisable(true); show.setDisable(true); clear.setDisable(true);
            cancel.setDisable(false); progress.setVisible(true); progress.setManaged(true);
            mode.setText("CREACIÓN DE IDENTIDAD · CONSULTA EL RESULTADO DEL PANEL");
            source.clear(); summary.setText("Creando una identidad local cifrada…"); worker.execute(task);
        }, finish, identity -> {
            createPanel.useIdentity(identity);
            referencePanel.useLocalIdentity(identity.fingerprint());
        });
        identityPanel.disableProperty().bind(working);
        var identitySection = new TitledPane("Identidad local", identityPanel); identitySection.setExpanded(false);
        content.getChildren().add(8, identitySection);
        var identityNavigation = new Button("Identidades"); identityNavigation.setMaxWidth(Double.MAX_VALUE);
        navigation.getChildren().add(4, identityNavigation);
        for (var section : java.util.List.of(referenceSection, createSection, identitySection)) {
            section.managedProperty().bind(section.visibleProperty());
        }
        createSection.setVisible(false); identitySection.setVisible(false);
        var navigationButtons = java.util.List.of(verify, createNavigation, identityNavigation);
        navigationButtons.forEach(button -> { button.getStyleClass().add("navigation-button"); button.disableProperty().bind(working); });
        java.util.function.Consumer<Button> navigate = selected -> {
            navigationButtons.forEach(button -> button.getStyleClass().remove("selected-nav"));
            selected.getStyleClass().add("selected-nav"); pageTitle.setText(selected.getText());
            referenceSection.setVisible(selected == verify);
            createSection.setVisible(selected == createNavigation); createSection.setExpanded(selected == createNavigation);
            identitySection.setVisible(selected == identityNavigation); identitySection.setExpanded(selected == identityNavigation);
            scanFolder.setVisible(selected == verify); scanFolder.setManaged(selected == verify);
        };
        createNavigation.setOnAction(event -> navigate.accept(createNavigation));
        identityNavigation.setOnAction(event -> navigate.accept(identityNavigation));
        cancel.setOnAction(e -> {
            if (active != null) { active.requestCancellation(); cancel.setDisable(true); summary.setText("Cancelando… Esperando al motor y a la liberación de recursos."); }
        });
        run.setOnAction(e -> {
            if (active != null) return;
            source.clear(); search.clear();
            choose.setDisable(true); scanFolder.setDisable(true); run.setDisable(true); show.setDisable(true); clear.setDisable(true); cancel.setDisable(false);
            progress.setVisible(true); progress.setManaged(true);
            mode.setText("DEMOSTRACIÓN REAL · SOLO ARCHIVOS SINTÉTICOS"); summary.setText("Comprobando archivos sintéticos en segundo plano…");
            var task = new VerificationTask(); active = task; working.set(true);
            task.setOnSucceeded(event -> {
                var result = task.getValue();
                if (task.cancellationRequested()) summary.setText("Cancelada · Motor detenido y archivos temporales eliminados. Resultados descartados.");
                else {
                    for (var difference : result.differences()) source.add(new Row(difference.path().value(), statusText(difference.status()),
                            "Archivo sintético leído por el motor. Firma válida con identidad efímera aprobada solo para esta prueba."));
                    summary.setText("Firma válida · Identidad de prueba · Cobertura: " + result.coverage()
                            + " · Incidencias: " + result.issues().size() + " · Archivos temporales eliminados.");
                }
                finish.run();
            });
            task.setOnFailed(event -> {
                summary.setText(task.getException() instanceof java.util.concurrent.CancellationException
                        ? "Cancelada · Motor detenido y archivos temporales eliminados."
                        : "No se pudo completar la prueba o su limpieza. No se declara una verificación correcta; pueden quedar archivos sintéticos.");
                finish.run();
            });
            worker.execute(task);
        });
                choose.setOnAction(e -> {
            var selected = folderChooser.apply(stage);
            if (selected == null) return;
            selectedFolder = selected.toPath(); folderField.setText(selectedFolder.toString());
            source.clear(); search.clear(); scanFolder.setDisable(false);
            mode.setText("INVENTARIO · SIN REFERENCIA · AUTENTICIDAD NO COMPROBADA");
            summary.setText("Carpeta seleccionada. Pulsa Inventariar carpeta para leerla. Límite: 1.000 archivos; sin soporte NAS todavía.");
        });
        scanFolder.setOnAction(e -> {
            if (active != null || selectedFolder == null) return;
            source.clear(); search.clear();
            mode.setText("INVENTARIO · SIN REFERENCIA · AUTENTICIDAD NO COMPROBADA");
            choose.setDisable(true); scanFolder.setDisable(true); run.setDisable(true); show.setDisable(true); clear.setDisable(true);
            cancel.setDisable(false); progress.setVisible(true); progress.setManaged(true);
            summary.setText("Leyendo carpeta en segundo plano… No se modifica su contenido.");
            var task = new FolderScanTask(selectedFolder); active = task; working.set(true);
            task.setOnSucceeded(event -> {
                var result = task.getValue();
                for (var entry : result.entries()) source.add(new Row(entry.path().value(), "Leído · Sin comparar",
                        "Tamaño: " + entry.expected().size() + " bytes\nSHA-256: " + entry.expected().sha256()
                        + "\n\nSin referencia firmada. Este hash no demuestra integridad ni ausencia de malware."));
                for (var issue : result.issues()) source.add(new Row(issue.relativePath(), "No verificable",
                        "Incidencia del recorrido: " + issue.reason() + "\nNo se infiere ausencia del archivo.", true));
                var coverage = task.cancellationRequested() ? io.vaultcheck.domain.FolderScan.Coverage.CANCELLED : result.coverage();
                summary.setText("Cobertura: " + coverage + " · Leídos: " + result.entries().size()
                        + " · Incidencias: " + result.issues().size() + " · Autenticidad no comprobada.");
                finish.run();
            });
            task.setOnFailed(event -> {
                summary.setText("No se pudo completar el inventario. Autenticidad no comprobada; no se declara integridad.");
                finish.run();
            });
            worker.execute(task);
        });
        stage.setOnCloseRequest(event -> {
            if (active == null) return;
            event.consume();
            if (closeAfterWork) return;
            var decision = new Alert(Alert.AlertType.CONFIRMATION, "Hay una comprobación en curso. ¿Cancelar y cerrar cuando el motor termine?", ButtonType.YES, ButtonType.NO);
            decision.initOwner(stage);
            if (decision.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                closeAfterWork = true;
                if (active == null) stage.close(); else cancel.fire();
            }
        });
        verify.setOnAction(e -> { navigate.accept(verify); search.requestFocus(); });
        var viewport = new ScrollPane(content);
        viewport.setId("workspace-scroll"); viewport.setFitToWidth(true);
        viewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        viewport.viewportBoundsProperty().addListener((observable, before, bounds) -> content.setMinHeight(bounds.getHeight()));
        root.setCenter(viewport);
        var scene = new Scene(root, 1280, 900);
        scene.getStylesheets().add(DesktopApplication.class.getResource("/desktop.css").toExternalForm());
        stage.setScene(scene); stage.setMinWidth(720); stage.setMinHeight(480);
        var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1280, screen.getWidth())); stage.setHeight(Math.min(940, screen.getHeight()));
        stage.show();
        WindowsWindowTheme.apply(stage.getTitle());
    }
    private static Label label(String text, String style) {
        var label = new Label(text); if (!style.isEmpty()) label.getStyleClass().add(style); return label;
    }
    private static TextField field(String value) { var field = new TextField(value); field.setEditable(false); return field; }
    private static Pane logo() {
        // Preserve the supplied composition, including its background, using embedded PNG layers only.
        var group = new Pane(); group.setMinSize(36, 36); group.setPrefSize(36, 36); group.setMaxSize(36, 36);
        addLayer(group, "_Image1.png", 0, 0, 1000, 1000);
        addLayer(group, "_Image2.png", 444 * 1.873024 - 454.305768, 431 * 1.873024 - 430.892967, 241 * 1.873024, 214 * 1.873024);
        addLayer(group, "_Image3.png", 334 * 1.873024 - 454.305768, 332 * 1.873024 - 430.892967, 348 * 1.873024, 330 * 1.873024);
        group.setAccessibleText("Logo de VaultCheck"); return group;
    }
    private static void addLayer(Pane pane, String name, double x, double y, double width, double height) {
        var view = new ImageView(new Image(DesktopApplication.class.getResource("/brand/" + name).toExternalForm()));
        view.setLayoutX(x * .036); view.setLayoutY(y * .036); view.setFitWidth(width * .036); view.setFitHeight(height * .036); pane.getChildren().add(view);
    }
    @Override public void stop() { if (active != null) active.requestCancellation(); worker.shutdown(); if (context != null) context.close(); }
        private static String statusText(io.vaultcheck.application.VerifyFolder.Status status) {
        return switch (status) {
            case MATCHED -> "Coincidente"; case MODIFIED -> "Modificado"; case ADDED -> "Añadido";
            case ABSENT -> "Ausente"; case NOT_VERIFIABLE -> "No verificable";
        };
    }
        private record Row(String path, String status, String evidence, boolean issue) {
        Row(String path, String status, String evidence) { this(path, status, evidence, false); }
        boolean isDifference() { return !issue && (status.equals("Modificado") || status.equals("Añadido") || status.equals("Ausente")); }
        boolean isUnavailable() { return status.equals("No verificable"); }
        Row(String path, String status) { this(path, status, "Ejemplo visual ficticio. No se ha leído este archivo ni comprobado su firma."); }
    }
}









