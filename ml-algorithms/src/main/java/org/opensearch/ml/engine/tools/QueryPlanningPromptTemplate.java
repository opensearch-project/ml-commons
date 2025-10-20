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
        + "- neural: semantic similarity on a 'semantic' or 'knn_vector' field (dense). Use \"query_text\" and \"k\"; include \"model_id\" unless bound in mapping.\n"
        + "- neural (top-level): allowed when it's the only relevance clause needed; otherwise wrap in a bool when combining with filters/other queries.\n"
        + "\n"
        + "Mechanics:\n"
        + "- Put exact constraints (term, terms, range, exists, prefix, wildcard) in bool.filter (non-scoring). Put full-text relevance (match, match_phrase, multi_match) in bool.must.\n"
        + "- Top N items/products/documents: return top hits (set \"size\": N as an integer) and sort by the relevant metric(s). Do not use aggregations for item lists.\n"
        + "- Neural retrieval size: set \"k\" ≥ \"size\" (e.g. heuristic, k = max(size*5, 100) and k<=ef_search).\n"
        + "- Spelling tolerance: match_phrase does NOT support fuzziness; use match or multi_match with \"fuzziness\": \"AUTO\" when tolerant matching is needed.\n"
        + "- Text operators (OR vs AND): default to OR for natural-language queries; to tighten, use minimum_should_match (e.g., \"75%\" requires ~75% of terms). Use AND only when every token is essential; if order/adjacency matters, use match_phrase. (Applies to match/multi_match.)\n"
        + "- Numeric note: use ONLY integers for size and k (e.g., \"size\": 5), not floats (wrong e.g., \"size\": 5.0).\n";

    public static final String AGGREGATION_RULES = "Aggregations (counts, averages, grouped summaries, distributions):\n"
        + "- Use aggregations when the user asks for grouped summaries (e.g., counts by category, averages by brand, or top N categories/brands).\n"
        + "- terms on field.keyword or numeric for grouping / top N groups (not items).\n"
        + "- Metric aggs (avg, min, max, sum, stats, cardinality) on numeric fields.\n"
        + "- date_histogram, histogram, range for distributions.\n"
        + "- Always set \"size\": 0 when only aggregations are needed.\n"
        + "- Use sub-aggregations + order for \"top N groups by metric\".\n"
        + "- If grouping/filtering exactly on a text field, use its .keyword sub-field when present.\n";

    public static final String SEMANTIC_SEARCH_RULES =
        """
            NEURAL / SEMANTIC SEARCH
            When to use:
            - The intent is conceptual/semantic (“about”, “similar to”, long phrases, synonyms, multilingual, ambiguous), and the mapping has:
              • type: "semantic", or
              • type: "knn_vector".
            - You also have exact filters (term/range/etc.) but text relevance still matters → add neural on that text field.
            - The user explicitly asks for semantic/neural/vector/embedding search.
            When NOT to use:
            - The request is purely structured/exact (IDs, codes, only term/range).
            - No suitable "semantic" or "knn_vector" field exists.
            - No Model ID found for neural search.
            How to query:
            - Use the \"neural\" clause against the chosen field.
            - Required: \"query_text\" and \"k\".
            - \"model_id\" rules:
              • For \"semantic\" fields, model usually bound in mapping → omit unless overriding.
              • For \"knn_vector\", include \"model_id\" unless a default is bound elsewhere.
              • If model ID is not found, do not generate query with Neural clause.
            Top-level usage:
            - If there are no filters/other clauses, \"neural\" MAY be the root query (no bool).
            - Use a bool wrapper only when combining with filters or additional queries; keep exact filters in bool.filter.
            Sizing:
            - \"size\": N is the returned hits.
            - Set \"k\" ≥ \"size\" (heuristic: k (int) = max(size*5, 100), reasonable cap ≈ 1000).
            Field choice:
            - Prefer a field that semantically represents intent (e.g., description/title/content).
            - If multiple candidates exist, pick the single best; add more only if clearly beneficial.
            Fallback:
            - If no suitable neural field exists or if no model id is found, do NOT add a neural clause; proceed with classic DSL or fall back to DEFAULT_QUERY if nothing relevant exists.
            """;

    public static final String DATE_RULES =
        """
            DATE RULES
            - Use range on date/date_nanos in bool.filter.
            - Emit ISO 8601 UTC ('Z') bounds; don't set time_zone for explicit UTC. (now is UTC)
            - Date math: now±N{y|M|w|d|h|m|s} (M=month, m=minute; e.g., now-7d .. now = last 7 days).
            - Rounding: "/UNIT" floors to start (now/d, now/w, now/M, now/y). Examples: last full day → now-1d/d .. now/d; last full month → now-1M/M .. now/M.
            - End boundaries: prefer the next unit’s start (avoid 23:59:59).
            - Formats: only add "format" when inputs aren’t default; epoch_millis allowed.
            - Buckets: use date_histogram (set calendar_interval or fixed_interval); add time_zone only when local day/week/month buckets are required.
            """;
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
        + DATE_RULES
        + "\n"
        + SEMANTIC_SEARCH_RULES
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
    public static final String EXAMPLE_1 = "Example 1 — numeric + date range (merged)\n"
        + "Input: Show all products that cost more than 50 dollars in the last 30 days.\n"
        + "Mapping: { \"properties\": { \"price\": { \"type\": \"float\" }, \"created_at\": { \"type\": \"date\" }, \"color\": { \"type\": \"keyword\" } } }\n"
        + "Query Fields: [price, created_at]\n"
        + "Field selection: relevant=[price(float), created_at(date)]; ignored=[color]\n"
        + "Output: { \"query\": { \"bool\": { \"filter\": [{ \"range\": { \"price\": { \"gt\": 50 } } }, { \"range\": { \"created_at\": { \"gte\": \"now-30d/d\", \"lte\": \"now\" } } } ] } } }\n";

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

    public static final String EXAMPLE_4 = "Example 4 — multi_match across fields + SHOULD filters\n"
        + "Input: Find profiles mentioning \\\"data engineering\\\" in the title or summary that are research papers or blogs.\n"
        + "Mapping: { \"properties\": { \"title\": { \"type\": \"text\" }, \"summary\": { \"type\": \"text\" }, \"type\": { \"type\": \"keyword\" } } }\n"
        + "Output: { \"query\": { \"bool\": { \"must\": [ { \"multi_match\": { \"query\": \"data engineering\", \"fields\": [\"title\", \"summary\"], \"fuzziness\": \"AUTO\" } } ], \"should\": [ { \"term\": { \"type\": \"research paper\" } }, { \"term\": { \"type\": \"blog\" } } ], \"minimum_should_match\": 1 } } }\n";

    public static final String EXAMPLE_5 = "Example 5 — wildcard + exists (exact filters in bool.filter)\n"
        + "Input: Find users whose email starts with \"sam\" and who have a phone number on file.\n"
        + "Mapping: { \"properties\": { \"email\": { \"type\": \"keyword\" }, \"phone\": { \"type\": \"keyword\" }, \"avatar_url\": { \"type\": \"keyword\" } } }\n"
        + "Field selection: relevant=[email(prefix), phone(exists)]; ignored=[avatar_url]\n"
        + "Output: { \"query\": { \"bool\": { \"filter\": [ { \"prefix\": { \"email\": \"sam\" } }, { \"exists\": { \"field\": \"phone\" } } ] } } }\n";

    public static final String EXAMPLE_6 = "Example 6 — nested query (only when mapping says nested)\n"
        + "Input: Find books where an author's first_name is John AND last_name is Doe.\n"
        + "Mapping: { \"properties\": { \"author\": { \"type\": \"nested\", \"properties\": { \"first_name\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"last_name\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } } } }, \"title\": { \"type\": \"text\" } } }\n"
        + "Output: { \"query\": { \"nested\": { \"path\": \"author\", \"query\": { \"bool\": { \"must\": [ { \"term\": { \"author.first_name.keyword\": \"John\" } }, { \"term\": { \"author.last_name.keyword\": \"Doe\" } } ] } } } } }\n";

    public static final String EXAMPLE_7 = "Example 7 — terms aggregation\n"
        + "Input: Show the number of orders per status.\n"
        + "Mapping: { \"properties\": { \"status\": { \"type\": \"keyword\" }, \"order_id\": { \"type\": \"keyword\" } } }\n"
        + "Output: { \"size\": 0, \"aggs\": { \"orders_by_status\": { \"terms\": { \"field\": \"status\" } } } }\n";

    public static final String EXAMPLE_8 = "Example 8 — top N items by metric (hits + sort, no aggs)\n"
        + "Input: Show the 5 highest-rated electronics products.\n"
        + "Mapping: { \"properties\": { \"category\": { \"type\": \"keyword\" }, \"rating\": { \"type\": \"float\" }, \"reviews_count\": { \"type\": \"integer\" }, \"product_name\": { \"type\": \"text\" }, \"description\": { \"type\": \"text\" } } }\n"
        + "Field selection: relevant=[category(keyword), rating(float), reviews_count(integer), product_name(text), description(text)]\n"
        + "Output: { \"size\": 5, \"query\": { \"bool\": { \"filter\": [ { \"term\": { \"category\": \"electronics\" } } ] } }, \"sort\": [ { \"rating\": { \"order\": \"desc\" } }, { \"reviews_count\": { \"order\": \"desc\" } } ] }\n";

    public static final String EXAMPLE_9 = "Example 9 — top N categories (grouping via aggs; not for item lists)\n"
        + "Input: List the top 3 categories by total sales volume.\n"
        + "Mapping: { \"properties\": { \"category\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"sales\": { \"type\": \"float\" }, \"region\": { \"type\": \"keyword\" } } }\n"
        + "Field selection: relevant=[category.keyword, sales]; ignored=[region]\n"
        + "Output: { \"size\": 0, \"aggs\": { \"top_categories\": { \"terms\": { \"field\": \"category.keyword\", \"size\": 3, \"order\": { \"total_sales\": \"desc\" } }, \"aggs\": { \"total_sales\": { \"sum\": { \"field\": \"sales\" } } } } } }\n";

    public static final String EXAMPLE_10 = "Example 10 — ambiguous mapping, proxy success\n"
        + "Input: Give medicines shipped from Vietnam.\n"
        + "Mapping: { \"properties\": { \"item_name\": { \"type\": \"text\" }, \"product_category\": { \"type\": \"keyword\" }, \"country\": { \"type\": \"keyword\" }, \"ship_status\": { \"type\": \"keyword\" }, \"notes\": { \"type\": \"text\" } } }\n"
        + "Query Fields: [product_category, origin_country]\n"
        + "Field selection: relevant=[product_category, country(proxy for origin), ship_status(proxy for shipped)]; ignored=[notes, item_name]\n"
        + "Output: { \"query\": { \"bool\": { \"filter\": [ { \"term\": { \"product_category\": \"medicines\" } }, { \"term\": { \"country\": \"Vietnam\" } }, { \"term\": { \"ship_status\": \"shipped\" } } ] } } }\n";

    public static final String EXAMPLE_11 = "Example 11 — true fallback (no remotely relevant fields)\n"
        + "Input: List satellites with periapsis above 400km.\n"
        + "Mapping: { \"properties\": { \"name\": { \"type\": \"text\" }, \"color\": { \"type\": \"keyword\" } } }\n"
        + "Output: "
        + DEFAULT_QUERY
        + "\n";

    public static final String EXAMPLE_12 = "Example 12 — neural preferred with safe fallback (merged)\n"
        + "Input: Find articles about \\\"LLM hallucinations\\\". Model Id may or may not be provided.\n"
        + "Mapping: { \"properties\": { \"content\": {\"type\":\"text\"}, \"content_vector\": {\"type\":\"knn_vector\",\"dimension\":768}, \"tags\": {\"type\":\"keyword\"}, \"published_at\": {\"type\":\"date\"} } }\n"
        + "Output (with model_id): { \"size\": 10, \"query\": { \"neural\": { \"content_vector\": { \"query_text\": \"LLM hallucinations\", \"model_id\": \"m-dense-001\", \"k\": 200 } } } }\n"
        + "Output (fallback without model_id): { \"size\": 10, \"query\": { \"match\": { \"content\": { \"query\": \"LLM hallucinations\" } } } }\n";

    public static final String EXAMPLE_13 = "Example 13 — neural on semantic field + exact filters (mapping includes non-semantic fields)\n"
        + "Input: Find \\\"wireless noise cancelling headphones with multipoint\\\" under $200; brand Sony.\n"
        + "Mapping: { \"properties\": { \"price\": {\"type\":\"float\"}, \"brand\": {\"type\":\"keyword\"}, \"title\": {\"type\":\"text\"}, \"description\": {\"type\":\"semantic\", \"model_id\":\"m-sem-123\"} } }\n"
        + "Output: { \"size\": 10, \"query\": { \"bool\": { \"must\": [ { \"neural\": { \"description\": { \"query_text\": \"wireless noise cancelling headphones with multipoint\", \"k\": 120 } } } ], \"filter\": [ { \"range\": { \"price\": { \"lte\": 200 } } }, { \"term\": { \"brand\": \"Sony\" } } ] } } }\n";

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
        + EXAMPLE_12
        + EXAMPLE_13;

    public static final String TEMPLATE_USE_INSTRUCTIONS =
        "Use this search template provided by the user as reference to generate the query: ${parameters.template}\n\n"
            + "Note that this template might contain terms that are not relevant to the question at hand, in that case ignore the template";

    public static final String DEFAULT_QUERY_PLANNING_SYSTEM_PROMPT = PROMPT_PREFIX
        + "\n\n"
        + OUTPUT_FORMAT_INSTRUCTIONS
        + "\n"
        + EXAMPLES
        + "\n"
        + TEMPLATE_USE_INSTRUCTIONS;

    public static final String DEFAULT_QUERY_PLANNING_USER_PROMPT = "Question: ${parameters.question}\n"
        + "Mapping: ${parameters.index_mapping:-}\n"
        + "Query Fields: ${parameters.query_fields:-}\n"
        + "Sample Document from index:${parameters.sample_document:-}\n"
        + "In UTC:${parameters.current_time:-} format: yyyy-MM-dd'T'HH:mm:ss'Z'\n"
        + "Embedding Model ID for Neural Search:${parameters.embedding_model_id:- not provided} \n"
        + "==== OUTPUT ====\n"
        + "GIVE THE OUTPUT PART ONLY IN YOUR RESPONSE (a single JSON object)\n"
        + "Output:";

    // Template selection prompt
    public static final String TEMPLATE_SELECTION_PURPOSE = "==== PURPOSE ====\n"
        + "You are an OpenSearch Search Template selector. Given a natural language question, a list of search template IDs and search template descriptions, choose the search template ID which is most related to the given question.\n\n";

    public static final String TEMPLATE_SELECTION_GOAL = "Given:\n"
        + "1) A natural-language question from the user.\n"
        + "2) A catalog of OpenSearch templates, each with:\n"
        + "    - id (string, case-sensitive)\n"
        + "    - description (1–3 sentences)\n"
        + "Return: the SINGLE id of the best-matching template.";

    public static final String TEMPLATE_SELECTION_OUTPUT_RULES = "- Output ONLY the template id.\n"
        + "- No quotes, no backticks, no punctuation, no prefix/suffix, no extra words.\n"
        + "- No spaces or newlines before/after. Output must be exactly one of the provided ids.\n"
        + "- Do not ask questions or explain.\n"
        + "- Think internally; do NOT reveal your reasoning.";

    public static final String TEMPLATE_SELECTION_CRITERIA = "(apply in order)\n"
        + "1) INTENT MATCH: Identify the user’s primary intent (e.g., product search/browse, analytical reporting, trend/sales analysis, inventory, support lookup). Prefer templates whose descriptions explicitly support that intent.\n"
        + "2) SIGNAL ALIGNMENT: Count strong lexical/semantic matches between the question and each template’s description/placeholders.\n"
        + "   - Attribute filters (brand, category, size, color, price, rating, etc.) → favor product/item search templates.\n"
        + "   - Metrics (sales value, revenue, units sold, conversion, time windows) → favor analytics/aggregation templates.\n"
        + "   - Temporal phrases (“last week”, “by month”, “trending”, “top sellers”) → favor templates with date/time and aggregations.\n"
        + "   - Opinion/quality words (“highly rated”, “best”, “top reviewed”) → favor templates with rating/review placeholders.\n"
        + "3) SPECIFICITY: If multiple templates match, prefer the one whose description/placeholders are the most specific to the question’s entities and constraints.\n"
        + "4) TIE-BREAK:\n"
        + "   - Prefer templates intended for the user’s domain (e.g., “products” vs “sales analytics”).\n"
        + "   - Prefer general-purpose search over analytics if the question asks to “find/search/browse” items; prefer analytics if it asks for “most sold/revenue/total/average”.";

    public static final String TEMPLATE_SELECTION_VALIDATION =
        "- Your output MUST be exactly one of the provided template ids (regex: ^[A-Za-z0-9_-]+$).\n"
            + "- If no perfect match exists, pick the closest by the criteria above. Never output “none” or invent an id.";

    public static final String TEMPLATE_SELECTION_INPUTS = "question: ${parameters.question}\n"
        + "search_templates: ${parameters.search_templates}";

    public static final String TEMPLATE_SELECTION_EXAMPLES = "Example A: \n"
        + "question: 'what shoes are highly rated'\n"
        + "search_templates :\n"
        + "[\n"
        + "{'template_id':'product-search-template','template_description':'Searches products in an e-commerce store.'},\n"
        + "{'template_id':'sales-value-analysis-template','template_description':'Aggregates sales value for top-selling products.'}\n"
        + "]\n"
        + "Example output : 'product-search-template'";

    public static final String DEFAULT_TEMPLATE_SELECTION_SYSTEM_PROMPT = TEMPLATE_SELECTION_PURPOSE
        + "==== GOAL ====\n"
        + TEMPLATE_SELECTION_GOAL
        + "\n"
        + "==== OUTPUT RULES ====\n"
        + TEMPLATE_SELECTION_OUTPUT_RULES
        + "\n"
        + "==== SELECTION CRITERIA ====\n"
        + TEMPLATE_SELECTION_CRITERIA
        + "\n"
        + "==== VALIDATION ====\n"
        + TEMPLATE_SELECTION_VALIDATION
        + "\n"
        + "==== EXAMPLES ====\n"
        + TEMPLATE_SELECTION_EXAMPLES;

    public static final String DEFAULT_TEMPLATE_SELECTION_USER_PROMPT = "==== INPUTS ====\n" + TEMPLATE_SELECTION_INPUTS;

    public static final String DEFAULT_SEARCH_TEMPLATE = "{"
        + "\"from\": {{from}}{{^from}}0{{/from}},"
        + "\"size\": {{size}}{{^size}}10{{/size}},"
        + "\n"
        + "\"query\": {"
        + "  \"bool\": {"
        + "    \"should\": ["
        + "      {"
        + "        \"multi_match\": {"
        + "          \"query\": \"{{lex_query}}\","
        + "          \"fields\": {{#lex_fields}}{{{lex_fields}}}{{/lex_fields}}{{^lex_fields}}[\"*^1.0\"]{{/lex_fields}},"
        + "          \"type\": \"{{#lex_type}}{{lex_type}}{{/lex_type}}{{^lex_type}}best_fields{{/lex_type}}\","
        + "          \"operator\": \"{{#lex_operator}}{{lex_operator}}{{/lex_operator}}{{^lex_operator}}or{{/lex_operator}}\","
        + "          \"boost\": {{#lex_boost}}{{lex_boost}}{{/lex_boost}}{{^lex_boost}}1.0{{/lex_boost}}"
        + "        }"
        + "      }{{#sem_enabled}},"
        + "      {"
        + "        \"neural\": {"
        + "          \"{{sem_field}}\": {"
        + "            \"query_text\": \"{{sem_query_text}}\","
        + "            \"model_id\": \"{{sem_model_id}}\","
        + "            \"k\": {{#sem_k}}{{sem_k}}{{/sem_k}}{{^sem_k}}150{{/sem_k}},"
        + "            \"boost\": {{#sem_boost}}{{sem_boost}}{{/sem_boost}}{{^sem_boost}}1.5{{/sem_boost}}"
        + "          }"
        + "        }"
        + "      }{{/sem_enabled}}"
        + "    ],"
        + "    \"filter\": {{#filters}}{{{filters}}}{{/filters}}{{^filters}}[]{{/filters}},"
        + "    \"minimum_should_match\": 1"
        + "  }"
        + "},"
        + "\n"
        + "\"sort\": {{#sort}}{{{sort}}}{{/sort}}{{^sort}}[{ \"_score\": { \"order\": \"desc\" } }]{{/sort}},"
        + "\n"
        + "\"track_total_hits\": {{#track_total_hits}}{{track_total_hits}}{{/track_total_hits}}{{^track_total_hits}}false{{/track_total_hits}}"
        + "}";
}
