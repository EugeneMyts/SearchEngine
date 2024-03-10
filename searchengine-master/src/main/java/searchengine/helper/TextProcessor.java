package searchengine.helper;

import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TextProcessor {

    private static final String[] FUNCTIONAL_PROPERTIES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private final RussianLuceneMorphology luceneMorphology;

    public TextProcessor() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    public Map<String, Integer> countLemmas(String text) {
        String[] words = prepareText(text).split(" ");
        Map<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            String lemma = getLemma(word);
            lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
        }

        return lemmas;
    }

    public String prepareText(String text) {
        text = text.toLowerCase(Locale.ROOT)
                .replaceAll("<script.*?</script>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("([^а-яa-z\\d])", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return text;
    }

    public String getLemma(String word) {
        if (word == null) {
            return "";
        }

        if (wordsIsCyrillic(word)) {
            try {
                List<String> morphInfo = luceneMorphology.getMorphInfo(word);
                return morphInfo.get(0)
                        .split("\\|")[0];

            } catch (WrongCharaterException exception) {
                return "";
            }
        } else if (wordIsNumeric(word)) {
            return word;
        } else if (wordIsLatin(word)) {
            return word;
        } else if (wordIsFunctional(word)) {
            return "";

        }
        return "";
    }


    private boolean wordsIsCyrillic(String word) {
        return word.matches("[а-я]+");
    }

    private boolean wordIsLatin(String words) {
        if (words.length() == 0) {
            return false;
        }

        return words.matches(".*[a-z]+.*");
    }

    public boolean wordIsFunctional(String word) {
        if (word.length() == 0) {
            return false;
        } else if (wordIsLatin(word) || wordIsNumeric(word)) {
            return false;
        }
        List<String> morphInfo = luceneMorphology.getMorphInfo(word);

        for (String property : FUNCTIONAL_PROPERTIES) {
            if (morphInfo.get(0).contains(property)) {
                return true;
            }
        }

        return false;
    }

    private boolean wordIsNumeric(String word) {
        String regex = ".*\\d+.*";
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(word).matches();
    }

    public String removePunctuation(String query) {
        return query.replaceAll("[^а-яa-z0-9]", " ").trim();

    }

    public String createSnippet(String text, String query, int wordsAround) {
        Document  document = Jsoup.parse(text);
        text = document.body().text();

        String textToLowerCase = text.toLowerCase();
        String preparedText = stringText(textToLowerCase);

        String textLemmasForSnippet = getLemmasForSnippet(preparedText);
        String[] textLemmasSplit = textLemmasForSnippet.split("\\s+");
        String queryLemmas = getLemmasForSnippet(query);
        String[] queryLemmasSplit = queryLemmas.split("\\s+");
        String firstLemma = queryLemmasSplit[0];

        Integer index = getIndexOfWordInArray(textLemmasSplit, firstLemma);
        if (index == -1) {
            return null;
        }

        StringBuilder snippet = new StringBuilder();
        int startIndex = Math.max((index - wordsAround), 0);
        if (index - wordsAround > 0) {
            snippet.append("... ");
        }
        int endIndex = Math.min((index + wordsAround), textLemmasSplit.length - 1);

        String[] textWords = text.split("\\s+");
        for (int i = startIndex; i <= endIndex; i++) {
            if (i == index || arrayContains(textLemmasSplit[i], queryLemmasSplit)) {
                snippet.append("<b>").append(textWords[i]).append("</b>").append(" ");

            } else {
                snippet.append(textWords[i]).append(" ");
            }
        }

        if (index + wordsAround < textLemmasSplit.length -1) {
            snippet.append("...");
        }
        return snippet.toString();


    }

    private boolean arrayContains(String word, String[] array) {
        for (String s : array) {
            if (s.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String stringText(String textLowerCase) {

        return textLowerCase.replaceAll("(?s)<script.*</script>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String getLemmasForSnippet(String preparedText) {
        preparedText = preparedText.toLowerCase();

        StringBuilder lemmas = new StringBuilder();
        for (String word : preparedText.split("\\s+")) {

            if (word.matches("[^а-яa-z\\d]+")) {
                lemmas.append(word).append(" ");
                continue;
            }

            word = word.replaceAll("([^а-яa-z\\d])", "");
            lemmas.append(getLemma(word)).append(" ");
        }

        return lemmas.toString();
    }

    private Integer getIndexOfWordInArray(String[] textLemmasSplit, String firstLemma) {
        int index = -1;
        for (int i = 0; i < textLemmasSplit.length; i++) {
            if (textLemmasSplit[i].contains(firstLemma)) {
                index = i;
                break;
            }
        }
        return index;
    }


}
