package com.scss.settings;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.UIUtil;
import com.intellij.webcore.ui.SwingHelper;
import com.scss.ScssLintProjectComponent;
import com.scss.utils.ScssLintFinder;
import com.scss.utils.ScssLintRunner;
import com.wix.settings.ValidationInfo;
import com.wix.settings.ValidationUtils;
import com.wix.ui.PackagesNotificationPanel;
import com.wix.utils.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ScssLintSettingsPage implements Configurable {
    public static final String FIX_IT = "Fix it";
    public static final String HOW_TO_USE_SCSSLINT = "How to Use SCSS Lint";
    public static final String HOW_TO_USE_LINK = "https://github.com/idok/scss-lint-plugin";
    protected Project project;

    private JCheckBox pluginEnabledCheckbox;
    private JPanel panel;
    private JPanel errorPanel;
    private TextFieldWithHistoryWithBrowseButton scssLintConfigFile;
    private JRadioButton searchForConfigInRadioButton;
    private JRadioButton useProjectConfigRadioButton;
    private HyperlinkLabel usageLink;
    private JLabel ScssLintConfigFilePathLabel;
    private JCheckBox treatAllIssuesCheckBox;
    private JLabel versionLabel;
    private JLabel scssLintExeLabel;
    private TextFieldWithHistoryWithBrowseButton scssLintExeField;
    private final PackagesNotificationPanel packagesNotificationPanel;

    public ScssLintSettingsPage(@NotNull final Project project) {
        this.project = project;
        configESLintBinField();
        configScssLintConfigField();
        useProjectConfigRadioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                scssLintConfigFile.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        pluginEnabledCheckbox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                boolean enabled = e.getStateChange() == ItemEvent.SELECTED;
                setEnabledState(enabled);
            }
        });

        this.packagesNotificationPanel = new PackagesNotificationPanel(project);
        errorPanel.add(this.packagesNotificationPanel.getComponent(), BorderLayout.CENTER);

        DocumentAdapter docAdp = new DocumentAdapter() {
            protected void textChanged(DocumentEvent e) {
                updateLaterInEDT();
            }
        };
        scssLintExeField.getChildComponent().getTextEditor().getDocument().addDocumentListener(docAdp);
        scssLintConfigFile.getChildComponent().getTextEditor().getDocument().addDocumentListener(docAdp);
        getVersion();
    }

    private void updateLaterInEDT() {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
            public void run() {
                ScssLintSettingsPage.this.update();
            }
        });
    }

    private void update() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        validate();
    }

    private void setEnabledState(boolean enabled) {
        scssLintConfigFile.setEnabled(enabled);
        searchForConfigInRadioButton.setEnabled(enabled);
        useProjectConfigRadioButton.setEnabled(enabled);
        scssLintExeField.setEnabled(enabled);
        ScssLintConfigFilePathLabel.setEnabled(enabled);
        scssLintExeLabel.setEnabled(enabled);
        treatAllIssuesCheckBox.setEnabled(enabled);
    }

    private void validate() {
        List<ValidationInfo> errors = new ArrayList<ValidationInfo>();
        if (!ValidationUtils.validatePath(project, scssLintExeField.getChildComponent().getText(), false)) {
            ValidationInfo error = new ValidationInfo(scssLintExeField.getChildComponent().getTextEditor(), "Path to scss lint exe is invalid {{LINK}}", FIX_IT);
            errors.add(error);
        }
        if (!ValidationUtils.validatePath(project, scssLintConfigFile.getChildComponent().getText(), true)) {
            ValidationInfo error = new ValidationInfo(scssLintConfigFile.getChildComponent().getTextEditor(), "Path to scss-lint config is invalid {{LINK}}", FIX_IT); //Please correct path to
            errors.add(error);
        }
        if (errors.isEmpty()) {
            getVersion();
        }
        packagesNotificationPanel.processErrors(errors);
    }

    private ScssLintRunner.ScssLintSettings settings;

    private void getVersion() {
        if (settings != null &&
                settings.scssLintExe.equals(scssLintExeField.getChildComponent().getText()) &&
                settings.cwd.equals(project.getBasePath())) {
            return;
        }
        if (StringUtils.isEmpty(scssLintExeField.getChildComponent().getText())) {
            return;
        }
        settings = new ScssLintRunner.ScssLintSettings();
        settings.scssLintExe = scssLintExeField.getChildComponent().getText();
        settings.cwd = project.getBasePath();
        try {
            versionLabel.setText(ScssLintRunner.runVersion(settings));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void configESLintBinField() {
        TextFieldWithHistory textFieldWithHistory = scssLintExeField.getChildComponent();
        textFieldWithHistory.setHistorySize(-1);
        textFieldWithHistory.setMinimumAndPreferredWidth(0);

        SwingHelper.addHistoryOnExpansion(textFieldWithHistory, new NotNullProducer<List<String>>() {
            @NotNull
            public List<String> produce() {
//                File projectRoot = new File(project.getBaseDir().getPath());
                List<File> newFiles = ScssLintFinder.findAllScssLintExe(); //searchForESLintBin(projectRoot);
                return FileUtils.toAbsolutePath(newFiles);
            }
        });

        SwingHelper.installFileCompletionAndBrowseDialog(project, scssLintExeField, "Select SCSS Lint exe", FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    }

    private void configScssLintConfigField() {
        TextFieldWithHistory textFieldWithHistory = scssLintConfigFile.getChildComponent();
        textFieldWithHistory.setHistorySize(-1);
        textFieldWithHistory.setMinimumAndPreferredWidth(0);

        SwingHelper.addHistoryOnExpansion(textFieldWithHistory, new NotNullProducer<List<String>>() {
            @NotNull
            public List<String> produce() {
                File projectRoot = new File(project.getBaseDir().getPath());
                return ScssLintFinder.searchForLintConfigFiles(projectRoot);
            }
        });

        SwingHelper.installFileCompletionAndBrowseDialog(project, scssLintConfigFile, "Select SCSS Lint config", FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "SCSS Lint Plugin";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        loadSettings();
        return panel;
    }

    @Override
    public boolean isModified() {
        return pluginEnabledCheckbox.isSelected() != getSettings().pluginEnabled
                || !scssLintExeField.getChildComponent().getText().equals(getSettings().scssLintExecutable)
                || treatAllIssuesCheckBox.isSelected() != getSettings().treatAllIssuesAsWarnings
                || !getLintConfigFile().equals(getSettings().scssLintConfigFile);
    }

    private String getLintConfigFile() {
        return useProjectConfigRadioButton.isSelected() ? scssLintConfigFile.getChildComponent().getText() : "";
    }

    @Override
    public void apply() throws ConfigurationException {
        saveSettings();
        PsiManager.getInstance(project).dropResolveCaches();
    }

    protected void saveSettings() {
        Settings settings = getSettings();
        settings.pluginEnabled = pluginEnabledCheckbox.isSelected();
        settings.scssLintExecutable = scssLintExeField.getChildComponent().getText();
        settings.scssLintConfigFile = getLintConfigFile();
        settings.treatAllIssuesAsWarnings = treatAllIssuesCheckBox.isSelected();
        project.getComponent(ScssLintProjectComponent.class).validateSettings();
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    protected void loadSettings() {
        Settings settings = getSettings();
        pluginEnabledCheckbox.setSelected(settings.pluginEnabled);
        scssLintExeField.getChildComponent().setText(settings.scssLintExecutable);
        scssLintConfigFile.getChildComponent().setText(settings.scssLintConfigFile);
        useProjectConfigRadioButton.setSelected(StringUtils.isNotEmpty(settings.scssLintConfigFile));
        searchForConfigInRadioButton.setSelected(StringUtils.isEmpty(settings.scssLintConfigFile));
        scssLintConfigFile.setEnabled(useProjectConfigRadioButton.isSelected());
        treatAllIssuesCheckBox.setSelected(settings.treatAllIssuesAsWarnings);
        setEnabledState(settings.pluginEnabled);
    }

    @Override
    public void reset() {
        loadSettings();
    }

    @Override
    public void disposeUIResources() {
    }

    protected Settings getSettings() {
        return Settings.getInstance(project);
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        usageLink = SwingHelper.createWebHyperlink(HOW_TO_USE_SCSSLINT, HOW_TO_USE_LINK);
    }
}
