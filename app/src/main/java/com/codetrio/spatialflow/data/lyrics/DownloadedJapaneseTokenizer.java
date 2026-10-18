package com.codetrio.spatialflow.data.lyrics;

import com.atilika.kuromoji.TokenizerBase;
import com.atilika.kuromoji.dict.Dictionary;
import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.util.ResourceResolver;
import com.atilika.kuromoji.viterbi.TokenFactory;
import com.atilika.kuromoji.viterbi.ViterbiNode;

import java.util.List;

/**
 * IPADIC tokenizer adapter derived from Atilika Kuromoji 0.9.0 (Apache-2.0).
 * Allows IPADIC dictionary binary resources to be supplied by an external archive or resolver.
 */
public final class DownloadedJapaneseTokenizer extends TokenizerBase {
    private DownloadedJapaneseTokenizer(Builder builder) {
        configure(builder);
    }

    @Override
    public List<Token> tokenize(String text) {
        return createTokenList(text);
    }

    public static DownloadedJapaneseTokenizer create(ResourceResolver resolver) {
        return new DownloadedJapaneseTokenizer(new Builder(resolver));
    }

    private static final class Builder extends TokenizerBase.Builder {
        Builder(ResourceResolver resolver) {
            this.resolver = resolver;
            totalFeatures = 9;
            readingFeature = 7;
            partOfSpeechFeature = 0;
            tokenFactory = new TokenFactory<Token>() {
                @Override
                public Token createToken(
                    int wordId,
                    String surface,
                    ViterbiNode.Type type,
                    int position,
                    Dictionary dictionary
                ) {
                    return new Token(wordId, surface, type, position, dictionary);
                }
            };
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TokenizerBase> T build() {
            return (T) new DownloadedJapaneseTokenizer(this);
        }
    }
}
