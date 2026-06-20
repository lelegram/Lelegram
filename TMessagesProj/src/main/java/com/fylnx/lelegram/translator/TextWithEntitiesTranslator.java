package com.fylnx.lelegram.translator;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TranslateAlert2;

import java.util.HashMap;
import java.util.List;

import com.fylnx.lelegram.LeleConfig;
import com.fylnx.lelegram.translator.html.HTMLKeeper;
import com.fylnx.lelegram.vendor.translator.BaiduTranslator;
import com.fylnx.lelegram.vendor.translator.BaseTranslator;
import com.fylnx.lelegram.vendor.translator.DeepLTranslator;
import com.fylnx.lelegram.vendor.translator.GoogleAppTranslator;
import com.fylnx.lelegram.vendor.translator.LingoTranslator;
import com.fylnx.lelegram.vendor.translator.MicrosoftTranslator;
import com.fylnx.lelegram.vendor.translator.SogouTranslator;
import com.fylnx.lelegram.vendor.translator.TranSmartTranslator;
import com.fylnx.lelegram.vendor.translator.YandexTranslator;
import com.fylnx.lelegram.vendor.translator.YouDaoTranslator;

public class TextWithEntitiesTranslator implements Translator.ITranslator {

    private static final HashMap<String, TextWithEntitiesTranslator> wrappedTranslators = new HashMap<>();

    public static TextWithEntitiesTranslator of(String type) {
        return wrappedTranslators.computeIfAbsent(type, type1 -> {
            var translator = switch (type1) {
                case Translator.PROVIDER_YANDEX -> YandexTranslator.getInstance();
                case Translator.PROVIDER_LINGO -> LingoTranslator.getInstance();
                case Translator.PROVIDER_DEEPL -> {
                    DeepLTranslator.setFormality(LeleConfig.deepLFormality);
                    yield DeepLTranslator.getInstance();
                }
                case Translator.PROVIDER_MICROSOFT -> MicrosoftTranslator.getInstance();
                case Translator.PROVIDER_YOUDAO -> YouDaoTranslator.getInstance();
                case Translator.PROVIDER_BAIDU -> BaiduTranslator.getInstance();
                case Translator.PROVIDER_SOGOU -> SogouTranslator.getInstance();
                case Translator.PROVIDER_TENCENT -> TranSmartTranslator.getInstance();
                default -> GoogleAppTranslator.getInstance();
            };
            return new TextWithEntitiesTranslator(translator);
        });
    }

    private final BaseTranslator translator;

    private TextWithEntitiesTranslator(BaseTranslator translator) {
        this.translator = translator;
    }

    @Override
    public Translator.TranslationResult translate(TLRPC.TL_textWithEntities query, String fl, String tl) throws Exception {
        if (LeleConfig.keepFormatting) {
            var html = HTMLKeeper.entitiesToHtml(query.text, query.entities, false);
            var result = translator.translate(html, null, tl);
            var textAndEntitiesTranslated = HTMLKeeper.htmlToEntities(result.translation, query.entities, false);
            return Translator.TranslationResult.of(
                    TranslateAlert2.preprocess(query, textAndEntitiesTranslated),
                    result.sourceLanguage
            );
        } else {
            var result = translator.translate(query.text, null, tl);
            return Translator.TranslationResult.of(Translator.textWithEntities(result.translation, null), result.sourceLanguage);
        }
    }

    @Override
    public boolean supportLanguage(String language) {
        return translator.supportLanguage(language);
    }

    @Override
    public List<String> getTargetLanguages() {
        return translator.getTargetLanguages();
    }
}
