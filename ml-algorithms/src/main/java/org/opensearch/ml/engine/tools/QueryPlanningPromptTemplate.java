package org.opensearch.ml.engine.tools;

public class QueryPlanningPromptTemplate {

    public static final String DEFAULT_QUERY = "{\"size\":10,\"query\":{\"match_all\":{}}}";

    // ==== RULES ====
    public static final String QUERY_TYPE_RULES = "Use only fields present in the provided mapping; never invent names.\n"
        + "Choose query types based on user intent and field types:\n"
        + "- match: single-token full-text on analyzed text fields.\n"
        + "- match_phrase: multi-token phrases on analyzed text fields (search string contains spaces, hyphens, commas, etc.).\n"
        + "- multi_match: when multiple analyzed text fields are equally relevant.\n"
        + "- term / terms: exact match on keyword, numeric, boolean.\n"
        + "- range: numeric/date comparisons (gt, lt, gte, lte).\n"
        + "- bool with must, should, must_not, filter: AND/OR/NOT logic.\n"
        + "- wildcard / prefix on keyword: \"starts with\" / pattern matching.\n"
        + "- exists: field presence/absence.\n"
        + "- nested query / nested agg: ONLY if the mapping for that exact path (or a parent) has \"type\":\"nested\".\n"
        + "\n"
        + "Mechanics:\n"
        + "- Put exact constraints (term, terms, range, exists, prefix, wildcard) in bool.filter (non-scoring). Put full-text relevance (match, match_phrase, multi_match) in bool.must.\n"
        + "- Top N items/products/documents: return top hits (set \"size\": N as an integer) and sort by the relevant metric(s). Do not use aggregations for item lists.\n"
        + "- Spelling tolerance: match_phrase does NOT support fuzziness; use match or multi_match with \"fuzziness\": \"AUTO\" when tolerant matching is needed.\n"
        + "- Numeric note: use integers for sizes (e.g., \"size\": 5), not floats.\n";

    public static final String AGGREGATION_RULES = "Aggregations (counts, averages, grouped summaries, distributions):\n"
        + "- Use aggregations when the user asks for grouped summaries (e.g., counts by category, averages by brand, or top N categories/brands).\n"
        + "- terms on field.keyword or numeric for grouping / top N groups (not items).\n"
        + "- Metric aggs (avg, min, max, sum, stats, cardinality) on numeric fields.\n"
        + "- date_histogram, histogram, range for distributions.\n"
        + "- Always set \"size\": 0 when only aggregations are needed.\n"
        + "- Use sub-aggregations + order for \"top N groups by metric\".\n"
        + "- If grouping/filtering exactly on a text field, use its .keyword sub-field when present.\n";

    // ==== FIELD SELECTION & PROXYING ====
    public static final String FIELD_SELECTION_AND_PROXYING =
        "Goal: pick the smallest set of mapping fields that best capture the user's intent.\n"
            + "Query Fields: when provided, and present in the mapping, prioritize using them; ignore any that are not in the mapping.\n"
            + "Proxy Rule (mandatory): If at least one field is even loosely related to the intent, you MUST proceed using the best available proxy fields. Do NOT fall back to the default query due to ambiguity.\n"
            + "Selection steps:\n"
            + "- Harvest candidates from the question (entities, attributes, constraints).\n"
            + "- From query_fields (that exist) and the index mapping, choose fields that map to those candidates and the user intent—even if only loosely (use reasonable proxies).\n"
            + "- Ignore other fields that don’t help answer the question.\n"
            + "- Micro Self-Check (silent): verify chosen fields exist; if any don’t, swap to the closest mapped proxy and continue. Only if no remotely relevant fields exist at all, use the default match_all query.\n";

    public static final String PROMPT_PREFIX = "==== PURPOSE ====\n"
        + "You are an OpenSearch DSL expert. Convert a natural-language question into a strict JSON OpenSearch query body.\n\n"
        + "==== RULES ====\n"
        + QUERY_TYPE_RULES
        + "\n"
        + AGGREGATION_RULES
        + "\n"
        + "==== FIELD SELECTION & PROXYING ====\n"
        + FIELD_SELECTION_AND_PROXYING;

    public static final String OUTPUT_FORMAT_INSTRUCTIONS = "==== OUTPUT FORMAT ====\n"
        + "- Return EXACTLY ONE JSON object representing the OpenSearch request body (not an escaped string).\n"
        + "- Output NOTHING else before or after it.\n"
        + "- Do NOT use code fences or markdown: no backticks (`), no ```json, no ```.\n"
        + "- Do NOT wrap in quotes or prose: no single quotes ('), no smart quotes (’ “ ”), no angle brackets (< >), no XML/HTML, no lists, no headers, no ellipses.\n"
        + "- Use valid JSON only: standard double quotes (\") for all keys/strings; no comments; no trailing commas.\n"
        + "- If the request truly cannot be fulfilled because no remotely relevant fields exist, return EXACTLY:\n"
        + DEFAULT_QUERY
        + "\n";

    // ==== EXAMPLES ==== (Field selection lines included only where they clarify proxies vs. distractors)
    public static final String EXAMPLE_1 = "Example 1 — numeric range\n"
        + "Input: Show all products that cost more than 50 dollars.\n"
        + "Mapping: { \"properties\": { \"price\": { \"type\": \"float\" }, \"cost\": { \"type\": \"float\" }, \"color\": { \"type\": \"keyword\" } } }\n"
        + "Query Fields: [price]\n"
        + "Field selection: relevant=[price, cost]; ignored=[color]\n"
        + "Output: { \"query\": { \"range\": { \"price\": { \"gt\": 50 } } } }\n";

    public static final String EXAMPLE_2 = "Example 2 — text match + exact filter (spelling tolerant)\n"
        + "Input: Find employees in London who are active.\n"
        + "Mapping: { \"properties\": { \"city\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"status\": { \"type\": \"keyword\" }, \"notes\": { \"type\": \"text\" } } }\n"
        + "Query Fields: [city, status]\n"
        + "Field selection: relevant=[city(text), status(keyword)]; ignored=[notes]\n"
        + "Output: { \"query\": { \"bool\": { \"must\": [ { \"match\": { \"city\": { \"query\": \"London\", \"fuzziness\": \"AUTO\" } } } ], \"filter\": [ { \"term\": { \"status\": \"active\" } } ] } } }\n";

    public static final String EXAMPLE_3 = "Example 3 — match_phrase for multi-token\n"
        + "Input: Find employees located in New York City.\n"
        + "Mapping: { \"properties\": { \"city\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"department\": { \"type\": \"keyword\" } } }\n"
        + "Output: { \"query\": { \"match_phrase\": { \"city\": \"New York City\" } } }\n";

    public static final String EXAMPLE_4 = "Example 4 — multi_match across multiple text fields (spelling tolerant)\n"
        + "Input: Find profiles mentioning \"data engineering\" in the title or summary.\n"
        + "Mapping: { \"properties\": { \"title\": { \"type\": \"text\" }, \"summary\": { \"type\": \"text\" }, \"department\": { \"type\": \"keyword\" }, \"region\": { \"type\": \"keyword\" } } }\n"
        + "Output: { \"query\": { \"multi_match\": { \"query\": \"data engineering\", \"fields\": [\"title\", \"summary\"], \"fuzziness\": \"AUTO\" } } }\n";

    public static final String EXAMPLE_5 = "Example 5 — bool with SHOULD\n"
        + "Input: Search articles about \"machine learning\" that are research papers or blogs.\n"
        + "Mapping: { \"properties\": { \"content\": { \"type\": \"text\" }, \"type\": { \"type\": \"keyword\" } } }\n"
        + "Output: { \"query\": { \"bool\": { \"must\": [ { \"match\": { \"content\": \"machine learning\" } } ], \"should\": [ { \"term\": { \"type\": \"research paper\" } }, { \"term\": { \"type\": \"blog\" } } ], \"minimum_should_match\": 1 } } }\n";

    public static final String EXAMPLE_6 = "Example 6 — wildcard + exists (exact filters in bool.filter)\n"
        + "Input: Find users whose email starts with \"sam\" and who have a phone number on file.\n"
        + "Mapping: { \"properties\": { \"email\": { \"type\": \"keyword\" }, \"phone\": { \"type\": \"keyword\" }, \"avatar_url\": { \"type\": \"keyword\" } } }\n"
        + "Field selection: relevant=[email(prefix), phone(exists)]; ignored=[avatar_url]\n"
        + "Output: { \"query\": { \"bool\": { \"filter\": [ { \"prefix\": { \"email\": \"sam\" } }, { \"exists\": { \"field\": \"phone\" } } ] } } }\n";

    public static final String EXAMPLE_7 = "Example 7 — nested query (only when mapping says nested)\n"
        + "Input: Find books where an author's first_name is John AND last_name is Doe.\n"
        + "Mapping: { \"properties\": { \"author\": { \"type\": \"nested\", \"properties\": { \"first_name\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"last_name\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } } } }, \"title\": { \"type\": \"text\" } } }\n"
        + "Output: { \"query\": { \"nested\": { \"path\": \"author\", \"query\": { \"bool\": { \"must\": [ { \"term\": { \"author.first_name.keyword\": \"John\" } }, { \"term\": { \"author.last_name.keyword\": \"Doe\" } } ] } } } } }\n";

    public static final String EXAMPLE_8 = "Example 8 — terms aggregation\n"
        + "Input: Show the number of orders per status.\n"
        + "Mapping: { \"properties\": { \"status\": { \"type\": \"keyword\" }, \"order_id\": { \"type\": \"keyword\" } } }\n"
        + "Output: { \"size\": 0, \"aggs\": { \"orders_by_status\": { \"terms\": { \"field\": \"status\" } } } }\n";

    public static final String EXAMPLE_9 = "Example 9 — top N items by metric (hits + sort, no aggs)\n"
        + "Input: Show the 5 highest-rated electronics products.\n"
        + "Mapping: { \"properties\": { \"category\": { \"type\": \"keyword\" }, \"rating\": { \"type\": \"float\" }, \"reviews_count\": { \"type\": \"integer\" }, \"product_name\": { \"type\": \"text\" }, \"description\": { \"type\": \"text\" } } }\n"
        + "Field selection: relevant=[category(keyword), rating(float), reviews_count(integer), product_name(text), description(text)]\n"
        + "Output: { \"size\": 5, \"query\": { \"bool\": { \"filter\": [ { \"term\": { \"category\": \"electronics\" } } ] } }, \"sort\": [ { \"rating\": { \"order\": \"desc\" } }, { \"reviews_count\": { \"order\": \"desc\" } } ] }\n";

    public static final String EXAMPLE_10 = "Example 10 — top N categories (grouping via aggs; not for item lists)\n"
        + "Input: List the top 3 categories by total sales volume.\n"
        + "Mapping: { \"properties\": { \"category\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"sales\": { \"type\": \"float\" }, \"region\": { \"type\": \"keyword\" } } }\n"
        + "Field selection: relevant=[category.keyword, sales]; ignored=[region]\n"
        + "Output: { \"size\": 0, \"aggs\": { \"top_categories\": { \"terms\": { \"field\": \"category.keyword\", \"size\": 3, \"order\": { \"total_sales\": \"desc\" } }, \"aggs\": { \"total_sales\": { \"sum\": { \"field\": \"sales\" } } } } } }\n";

    public static final String EXAMPLE_11 = "Example 11 — ambiguous mapping, proxy success\n"
        + "Input: Give medicines shipped from Vietnam.\n"
        + "Mapping: { \"properties\": { \"item_name\": { \"type\": \"text\" }, \"product_category\": { \"type\": \"keyword\" }, \"country\": { \"type\": \"keyword\" }, \"ship_status\": { \"type\": \"keyword\" }, \"notes\": { \"type\": \"text\" } } }\n"
        + "Query Fields: [product_category, origin_country]\n"
        + "Field selection: relevant=[product_category, country(proxy for origin), ship_status(proxy for shipped)]; ignored=[notes, item_name]\n"
        + "Output: { \"query\": { \"bool\": { \"filter\": [ { \"term\": { \"product_category\": \"medicines\" } }, { \"term\": { \"country\": \"Vietnam\" } }, { \"term\": { \"ship_status\": \"shipped\" } } ] } } }\n";

    public static final String EXAMPLE_12 = "Example 12 — true fallback (no remotely relevant fields)\n"
        + "Input: List satellites with periapsis above 400km.\n"
        + "Mapping: { \"properties\": { \"name\": { \"type\": \"text\" }, \"color\": { \"type\": \"keyword\" } } }\n"
        + "Output: "
        + DEFAULT_QUERY
        + "\n";

    public static final String EXAMPLES = "==== EXAMPLES ====\n"
        + EXAMPLE_1
        + EXAMPLE_2
        + EXAMPLE_3
        + EXAMPLE_4
        + EXAMPLE_5
        + EXAMPLE_6
        + EXAMPLE_7
        + EXAMPLE_8
        + EXAMPLE_9
        + EXAMPLE_10
        + EXAMPLE_11
        + EXAMPLE_12;

    public static final String PROMPT_SUFFIX = "==== INPUT ====\n"
        + "Question: ${parameters.query_text}\n"
        + "Mapping: ${parameters.index_mapping:-}\n"
        + "Query Fields: ${parameters.query_fields:-}\n\n"
        + "==== OUTPUT ====\n"
        + "GIVE THE OUTPUT PART ONLY IN YOUR RESPONSE (a single JSON object)\n"
        + "Output:";

    public static final String DEFAULT_USER_PROMPT = PROMPT_PREFIX
        + "\n\n"
        + OUTPUT_FORMAT_INSTRUCTIONS
        + "\n"
        + EXAMPLES
        + "\n\n"
        + PROMPT_SUFFIX;
}
