package com.cristoree.rocco.ai;

import com.cristoree.rocco.util.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Lleva las ultimas respuestas de Rocco y encuentra lo que anda repitiendo: grupos de tres
// palabras que aparecen en varias respuestas y arranques que usa una y otra vez. Va al bloque
// RIGHT NOW para que el modelo los evite un rato.
public final class PhraseTracker
{
    private static final int KEEP = 20;
    private static final int SHOWN = 6;

    private final Deque<List<String>> recent = new ArrayDeque<>();

    public void add(String reply)
    {
        List<String> words = new ArrayList<>();
        for (String word : Text.normalize(reply).split("[^a-z0-9]+"))
        {
            if (!word.isEmpty())
            {
                words.add(word);
            }
        }
        if (words.size() < 2)
        {
            return;
        }
        this.recent.addLast(words);
        while (this.recent.size() > KEEP)
        {
            this.recent.removeFirst();
        }
    }

    // "Phrases you've been overusing": vacio si no hay nada repetido.
    public String overused()
    {
        Map<String, Integer> trigrams = new HashMap<>();
        Map<String, Integer> openings = new HashMap<>();

        for (List<String> words : this.recent)
        {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i + 2 < words.size(); i++)
            {
                String gram = words.get(i) + " " + words.get(i + 1) + " " + words.get(i + 2);
                // Las de puras palabras cortas ("que no es") son relleno, no muletillas.
                if (gram.replace(" ", "").length() >= 9 && seen.add(gram))
                {
                    trigrams.merge(gram, 1, Integer::sum);
                }
            }
            openings.merge(words.get(0) + " " + words.get(1), 1, Integer::sum);
        }

        List<String> out = new ArrayList<>();
        trigrams.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(SHOWN)
                .forEach(entry -> out.add("\"" + entry.getKey() + "\""));
        openings.entrySet().stream()
                .filter(entry -> entry.getValue() >= 3)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(2)
                .forEach(entry -> out.add("starting with \"" + entry.getKey() + "\""));
        return String.join(", ", out);
    }
}
