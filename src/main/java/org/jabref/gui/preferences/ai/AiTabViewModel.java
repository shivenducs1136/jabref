package org.jabref.gui.preferences.ai;

import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.jabref.gui.preferences.PreferenceTabViewModel;
import org.jabref.logic.ai.AiDefaultPreferences;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.strings.StringUtil;
import org.jabref.preferences.AiPreferences;
import org.jabref.preferences.PreferencesService;

import de.saxsys.mvvmfx.utils.validation.FunctionBasedValidator;
import de.saxsys.mvvmfx.utils.validation.ValidationMessage;
import de.saxsys.mvvmfx.utils.validation.ValidationStatus;
import de.saxsys.mvvmfx.utils.validation.Validator;

public class AiTabViewModel implements PreferenceTabViewModel {
    private final BooleanProperty useAi = new SimpleBooleanProperty();

    private final StringProperty openAiToken = new SimpleStringProperty();

    private final BooleanProperty customizeSettings = new SimpleBooleanProperty();

    private final StringProperty chatModel = new SimpleStringProperty();
    private final ObjectProperty<AiPreferences.EmbeddingModel> embeddingModel = new SimpleObjectProperty<>();
    private final StringProperty apiBaseUrl = new SimpleStringProperty();

    private final StringProperty instruction = new SimpleStringProperty();
    private final DoubleProperty temperature = new SimpleDoubleProperty();
    private final IntegerProperty contextWindowSize = new SimpleIntegerProperty();
    private final IntegerProperty documentSplitterChunkSize = new SimpleIntegerProperty();
    private final IntegerProperty documentSplitterOverlapSize = new SimpleIntegerProperty();
    private final IntegerProperty ragMaxResultsCount = new SimpleIntegerProperty();
    private final DoubleProperty ragMinScore = new SimpleDoubleProperty();

    private final AiPreferences aiPreferences;

    private final Validator openAiTokenValidator;
    private final Validator chatModelValidator;
    private final Validator apiBaseUrlValidator;
    private final Validator instructionValidator;
    private final Validator temperatureValidator;
    private final Validator contextWindowSizeValidator;
    private final Validator documentSplitterChunkSizeValidator;
    private final Validator documentSplitterOverlapSizeValidator;
    private final Validator ragMaxResultsCountValidator;
    private final Validator ragMinScoreValidator;

    public AiTabViewModel(PreferencesService preferencesService) {
        this.aiPreferences = preferencesService.getAiPreferences();

        this.openAiTokenValidator = new FunctionBasedValidator<>(
                openAiToken,
                token -> !StringUtil.isBlank(token),
                ValidationMessage.error(Localization.lang("An OpenAI token has to be provided")));

        this.chatModelValidator = new FunctionBasedValidator<>(
                openAiToken,
                token -> !StringUtil.isBlank(token),
                ValidationMessage.error(Localization.lang("Chat model has to be provided")));

        this.apiBaseUrlValidator = new FunctionBasedValidator<>(
                apiBaseUrl,
                token -> !StringUtil.isBlank(token),
                ValidationMessage.error(Localization.lang("API base URL has to be provided")));

        this.instructionValidator = new FunctionBasedValidator<>(
                instruction,
                message -> !StringUtil.isBlank(message),
                ValidationMessage.error(Localization.lang("The instruction has to be provided")));

        // Source: https://platform.openai.com/docs/api-reference/chat/create#chat-create-temperature
        this.temperatureValidator = new FunctionBasedValidator<>(
                temperature,
                temp -> temp.doubleValue() >= 0 && temp.doubleValue() <= 2,
                ValidationMessage.error(Localization.lang("Temperature must be between 0 and 2")));

        this.contextWindowSizeValidator = new FunctionBasedValidator<>(
                contextWindowSize,
                size -> size.intValue() > 0,
                ValidationMessage.error(Localization.lang("Context window size must be greater than 0")));

        this.documentSplitterChunkSizeValidator = new FunctionBasedValidator<>(
                documentSplitterChunkSize,
                size -> size.intValue() > 0,
                ValidationMessage.error(Localization.lang("Document splitter chunk size must be greater than 0")));

        this.documentSplitterOverlapSizeValidator = new FunctionBasedValidator<>(
                documentSplitterOverlapSize,
                size -> size.intValue() > 0 && size.intValue() < documentSplitterChunkSize.get(),
                ValidationMessage.error(Localization.lang("Document splitter overlap size must be greater than 0 and less than chunk size")));

        this.ragMaxResultsCountValidator = new FunctionBasedValidator<>(
                ragMaxResultsCount,
                count -> count.intValue() > 0,
                ValidationMessage.error(Localization.lang("RAG max results count must be greater than 0")));

        this.ragMinScoreValidator = new FunctionBasedValidator<>(
                ragMinScore,
                score -> score.doubleValue() > 0 && score.doubleValue() < 1,
                ValidationMessage.error(Localization.lang("RAG min score must be greater than 0 and less than 1")));
    }

    @Override
    public void setValues() {
        useAi.setValue(aiPreferences.getEnableChatWithFiles());
        openAiToken.setValue(aiPreferences.getOpenAiToken());

        customizeSettings.setValue(aiPreferences.getCustomizeSettings());

        chatModel.setValue(aiPreferences.getChatModel());
        embeddingModel.setValue(aiPreferences.getEmbeddingModel());
        apiBaseUrl.setValue(aiPreferences.getApiBaseUrl());

        instruction.setValue(aiPreferences.getInstruction());
        temperature.setValue(aiPreferences.getTemperature());
        contextWindowSize.setValue(aiPreferences.getContextWindowSize());
        documentSplitterChunkSize.setValue(aiPreferences.getDocumentSplitterChunkSize());
        documentSplitterOverlapSize.setValue(aiPreferences.getDocumentSplitterOverlapSize());
        ragMaxResultsCount.setValue(aiPreferences.getRagMaxResultsCount());
        ragMinScore.setValue(aiPreferences.getRagMinScore());
    }

    @Override
    public void storeSettings() {
        aiPreferences.setEnableChatWithFiles(useAi.get());
        aiPreferences.setOpenAiToken(openAiToken.get());

        aiPreferences.setCustomizeSettings(customizeSettings.get());

        if (customizeSettings.get()) {
            aiPreferences.setChatModel(chatModel.get());
            aiPreferences.setEmbeddingModel(embeddingModel.get());
            aiPreferences.setApiBaseUrl(apiBaseUrl.get());

            aiPreferences.setInstruction(instruction.get());
            aiPreferences.setTemperature(temperature.get());
            aiPreferences.setContextWindowSize(contextWindowSize.get());
            aiPreferences.setDocumentSplitterChunkSize(documentSplitterChunkSize.get());
            aiPreferences.setDocumentSplitterOverlapSize(documentSplitterOverlapSize.get());
            aiPreferences.setRagMaxResultsCount(ragMaxResultsCount.get());
            aiPreferences.setRagMinScore(ragMinScore.get());
        } else {
            resetExpertSettings();
        }
    }

    public void resetExpertSettings() {
        aiPreferences.setChatModel(AiDefaultPreferences.CHAT_MODEL);
        chatModel.setValue(AiDefaultPreferences.CHAT_MODEL);

        aiPreferences.setApiBaseUrl(AiDefaultPreferences.API_BASE_URL);
        apiBaseUrl.setValue(AiDefaultPreferences.API_BASE_URL);

        aiPreferences.setInstruction(AiDefaultPreferences.SYSTEM_MESSAGE);
        instruction.set(AiDefaultPreferences.SYSTEM_MESSAGE);

        aiPreferences.setContextWindowSize(AiDefaultPreferences.CONTEXT_WINDOW_SIZE);
        contextWindowSize.set(AiDefaultPreferences.CONTEXT_WINDOW_SIZE);

        aiPreferences.setDocumentSplitterChunkSize(AiDefaultPreferences.DOCUMENT_SPLITTER_CHUNK_SIZE);
        documentSplitterChunkSize.set(AiDefaultPreferences.DOCUMENT_SPLITTER_CHUNK_SIZE);

        aiPreferences.setDocumentSplitterOverlapSize(AiDefaultPreferences.DOCUMENT_SPLITTER_OVERLAP);
        documentSplitterOverlapSize.set(AiDefaultPreferences.DOCUMENT_SPLITTER_OVERLAP);

        aiPreferences.setRagMaxResultsCount(AiDefaultPreferences.RAG_MAX_RESULTS_COUNT);
        ragMaxResultsCount.set(AiDefaultPreferences.RAG_MAX_RESULTS_COUNT);

        aiPreferences.setRagMinScore(AiDefaultPreferences.RAG_MIN_SCORE);
        ragMinScore.set(AiDefaultPreferences.RAG_MIN_SCORE);
    }

    @Override
    public boolean validateSettings() {
        if (useAi.get()) {
            if (customizeSettings.get()) {
                return validateBasicSettings() && validateExpertSettings();
            } else {
                return validateBasicSettings();
            }
        }

        return true;
    }

    public boolean validateBasicSettings() {
        return openAiTokenValidator.getValidationStatus().isValid();
    }

    public boolean validateExpertSettings() {
        List<Validator> validators = List.of(
                chatModelValidator,
                apiBaseUrlValidator,
                instructionValidator,
                temperatureValidator,
                contextWindowSizeValidator,
                documentSplitterChunkSizeValidator,
                documentSplitterOverlapSizeValidator,
                ragMaxResultsCountValidator,
                ragMinScoreValidator
        );

        return validators.stream().map(Validator::getValidationStatus).allMatch(ValidationStatus::isValid);
    }

    public StringProperty openAiTokenProperty() {
        return openAiToken;
    }

    public BooleanProperty useAiProperty() {
        return useAi;
    }

    public boolean getUseAi() {
        return useAi.get();
    }

    public BooleanProperty customizeSettingsProperty() {
        return customizeSettings;
    }

    public boolean getCustomizeSettings() {
        return customizeSettings.get();
    }

    public StringProperty chatModelProperty() {
        return chatModel;
    }

    public ObjectProperty<AiPreferences.EmbeddingModel> embeddingModelProperty() {
        return embeddingModel;
    }

    public StringProperty apiBaseUrlProperty() {
        return apiBaseUrl;
    }

    public StringProperty instructionProperty() {
        return instruction;
    }

    public DoubleProperty temperatureProperty() {
        return temperature;
    }

    public IntegerProperty contextWindowSizeProperty() {
        return contextWindowSize;
    }

    public IntegerProperty documentSplitterChunkSizeProperty() {
        return documentSplitterChunkSize;
    }

    public IntegerProperty documentSplitterOverlapSizeProperty() {
        return documentSplitterOverlapSize;
    }

    public IntegerProperty ragMaxResultsCountProperty() {
        return ragMaxResultsCount;
    }

    public DoubleProperty ragMinScoreProperty() {
        return ragMinScore;
    }

    public ValidationStatus getOpenAiTokenValidationStatus() {
        return openAiTokenValidator.getValidationStatus();
    }

    public ValidationStatus getChatModelValidationStatus() {
        return chatModelValidator.getValidationStatus();
    }

    public ValidationStatus getApiBaseUrlValidationStatus() {
        return apiBaseUrlValidator.getValidationStatus();
    }

    public ValidationStatus getSystemMessageValidationStatus() {
        return instructionValidator.getValidationStatus();
    }

    public ValidationStatus getTemperatureValidationStatus() {
        return temperatureValidator.getValidationStatus();
    }

    public ValidationStatus getMessageWindowSizeValidationStatus() {
        return contextWindowSizeValidator.getValidationStatus();
    }

    public ValidationStatus getDocumentSplitterChunkSizeValidationStatus() {
        return documentSplitterChunkSizeValidator.getValidationStatus();
    }

    public ValidationStatus getDocumentSplitterOverlapSizeValidationStatus() {
        return documentSplitterOverlapSizeValidator.getValidationStatus();
    }

    public ValidationStatus getRagMaxResultsCountValidationStatus() {
        return ragMaxResultsCountValidator.getValidationStatus();
    }

    public ValidationStatus getRagMinScoreValidationStatus() {
        return ragMinScoreValidator.getValidationStatus();
    }
}
