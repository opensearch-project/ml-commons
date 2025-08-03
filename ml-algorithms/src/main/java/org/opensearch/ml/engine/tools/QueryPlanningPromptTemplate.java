package org.opensearch.ml.engine.tools;

public class QueryPlanningPromptTemplate {

    public static final String DEFAULT_QUERY =
        "{ \"query\": { \"multi_match\" : { \"query\":    \"${parameters.query_text}\",  \"fields\": ${parameters.query_fields:-[\"*\"]} } } }";

    public static final String QUERY_TYPE_RULES = "Choose query types based on user intent and fields: "
        + "match → single-token full‑text searches on analyzed text fields, "
        + "match_phrase → multi-token phrases on analyzed text fields (search string contains a space, hyphen, comma, etc.), "
        + "term / terms → exact match on keyword, numeric, boolean, "
        + "range → numeric/date comparisons (gt, lt, gte, lte), "
        + "bool with must, should, must_not, filter → AND/OR/NOT logic, "
        + "wildcard / prefix on keyword → \"starts with\", \"contains\", "
        + "exists → field presence/absence, "
        + "nested query / nested agg → Never wrap a field in nested unless the mapping for that exact path (or one of its parents) explicitly says \"type\": \"nested\". "
        + "Otherwise use a normal query on the flattened field. ";

    public static final String AGGREGATION_RULES = "Aggregations (when asked for counts, averages, \"top N\", distributions): "
        + "terms on field.keyword or numeric for grouping / top N, "
        + "Metric aggs (avg, min, max, sum, stats, cardinality) on numeric fields, "
        + "date_histogram, histogram, range for distributions, "
        + "Always set \"size\": 0 when only aggregations are needed, "
        + "Use sub‑aggregations + order for \"top N by metric\", "
        + "If grouping by a text field, use its .keyword sub‑field.";

    public static final String PROMPT_PREFIX =
        "You are an OpenSearch DSL expert. Your job is to convert natural‑language questions into strict JSON OpenSearch search query bodies. "
            + "Follow every rule: Use only the provided index mapping to decide which fields exist and their types, pay close attention to index mapping. "
            + "Never invent fields not in the mapping. "
            + QUERY_TYPE_RULES
            + AGGREGATION_RULES;

    public static final String OUTPUT_FORMAT_INSTRUCTIONS = "Output format: Output only a valid escaped JSON string or the literal "
        + DEFAULT_QUERY
        + ". Do not print anything other than the JSON like code blocks etc. "
        + "Follow the examples below. "
        + "Fallback: If the request cannot be fulfilled with the mapping (missing field, unsupported feature, etc.), "
        + "output the literal string: "
        + DEFAULT_QUERY;

    // Individual example constants for better maintainability
    public static final String EXAMPLE_1 = "Example 1 — numeric range Input: Show all products that cost more than 50 dollars. "
        + "Mapping: \"{ \"properties\": { \"price\": { \"type\": \"float\" } } }\" "
        + "Output: \"{ \"query\": { \"range\": { \"price\": { \"gt\": 50 } } } }\" ";

    public static final String EXAMPLE_2 = "Example 2 — text match + exact filter Input: Find employees in London who are active. "
        + "Mapping: \"{ \"properties\": { \"city\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"status\": { \"type\": \"keyword\" } } }\" "
        + "Output: \"{ \"query\": { \"bool\": { \"must\": [ { \"match\": { \"city\": \"London\" } } ], \"filter\": [ { \"term\": { \"status\": \"active\" } } ] } } }\" ";

    public static final String EXAMPLE_3 =
        "Example 3 — match_phrase (use when search string contains a space, hyphen, comma, etc. here \"new york city\" has space) Input: Find employees who are active and located in New York City "
            + "Mapping: \"{ \"properties\": { \"city\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"status\": { \"type\": \"keyword\" } } }\" "
            + "Output: \"{ \"query\": { \"bool\": { \"must\": [ { \"match_phrase\": { \"city\": \"New York City\" } } ], \"filter\": [ { \"term\": { \"status\": \"active\" } } ] } } }\" ";

    public static final String EXAMPLE_4 =
        "Example 4 — bool with SHOULD Input: Search articles about \"machine learning\" that are research papers or blogs. "
            + "Mapping: \"{ \"properties\": { \"content\": { \"type\": \"text\" }, \"type\": { \"type\": \"keyword\" } } }\" "
            + "Output: \"{ \"query\": { \"bool\": { \"must\": [ { \"match\": { \"content\": \"machine learning\" } } ], \"should\": [ { \"term\": { \"type\": \"research paper\" } }, { \"term\": { \"type\": \"blog\" } } ], \"minimum_should_match\": 1 } } }\" ";

    public static final String EXAMPLE_5 = "Example 5 — MUST NOT Input: List customers who have not made a purchase in 2023. "
        + "Mapping: \"{ \"properties\": { \"last_purchase_date\": { \"type\": \"date\" } } }\" "
        + "Output: \"{ \"query\": { \"bool\": { \"must_not\": [ { \"range\": { \"last_purchase_date\": { \"gte\": \"2023-01-01\", \"lte\": \"2023-12-31\" } } } ] } } }\" ";

    public static final String EXAMPLE_6 = "Example 6 — wildcard Input: Find files with names starting with \"report_\". "
        + "Mapping: \"{ \"properties\": { \"filename\": { \"type\": \"keyword\" } } }\" "
        + "Output: \"{ \"query\": { \"wildcard\": { \"filename\": \"report_*\" } } }\" ";

    public static final String EXAMPLE_7 =
        "Example 7 — nested query (note the index mapping says \"type\": \"nested\", do not use it for other types) Input: Find books where an authors first_name is John AND last_name is Doe. "
            + "Mapping: \"{ \"properties\": { \"author\": { \"type\": \"nested\", \"properties\": { \"first_name\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"last_name\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } } } } } }\" "
            + "Output: \"{ \"query\": { \"nested\": { \"path\": \"author\", \"query\": { \"bool\": { \"must\": [ { \"term\": { \"author.first_name.keyword\": \"John\" } }, { \"term\": { \"author.last_name.keyword\": \"Doe\" } } ] } } } } }\" ";

    public static final String EXAMPLE_8 = "Example 8 — terms aggregation Input: Show the number of orders per status. "
        + "Mapping: \"{ \"properties\": { \"status\": { \"type\": \"keyword\" } } }\" "
        + "Output: \"{ \"size\": 0, \"aggs\": { \"orders_by_status\": { \"terms\": { \"field\": \"status\" } } } }\" ";

    public static final String EXAMPLE_9 =
        "Example 9 — metric aggregation with filter Input: What is the average price of electronics products? "
            + "Mapping: \"{ \"properties\": { \"category\": { \"type\": \"keyword\" }, \"price\": { \"type\": \"float\" } } }\" "
            + "Output: \"{ \"size\": 0, \"query\": { \"term\": { \"category\": \"electronics\" } }, \"aggs\": { \"avg_price\": { \"avg\": { \"field\": \"price\" } } } }\" ";

    public static final String EXAMPLE_10 = "Example 10 — top N by metric Input: List the top 3 categories by total sales volume. "
        + "Mapping: \"{ \"properties\": { \"category\": { \"type\": \"text\", \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }, \"sales\": { \"type\": \"float\" } } }\" "
        + "Output: \"{ \"size\": 0, \"aggs\": { \"top_categories\": { \"terms\": { \"field\": \"category.keyword\", \"size\": 3, \"order\": { \"total_sales\": \"desc\" } }, \"aggs\": { \"total_sales\": { \"sum\": { \"field\": \"sales\" } } } } } }\" ";

    public static final String EXAMPLE_11 = "Example 11 — fallback Input: Find employees who speak Klingon fluently. "
        + "Mapping: \"{ \"properties\": { \"name\": { \"type\": \"text\" }, \"role\": { \"type\": \"keyword\" } } }\" "
        + "Output: "
        + DEFAULT_QUERY;

    public static final String EXAMPLES = "EXAMPLES: "
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

    public static final String PROMPT_SUFFIX = "GIVE THE OUTPUT PART ONLY IN YOUR RESPONSE "
        + "Question: asked by user "
        + "Mapping:${parameters.index_mapping:-} "
        + "Output:";

    public static final String DEFAULT_SYSTEM_PROMPT = PROMPT_PREFIX + " " + OUTPUT_FORMAT_INSTRUCTIONS + EXAMPLES + " " + PROMPT_SUFFIX;
}
