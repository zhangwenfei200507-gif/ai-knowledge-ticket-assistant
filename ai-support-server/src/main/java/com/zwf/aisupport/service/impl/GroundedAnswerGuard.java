package com.zwf.aisupport.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GroundedAnswerGuard {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFERENCES_HEADING = Pattern.compile("(?im)^\\s*(?:#{1,3}\\s*)?references\\s*$");

    boolean containsOnlyVerifiedUrls(String answer, Collection<String> verifiedUrls) {
        Set<String> allowed = verifiedUrls == null ? Set.of() : verifiedUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(this::normalizeUrl)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        String body = removeReferencesSection(answer);
        Matcher matcher = URL_PATTERN.matcher(body);
        while (matcher.find()) {
            if (!allowed.contains(normalizeUrl(matcher.group()))) {
                return false;
            }
        }
        return true;
    }

    String replaceReferencesWithVerifiedSources(String answer, List<String> verifiedReferences) {
        String body = removeReferencesSection(answer).trim();
        String referenceList = verifiedReferences == null || verifiedReferences.isEmpty()
                ? "- See the verified grounding sources shown with this answer."
                : verifiedReferences.stream().map(reference -> "- " + reference)
                        .reduce((left, right) -> left + "\n" + right).orElse("");
        return body + "\n\n## References\n" + referenceList;
    }

    String markUnverifiedFallback(String answer) {
        return removeReferencesSection(answer).trim()
                + "\n\n## Verification Status\n"
                + "Unverified general-model guidance. Confirm it against the target version and environment before applying it.";
    }

    private String removeReferencesSection(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        Matcher matcher = REFERENCES_HEADING.matcher(answer);
        return matcher.find() ? answer.substring(0, matcher.start()).trim() : answer;
    }

    private String normalizeUrl(String value) {
        String normalized = value.trim();
        while (!normalized.isEmpty() && ".,;:!?)]}".indexOf(normalized.charAt(normalized.length() - 1)) >= 0) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
