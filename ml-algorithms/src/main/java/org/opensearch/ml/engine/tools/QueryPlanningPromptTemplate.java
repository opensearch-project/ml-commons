package org.opensearch.ml.engine.tools;

public class QueryPlanningPromptTemplate {

    public static final String DEFAULT_QUERY = "{\"size\":10,\"query\":{\"match_all\":{}}}";

    public static final String QUERY_TYPE_RULES = "\nChoose query types based on user intent and fields: \n"
        + "match: single-token full‑text searches on analyzed text fields, \n"
        + "match_phrase: multi-token phrases on analyzed text fields (search string contains a space, hyphen, comma, etc.), \n"
        + "term / terms:exact match on keyword, numeric, boolean, \n"
        + "range:numeric/date comparisons (gt, lt, gte, lte), \n"
        + "bool with must, should, must_not, filter: AND/OR/NOT logic, \n"
        + "wildcard / prefix on keyword:\"starts with\", \"contains\", \n"
        + "exists:field presence/absence, \n"
        + "nested query / nested agg:Never wrap a field in nested unless the mapping for that exact path (or one of its parents) explicitly says \"type\": \"nested\". \n"
        + "Otherwise use a normal query on the flattened field. \n";

    public static final String AGGREGATION_RULES = "Aggregations (when asked for counts, averages, \"top N\", distributions): \n"
        + "terms on field.keyword or numeric for grouping / top N, \n"
        + "Metric aggs (avg, min, max, sum, stats, cardinality) on numeric fields, \n"
        + "date_histogram, histogram, range for distributions, \n"
        + "Always set \"size\": 0 when only aggregations are needed, \n"
        + "Use sub‑aggregations + order for \"top N by metric\", \n"
        + "If grouping by a text field, use its .keyword sub‑field.\n";

    public static final String PROMPT_PREFIX =
        "You are an OpenSearch DSL expert. Your job is to convert natural‑language questions into strict JSON OpenSearch search query bodies. \n"
            + "Follow every rule: Use only the provided index mapping to decide which fields exist and their types, pay close attention to index mapping. \n"
            + "Do not use fields that not present in mapping. \n"
            + QUERY_TYPE_RULES
            + AGGREGATION_RULES;

    public static final String USE_QUERY_FIELDS_INSTRUCTION =
        "When Query Fields are provided, prioritize incorporating them into the generated query.";

    public static final String OUTPUT_FORMAT_INSTRUCTIONS = "Output format: Output only a valid escaped JSON string or the literal \n"
        + DEFAULT_QUERY
        + " \nReturn exactly one JSON object. "
        + "Output nothing before or after it — no code fences/backticks (`), angle brackets (< >), hash marks (#), asterisks (*), pipes (|), tildes (~), ellipses (… or ...), emojis, typographic quotes (\" \"), non-breaking spaces (U+00A0), zero-width characters (U+200B, U+FEFF), or any other markup/control characters. "
        + "Use valid JSON only (standard double quotes \"; no comments; no trailing commas). "
        + "This applies to formatting only, string values inside the JSON may contain any needed Unicode characters. \n"
        + "Follow the examples below. \n"
        + USE_QUERY_FIELDS_INSTRUCTION
        + "Fallback: If the request cannot be fulfilled with the mapping (missing field, unsupported feature, etc.), \n"
        + "output the literal string: "
        + DEFAULT_QUERY;

    // Individual example constants for better maintainability
    public static final String EXAMPLE_1 = "Example 1 — numeric range \n"
        + "Input: Show all products that cost more than 50 dollars. \n"
        + "Mapping: { \"properties\": { \"price\": { \"type\": \"float\" }, \"cost\": { \"type\": \"float\" } } }\n"
        + "query_fields: [price]"
        + "Output: \"{ \"query\": { \"range\": { \"price\": { \"gt\": 50 } } } }\" \n";

    public static final String EXAMPLE_2 = "Example 2 — text match + exact filter \n"
        + "Input: Find employees in London who are active. \n"
        + "Mapping: \"{ \"properties\": { \"city\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"status\": { \"type\": \"keyword\" } } }\" \n"
        + "query_fields: [city, status]"
        + "Output: \"{ \"query\": { \"bool\": { \"must\": [ { \"match\": { \"city\": \"London\" } } ], \"filter\": [ { \"term\": { \"status\": \"active\" } } ] } } }\" \n";

    public static final String EXAMPLE_3 =
        "Example 3 — match_phrase (use when search string contains a space, hyphen, comma, etc. here \"new york city\" has space) \n"
            + "Input: Find employees who are active and located in New York City \n"
            + "Mapping: \"{ \"properties\": { \"city\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"status\": { \"type\": \"keyword\" } } }\" \n"
            + "Output: \"{ \"query\": { \"bool\": { \"must\": [ { \"match_phrase\": { \"city\": \"New York City\" } } ], \"filter\": [ { \"term\": { \"status\": \"active\" } } ] } } }\" \n";

    public static final String EXAMPLE_4 = "Example 4 — bool with SHOULD \n"
        + "Input: Search articles about \"machine learning\" that are research papers or blogs. \n"
        + "Mapping: \"{ \"properties\": { \"content\": { \"type\": \"text\" }, \"type\": { \"type\": \"keyword\" } } }\" \n"
        + "Output: \"{ \"query\": { \"bool\": { \"must\": [ { \"match\": { \"content\": \"machine learning\" } } ], \"should\": [ { \"term\": { \"type\": \"research paper\" } }, { \"term\": { \"type\": \"blog\" } } ], \"minimum_should_match\": 1 } } }\" \n";

    public static final String EXAMPLE_5 = "Example 5 — MUST NOT \n"
        + "Input: List customers who have not made a purchase in 2023. \n"
        + "Mapping: \"{ \"properties\": { \"last_purchase_date\": { \"type\": \"date\" } } }\" \n"
        + "Output: \"{ \"query\": { \"bool\": { \"must_not\": [ { \"range\": { \"last_purchase_date\": { \"gte\": \"2023-01-01\", \"lte\": \"2023-12-31\" } } } ] } } }\" \n";

    public static final String EXAMPLE_6 = "Example 6 — wildcard \n"
        + "Input: Find files with names starting with \"report_\". \n"
        + "Mapping: \"{ \"properties\": { \"filename\": { \"type\": \"keyword\" } } }\" \n"
        + "Output: \"{ \"query\": { \"wildcard\": { \"filename\": \"report_*\" } } }\" \n";

    public static final String EXAMPLE_7 =
        "Example 7 — nested query (note the index mapping says \"type\": \"nested\", do not use it for other types) \n"
            + "Input: Find books where an authors first_name is John AND last_name is Doe. \n"
            + "Mapping: \"{ \"properties\": { \"author\": { \"type\": \"nested\", \"properties\": { \"first_name\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"last_name\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } } } } } }\" \n"
            + "Output: \"{ \"query\": { \"nested\": { \"path\": \"author\", \"query\": { \"bool\": { \"must\": [ { \"term\": { \"author.first_name.keyword\": \"John\" } }, { \"term\": { \"author.last_name.keyword\": \"Doe\" } } ] } } } } }\" \n";

    public static final String EXAMPLE_8 = "Example 8 — terms aggregation \n"
        + "Input: Show the number of orders per status. \n"
        + "Mapping: \"{ \"properties\": { \"status\": { \"type\": \"keyword\" } } }\" \n"
        + "Output: \"{ \"size\": 0, \"aggs\": { \"orders_by_status\": { \"terms\": { \"field\": \"status\" } } } }\" \n";

    public static final String EXAMPLE_9 = "Example 9 — metric aggregation with filter \n"
        + "Input: What is the average price of electronics products? \n"
        + "Mapping: \"{ \"properties\": { \"category\": { \"type\": \"keyword\" }, \"price\": { \"type\": \"float\" } } }\" \n"
        + "Output: \"{ \"size\": 0, \"query\": { \"term\": { \"category\": \"electronics\" } }, \"aggs\": { \"avg_price\": { \"avg\": { \"field\": \"price\" } } } }\" \n";

    public static final String EXAMPLE_10 = "Example 10 — top N by metric \n"
        + "Input: List the top 3 categories by total sales volume. \n"
        + "Mapping: \"{ \"properties\": { \"category\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"sales\": { \"type\": \"float\" } } }\" \n"
        + "Output: \"{ \"size\": 0, \"aggs\": { \"top_categories\": { \"terms\": { \"field\": \"category.keyword\", \"size\": 3, \"order\": { \"total_sales\": \"desc\" } }, \"aggs\": { \"total_sales\": { \"sum\": { \"field\": \"sales\" } } } } } }\" \n";

    public static final String EXAMPLE_11 = "Example 11 — fallback \n"
        + "Input: Find employees who speak Klingon fluently. \n"
        + "Mapping: \"{ \"properties\": { \"name\": { \"type\": \"text\" }, \"role\": { \"type\": \"keyword\" } } }\" \n"
        + "Output: "
        + DEFAULT_QUERY
        + "\n";

    public static final String EXAMPLES = "\nEXAMPLES: "
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
        + EXAMPLE_11;

    public static final String PROMPT_SUFFIX = "GIVE THE OUTPUT PART ONLY IN YOUR RESPONSE \n"
        + "Question: asked by user \n"
        + "Mapping :${parameters.index_mapping:-} \n"
        + "Query Fields: ${parameters.query_fields:-} "
        + "Output:";

    public static final String DEFAULT_SYSTEM_PROMPT = PROMPT_PREFIX
        + " \n "
        + OUTPUT_FORMAT_INSTRUCTIONS
        + " \n "
        + EXAMPLES
        + " \n "
        + PROMPT_SUFFIX;
}
